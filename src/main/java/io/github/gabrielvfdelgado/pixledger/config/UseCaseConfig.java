package io.github.gabrielvfdelgado.pixledger.config;

import io.github.gabrielvfdelgado.pixledger.adapters.out.bank.SimulatedExternalBank;
import io.github.gabrielvfdelgado.pixledger.adapters.out.postgres.JdbcAccountRepository;
import io.github.gabrielvfdelgado.pixledger.adapters.out.postgres.JdbcIdempotencyStore;
import io.github.gabrielvfdelgado.pixledger.adapters.out.postgres.JdbcLedgerRepository;
import io.github.gabrielvfdelgado.pixledger.adapters.out.postgres.JdbcOutboxRepository;
import io.github.gabrielvfdelgado.pixledger.adapters.out.postgres.JdbcTransferRepository;
import io.github.gabrielvfdelgado.pixledger.adapters.out.postgres.SpringTransactionRunner;
import io.github.gabrielvfdelgado.pixledger.adapters.out.redis.RedisIdempotencyCache;
import io.github.gabrielvfdelgado.pixledger.application.port.out.AccountRepository;
import io.github.gabrielvfdelgado.pixledger.application.port.out.ExternalBank;
import io.github.gabrielvfdelgado.pixledger.application.port.out.IdempotencyCache;
import io.github.gabrielvfdelgado.pixledger.application.port.out.IdempotencyStore;
import io.github.gabrielvfdelgado.pixledger.application.port.out.LedgerRepository;
import io.github.gabrielvfdelgado.pixledger.application.port.out.OutboxRepository;
import io.github.gabrielvfdelgado.pixledger.application.port.out.TransactionRunner;
import io.github.gabrielvfdelgado.pixledger.application.port.out.TransferRepository;
import io.github.gabrielvfdelgado.pixledger.application.service.AccountService;
import io.github.gabrielvfdelgado.pixledger.application.service.IdempotentExecutor;
import io.github.gabrielvfdelgado.pixledger.application.service.TransferProcessor;
import io.github.gabrielvfdelgado.pixledger.application.service.TransferService;
import io.lettuce.core.ClientOptions;
import io.lettuce.core.SocketOptions;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import java.time.Clock;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.data.redis.LettuceClientConfigurationBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

/** Único lugar que conhece as implementações e monta o hexágono. */
@Configuration
public class UseCaseConfig {

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    AccountRepository accountRepository(JdbcTemplate jdbc) {
        return new JdbcAccountRepository(jdbc);
    }

    @Bean
    TransferRepository transferRepository(JdbcTemplate jdbc) {
        return new JdbcTransferRepository(jdbc);
    }

    @Bean
    LedgerRepository ledgerRepository(JdbcTemplate jdbc, NamedParameterJdbcTemplate namedJdbc) {
        return new JdbcLedgerRepository(jdbc, namedJdbc);
    }

    @Bean
    IdempotencyStore idempotencyStore(JdbcTemplate jdbc) {
        return new JdbcIdempotencyStore(jdbc);
    }

    @Bean
    IdempotencyCache idempotencyCache(StringRedisTemplate redis) {
        return new RedisIdempotencyCache(redis);
    }

    /** Com o Redis fora do ar, falha na hora em vez de acumular comandos e atrasar a API. */
    @Bean
    LettuceClientConfigurationBuilderCustomizer redisFailFast() {
        return builder -> builder.clientOptions(ClientOptions.builder()
                .disconnectedBehavior(ClientOptions.DisconnectedBehavior.REJECT_COMMANDS)
                .socketOptions(SocketOptions.builder().connectTimeout(Duration.ofMillis(250)).build())
                .build());
    }

    @Bean
    TransactionRunner transactionRunner(TransactionTemplate template) {
        return new SpringTransactionRunner(template);
    }

    @Bean
    IdempotentExecutor idempotentExecutor(IdempotencyStore store, IdempotencyCache cache,
            TransactionRunner transactions) {
        return new IdempotentExecutor(store, cache, transactions);
    }

    @Bean
    AccountService accountService(AccountRepository accounts, LedgerRepository ledger,
            TransactionRunner transactions, IdempotentExecutor idempotent, Clock clock) {
        return new AccountService(accounts, ledger, transactions, idempotent, clock);
    }

    @Bean
    TransferService transferService(AccountRepository accounts, TransferRepository transfers, LedgerRepository ledger,
            OutboxRepository outbox, TransactionRunner transactions, IdempotentExecutor idempotent, Clock clock) {
        return new TransferService(accounts, transfers, ledger, outbox, transactions, idempotent, clock);
    }

    @Bean
    OutboxRepository outboxRepository(JdbcTemplate jdbc) {
        return new JdbcOutboxRepository(jdbc);
    }

    @Bean
    ExternalBank externalBank(@Value("${pixledger.bank.latency-ms}") long latencyMs) {
        return new SimulatedExternalBank(latencyMs);
    }

    @Bean
    TransferProcessor transferProcessor(TransferService transfers, ExternalBank bank) {
        return new TransferProcessor(transfers, transfers, bank);
    }

    @Bean
    OpenAPI openApi() {
        return new OpenAPI().info(new Info()
                .title("PixLedger API")
                .version("v1")
                .description("Carteira digital com Pix simulado. Projeto de portfólio, sem integração com o Pix real."));
    }
}
