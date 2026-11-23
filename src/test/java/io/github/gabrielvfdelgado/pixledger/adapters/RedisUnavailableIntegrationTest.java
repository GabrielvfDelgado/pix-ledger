package io.github.gabrielvfdelgado.pixledger.adapters;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.gabrielvfdelgado.pixledger.PostgresIntegrationTest;
import io.github.gabrielvfdelgado.pixledger.application.port.in.IdempotencyKey;
import io.github.gabrielvfdelgado.pixledger.application.port.in.IdempotentResult;
import io.github.gabrielvfdelgado.pixledger.application.port.in.TransferView;
import io.github.gabrielvfdelgado.pixledger.application.service.AccountService;
import io.github.gabrielvfdelgado.pixledger.application.service.TransferService;
import io.github.gabrielvfdelgado.pixledger.domain.account.AccountId;
import io.github.gabrielvfdelgado.pixledger.domain.account.PixKey;
import io.github.gabrielvfdelgado.pixledger.domain.shared.Money;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

class RedisUnavailableIntegrationTest extends PostgresIntegrationTest {

    @DynamicPropertySource
    static void redisOffline(DynamicPropertyRegistry registry) {
        registerDatabase(registry);
        registry.add("spring.redis.host", () -> "127.0.0.1");
        registry.add("spring.redis.port", () -> 1);
    }

    @Autowired
    private AccountService accounts;

    @Autowired
    private TransferService transfers;

    @Autowired
    private TestRestTemplate http;

    @Test
    void keepsChargingOnceWithoutRedis() {
        AccountId ana = accounts.open("Ana Souza").id();
        AccountId bia = accounts.open("Bia Lima").id();
        PixKey biaKey = PixKey.random();
        accounts.register(bia, biaKey);
        accounts.deposit(IdempotencyKey.of(UUID.randomUUID().toString()), ana, Money.ofCents(10_000));
        IdempotencyKey key = IdempotencyKey.of(UUID.randomUUID().toString());

        IdempotentResult<TransferView> first = transfers.request(key, ana, biaKey, Money.ofCents(3_000));
        IdempotentResult<TransferView> repeated = transfers.request(key, ana, biaKey, Money.ofCents(3_000));

        assertThat(first.replayed()).isFalse();
        assertThat(repeated.replayed()).isTrue();
        assertThat(repeated.value().id()).isEqualTo(first.value().id());
        assertThat(accounts.balanceOf(ana)).isEqualTo(Money.ofCents(7_000));
    }

    @Test
    void staysHealthyWithoutRedis() {
        assertThat(http.getForEntity("/actuator/health", String.class).getStatusCode()).isEqualTo(HttpStatus.OK);
    }
}
