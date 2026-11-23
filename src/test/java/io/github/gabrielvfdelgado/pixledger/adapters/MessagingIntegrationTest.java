package io.github.gabrielvfdelgado.pixledger.adapters;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.fasterxml.jackson.databind.JsonNode;
import io.github.gabrielvfdelgado.pixledger.PostgresIntegrationTest;
import io.github.gabrielvfdelgado.pixledger.adapters.in.messaging.TransferEventListener;
import io.github.gabrielvfdelgado.pixledger.adapters.out.rabbit.RabbitEventPublisher;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.RabbitMQContainer;

/** Exercita o fluxo completo: API, outbox, RabbitMQ, banco simulado, retentativas e estorno. */
@DirtiesContext
class MessagingIntegrationTest extends PostgresIntegrationTest {

    private static final Duration TIMEOUT = Duration.ofSeconds(20);
    private static final String ACCOUNTS = "/api/v1/accounts";
    private static final String TRANSFERS = "/api/v1/transfers";
    private static final RabbitMQContainer RABBIT = new RabbitMQContainer("rabbitmq:3.13-management-alpine");

    static {
        RABBIT.start();
    }

    @DynamicPropertySource
    static void messaging(DynamicPropertyRegistry registry) {
        registry.add("pixledger.messaging.enabled", () -> true);
        registry.add("pixledger.outbox.poll-ms", () -> 50);
        registry.add("pixledger.bank.latency-ms", () -> 0);
        registry.add("pixledger.retry.initial-interval-ms", () -> 20);
        registry.add("pixledger.retry.max-interval-ms", () -> 50);
        registry.add("spring.rabbitmq.host", RABBIT::getHost);
        registry.add("spring.rabbitmq.port", RABBIT::getAmqpPort);
        registry.add("spring.rabbitmq.username", RABBIT::getAdminUsername);
        registry.add("spring.rabbitmq.password", RABBIT::getAdminPassword);
    }

    @Autowired
    private TestRestTemplate http;

    @Autowired
    private RabbitTemplate rabbit;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void approvedTransferIsSettledAutomatically() {
        Scenario scenario = new Scenario();

        String transfer = scenario.transfer("30.00");

        awaitStatus(transfer, "SETTLED");
        assertThat(balance(scenario.ana)).isEqualTo("70.00");
        assertThat(balance(scenario.bia)).isEqualTo("30.00");
        await().atMost(TIMEOUT).untilAsserted(() -> assertThat(unpublishedEvents(transfer)).isZero());
        assertThat(eventCount(transfer)).isEqualTo(2);
    }

    @Test
    void rejectedTransferIsRefunded() {
        Scenario scenario = new Scenario();

        String transfer = scenario.transfer("30.13");

        JsonNode failed = awaitStatus(transfer, "FAILED");
        assertThat(failed.get("failureReason").asText()).isEqualTo("Pagamento recusado pelo banco de destino");
        assertThat(balance(scenario.ana)).isEqualTo("100.00");
        assertThat(balance(scenario.bia)).isEqualTo("0.00");
    }

    @Test
    void temporaryFailureIsRetriedUntilSettled() {
        Scenario scenario = new Scenario();

        String transfer = scenario.transfer("30.98");

        awaitStatus(transfer, "SETTLED");
        assertThat(balance(scenario.ana)).isEqualTo("69.02");
        assertThat(balance(scenario.bia)).isEqualTo("30.98");
    }

    @Test
    void exhaustedRetriesRefundPayer() {
        Scenario scenario = new Scenario();

        String transfer = scenario.transfer("30.99");

        JsonNode failed = awaitStatus(transfer, "FAILED");
        assertThat(failed.get("failureReason").asText()).isEqualTo("Banco externo indisponível");
        assertThat(balance(scenario.ana)).isEqualTo("100.00");
        assertThat(balance(scenario.bia)).isEqualTo("0.00");
    }

