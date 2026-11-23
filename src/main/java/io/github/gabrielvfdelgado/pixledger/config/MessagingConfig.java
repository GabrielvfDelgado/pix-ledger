package io.github.gabrielvfdelgado.pixledger.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.gabrielvfdelgado.pixledger.adapters.in.messaging.TransferEventListener;
import io.github.gabrielvfdelgado.pixledger.adapters.in.messaging.TransferRetryRecoverer;
import io.github.gabrielvfdelgado.pixledger.adapters.in.scheduling.OutboxScheduler;
import io.github.gabrielvfdelgado.pixledger.adapters.out.rabbit.RabbitEventPublisher;
import io.github.gabrielvfdelgado.pixledger.application.port.in.ProcessTransferUseCase;
import io.github.gabrielvfdelgado.pixledger.application.port.out.EventPublisher;
import io.github.gabrielvfdelgado.pixledger.application.port.out.ExternalBankUnavailableException;
import io.github.gabrielvfdelgado.pixledger.application.port.out.OutboxRepository;
import io.github.gabrielvfdelgado.pixledger.application.port.out.TransactionRunner;
import io.github.gabrielvfdelgado.pixledger.application.service.OutboxRelayService;
import java.time.Clock;
import java.util.HashMap;
import java.util.Map;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Declarables;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.config.RetryInterceptorBuilder;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.dao.DataAccessException;
import org.springframework.retry.backoff.ExponentialBackOffPolicy;
import org.springframework.retry.policy.SimpleRetryPolicy;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.transaction.TransactionException;

/** Filas, retentativas e publicação de eventos. */
@Configuration
@EnableScheduling
@ConditionalOnProperty(name = "pixledger.messaging.enabled", havingValue = "true")
public class MessagingConfig {

    private static final String DEAD_LETTER_EXCHANGE = "pix-ledger.dlx";
    private static final String DEAD_LETTER_KEY = "transfer.dead";
    private static final int CONSUMERS = 4;
    private static final int PREFETCH = 4;

    @Bean
    Declarables transferTopology() {
        TopicExchange events = new TopicExchange(RabbitEventPublisher.EXCHANGE, true, false);
        DirectExchange deadLetters = new DirectExchange(DEAD_LETTER_EXCHANGE, true, false);
        Queue requested = queueWithDeadLetter(TransferEventListener.REQUESTED_QUEUE);
        Queue outcomes = queueWithDeadLetter(TransferEventListener.OUTCOMES_QUEUE);
        Queue dead = QueueBuilder.durable(TransferEventListener.DEAD_LETTER_QUEUE).build();
        Binding requestedBinding = BindingBuilder.bind(requested).to(events).with(RabbitEventPublisher.REQUESTED_KEY);
        Binding settledBinding = BindingBuilder.bind(outcomes).to(events).with(RabbitEventPublisher.SETTLED_KEY);
        Binding failedBinding = BindingBuilder.bind(outcomes).to(events).with(RabbitEventPublisher.FAILED_KEY);
        Binding deadBinding = BindingBuilder.bind(dead).to(deadLetters).with(DEAD_LETTER_KEY);
        return new Declarables(events, deadLetters, requested, outcomes, dead, requestedBinding, settledBinding,
                failedBinding, deadBinding);
    }

    private static Queue queueWithDeadLetter(String name) {
        return QueueBuilder.durable(name)
                .deadLetterExchange(DEAD_LETTER_EXCHANGE)
                .deadLetterRoutingKey(DEAD_LETTER_KEY)
                .build();
    }

    @Bean
    EventPublisher eventPublisher(RabbitTemplate rabbit, ObjectMapper json) {
        return new RabbitEventPublisher(rabbit, json);
    }

    @Bean
    OutboxRelayService outboxRelayService(OutboxRepository outbox, EventPublisher publisher,
            TransactionRunner transactions, Clock clock) {
        return new OutboxRelayService(outbox, publisher, transactions, clock);
    }

    @Bean
    OutboxScheduler outboxScheduler(OutboxRelayService relay) {
        return new OutboxScheduler(relay);
    }

    @Bean
    TransferEventListener transferEventListener(ProcessTransferUseCase processor, ObjectMapper json) {
        return new TransferEventListener(processor, json);
    }

    @Bean
    TransferRetryRecoverer transferRetryRecoverer(ProcessTransferUseCase processor, ObjectMapper json) {
        return new TransferRetryRecoverer(processor, json);
    }

    @Bean(TransferEventListener.LISTENER_FACTORY)
    SimpleRabbitListenerContainerFactory transferListenerFactory(ConnectionFactory connections,
            TransferRetryRecoverer recoverer,
            @Value("${pixledger.retry.max-attempts}") int maxAttempts,
            @Value("${pixledger.retry.initial-interval-ms}") long initialInterval,
            @Value("${pixledger.retry.multiplier}") double multiplier,
            @Value("${pixledger.retry.max-interval-ms}") long maxInterval) {
        Map<Class<? extends Throwable>, Boolean> retryable = new HashMap<>();
        retryable.put(ExternalBankUnavailableException.class, true);
        retryable.put(DataAccessException.class, true);
        retryable.put(TransactionException.class, true);

        ExponentialBackOffPolicy backOff = new ExponentialBackOffPolicy();
        backOff.setInitialInterval(initialInterval);
        backOff.setMultiplier(multiplier);
        backOff.setMaxInterval(maxInterval);

        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        factory.setConnectionFactory(connections);
        factory.setConcurrentConsumers(CONSUMERS);
        factory.setPrefetchCount(PREFETCH);
        factory.setDefaultRequeueRejected(false);
        factory.setAdviceChain(RetryInterceptorBuilder.stateless()
                .retryPolicy(new SimpleRetryPolicy(maxAttempts, retryable, true, false))
                .backOffPolicy(backOff)
                .recoverer(recoverer)
                .build());
        return factory;
    }
}
