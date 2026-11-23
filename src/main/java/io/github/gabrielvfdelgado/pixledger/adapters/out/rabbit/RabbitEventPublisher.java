package io.github.gabrielvfdelgado.pixledger.adapters.out.rabbit;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.gabrielvfdelgado.pixledger.application.port.out.EventPublishException;
import io.github.gabrielvfdelgado.pixledger.application.port.out.EventPublisher;
import io.github.gabrielvfdelgado.pixledger.application.port.out.OutboxEvent;
import io.github.gabrielvfdelgado.pixledger.application.port.out.TransferEventType;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

public final class RabbitEventPublisher implements EventPublisher {

    public static final String EXCHANGE = "pix-ledger.events";
    public static final String REQUESTED_KEY = "transfer.requested";
    public static final String SETTLED_KEY = "transfer.settled";
    public static final String FAILED_KEY = "transfer.failed";

    private static final Logger LOG = LoggerFactory.getLogger(RabbitEventPublisher.class);
    private static final long CONFIRM_TIMEOUT_SECONDS = 5;

    private final RabbitTemplate rabbit;
    private final ObjectMapper json;

    public RabbitEventPublisher(RabbitTemplate rabbit, ObjectMapper json) {
        this.rabbit = rabbit;
        this.json = json;
    }

    @Override
    public void publish(OutboxEvent event) {
        CorrelationData correlation = new CorrelationData(event.id().toString());
        try {
            rabbit.send(EXCHANGE, routingKey(event.type()), toMessage(event), correlation);
            CorrelationData.Confirm confirm = correlation.getFuture().get(CONFIRM_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            if (!confirm.isAck() || correlation.getReturned() != null) {
                throw new EventPublishException("O broker não aceitou o evento " + event.id(), null);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw failure(event, e);
        } catch (AmqpException | ExecutionException | TimeoutException | JsonProcessingException e) {
            throw failure(event, e);
        }
    }

    private Message toMessage(OutboxEvent event) throws JsonProcessingException {
        ObjectNode body = json.createObjectNode();
        body.put("eventId", event.id().toString());
        body.put("type", event.type().name());
        body.put("transferId", event.transferId().value().toString());
        body.put("occurredAt", event.occurredAt().toString());
        return MessageBuilder.withBody(json.writeValueAsString(body).getBytes(StandardCharsets.UTF_8))
                .setContentType(MessageProperties.CONTENT_TYPE_JSON)
                .setContentEncoding(StandardCharsets.UTF_8.name())
                .setMessageId(event.id().toString())
                .setDeliveryMode(MessageDeliveryMode.PERSISTENT)
                .build();
    }

    private static String routingKey(TransferEventType type) {
        switch (type) {
            case TRANSFER_REQUESTED:
                return REQUESTED_KEY;
            case TRANSFER_SETTLED:
                return SETTLED_KEY;
            default:
                return FAILED_KEY;
        }
    }

    private static EventPublishException failure(OutboxEvent event, Exception cause) {
        LOG.warn("Evento {} continua na outbox: {}", event.id(), cause.getClass().getSimpleName());
        return new EventPublishException("Falha ao publicar o evento " + event.id(), cause);
    }
}