    @Test
    void duplicatedMessageDoesNotPayTwice() {
        Scenario scenario = new Scenario();
        String transfer = scenario.transfer("30.00");
        awaitStatus(transfer, "SETTLED");

        for (int i = 0; i < 3; i++) {
            rabbit.send(RabbitEventPublisher.EXCHANGE, RabbitEventPublisher.REQUESTED_KEY, json(
                    "{\"eventId\":\"" + UUID.randomUUID() + "\",\"type\":\"TRANSFER_REQUESTED\",\"transferId\":\""
                            + transfer + "\"}"));
        }

        await().pollDelay(Duration.ofMillis(300)).atMost(TIMEOUT).untilAsserted(() ->
                assertThat(balance(scenario.bia)).isEqualTo("30.00"));
        assertThat(balance(scenario.ana)).isEqualTo("70.00");
    }

    @Test
    void invalidMessageGoesToDeadLetterQueue() {
        String marker = "invalida-" + UUID.randomUUID();

        rabbit.send(RabbitEventPublisher.EXCHANGE, RabbitEventPublisher.REQUESTED_KEY, json(marker));

        await().atMost(TIMEOUT).untilAsserted(() -> {
            Message dead = rabbit.receive(TransferEventListener.DEAD_LETTER_QUEUE, 200);
            assertThat(dead).isNotNull();
            assertThat(new String(dead.getBody(), StandardCharsets.UTF_8)).isEqualTo(marker);
        });
    }

    private static Message json(String body) {
        return MessageBuilder.withBody(body.getBytes(StandardCharsets.UTF_8))
                .setContentType(MediaType.APPLICATION_JSON_VALUE)
                .build();
    }

    private JsonNode awaitStatus(String transfer, String expected) {
        await().atMost(TIMEOUT).pollInterval(Duration.ofMillis(50)).until(() ->
                expected.equals(http.getForObject(TRANSFERS + "/" + transfer, JsonNode.class).get("status").asText()));
        return http.getForObject(TRANSFERS + "/" + transfer, JsonNode.class);
    }

    private Long unpublishedEvents(String transfer) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM outbox_events WHERE transfer_id = ? AND published_at IS NULL",
                Long.class, UUID.fromString(transfer));
    }

    private Long eventCount(String transfer) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM outbox_events WHERE transfer_id = ?", Long.class,
                UUID.fromString(transfer));
    }

    private String balance(String account) {
        return http.getForObject(ACCOUNTS + "/" + account + "/balance", JsonNode.class).get("balance").asText();
    }

    private ResponseEntity<JsonNode> post(String path, Map<String, String> body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Idempotency-Key", UUID.randomUUID().toString());
        return http.postForEntity(path, new HttpEntity<>(body, headers), JsonNode.class);
    }

    /** Ana com R$ 100 e Bia com uma chave Pix. */
    private final class Scenario {

        private final String ana = open("Ana Souza");
        private final String bia = open("Bia Lima");
        private final String email = "conta-" + UUID.randomUUID() + "@example.com";

        private Scenario() {
            Map<String, String> key = new HashMap<>();
            key.put("type", "EMAIL");
            key.put("value", email);
            assertThat(post(ACCOUNTS + "/" + bia + "/pix-keys", key).getStatusCode()).isEqualTo(HttpStatus.CREATED);
            assertThat(post(ACCOUNTS + "/" + ana + "/deposits", Collections.singletonMap("amount", "100.00"))
                    .getStatusCode()).isEqualTo(HttpStatus.CREATED);
        }

        private String open(String holderName) {
            return post(ACCOUNTS, Collections.singletonMap("holderName", holderName)).getBody().get("id").asText();
        }

        private String transfer(String amount) {
            Map<String, String> body = new HashMap<>();
            body.put("payerAccountId", ana);
            body.put("pixKeyType", "EMAIL");
            body.put("pixKey", email);
            body.put("amount", amount);
            ResponseEntity<JsonNode> response = post(TRANSFERS, body);
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
            return response.getBody().get("id").asText();
        }
    }
}
