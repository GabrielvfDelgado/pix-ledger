package io.github.gabrielvfdelgado.pixledger.adapters;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.gabrielvfdelgado.pixledger.PostgresIntegrationTest;
import io.github.gabrielvfdelgado.pixledger.application.port.in.IdempotencyKey;
import io.github.gabrielvfdelgado.pixledger.application.port.in.RelayOutboxUseCase;
import io.github.gabrielvfdelgado.pixledger.application.port.in.TransferView;
import io.github.gabrielvfdelgado.pixledger.application.service.AccountService;
import io.github.gabrielvfdelgado.pixledger.application.service.TransferService;
import io.github.gabrielvfdelgado.pixledger.domain.account.AccountId;
import io.github.gabrielvfdelgado.pixledger.domain.account.PixKey;
import io.github.gabrielvfdelgado.pixledger.domain.shared.Money;
import io.github.gabrielvfdelgado.pixledger.domain.transfer.TransferStatus;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

@DirtiesContext
class RabbitUnavailableIntegrationTest extends PostgresIntegrationTest {

    @DynamicPropertySource
    static void rabbitOffline(DynamicPropertyRegistry registry) {
        registry.add("pixledger.messaging.enabled", () -> true);
        registry.add("spring.rabbitmq.host", () -> "127.0.0.1");
        registry.add("spring.rabbitmq.port", () -> 1);
        registry.add("spring.rabbitmq.connection-timeout", () -> "200ms");
    }

    @Autowired
    private AccountService accounts;

    @Autowired
    private TransferService transfers;

    @Autowired
    private RelayOutboxUseCase relay;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private TestRestTemplate http;

    @Test
    void acceptsTransferAndKeepsEventInOutbox() {
        AccountId ana = accounts.open("Ana Souza").id();
        AccountId bia = accounts.open("Bia Lima").id();
        PixKey key = PixKey.random();
        accounts.register(bia, key);
        accounts.deposit(IdempotencyKey.of(UUID.randomUUID().toString()), ana, Money.ofCents(10_000));

        TransferView transfer = transfers.request(IdempotencyKey.of(UUID.randomUUID().toString()), ana, key,
                Money.ofCents(3_000)).value();

        assertThat(relay.relay()).isZero();
        assertThat(transfers.get(transfer.id()).status()).isEqualTo(TransferStatus.PENDING);
        assertThat(accounts.balanceOf(ana)).isEqualTo(Money.ofCents(7_000));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM outbox_events WHERE transfer_id = ? "
                + "AND published_at IS NULL", Long.class, transfer.id().value())).isEqualTo(1);
        assertThat(http.getForEntity("/actuator/health", String.class).getStatusCode()).isEqualTo(HttpStatus.OK);
    }
}
