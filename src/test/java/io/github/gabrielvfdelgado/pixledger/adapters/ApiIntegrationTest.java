package io.github.gabrielvfdelgado.pixledger.adapters;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import io.github.gabrielvfdelgado.pixledger.PostgresIntegrationTest;
import io.github.gabrielvfdelgado.pixledger.application.port.in.ConcludeTransferUseCase;
import io.github.gabrielvfdelgado.pixledger.application.port.out.LedgerRepository;
import io.github.gabrielvfdelgado.pixledger.domain.account.AccountId;
import io.github.gabrielvfdelgado.pixledger.domain.ledger.JournalKind;
import io.github.gabrielvfdelgado.pixledger.domain.ledger.JournalTransaction;
import io.github.gabrielvfdelgado.pixledger.domain.ledger.SystemAccounts;
import io.github.gabrielvfdelgado.pixledger.domain.shared.DomainException;
import io.github.gabrielvfdelgado.pixledger.domain.shared.Money;
import io.github.gabrielvfdelgado.pixledger.domain.transfer.TransferId;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.TransactionSystemException;
import org.springframework.transaction.support.TransactionTemplate;

class ApiIntegrationTest extends PostgresIntegrationTest {

    private static final String ACCOUNTS = "/api/v1/accounts";
    private static final String TRANSFERS = "/api/v1/transfers";

    @Autowired
    private TestRestTemplate http;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private TransactionTemplate transactions;

    @Autowired
    private LedgerRepository ledger;

    @Autowired
    private ConcludeTransferUseCase conclusions;

    @Test
    void completesTransferAndPersistsLedger() {
        String ana = openAccount("Ana Souza");
        String bia = openAccount("Bia Lima");
        String key = registerEmail(bia);
        deposit(ana, "100.00");

        ResponseEntity<JsonNode> created = requestTransfer(ana, key, "30.00");

        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        assertThat(created.getHeaders().getLocation()).isNotNull();
        assertThat(created.getBody().get("status").asText()).isEqualTo("PENDING");
        assertThat(balance(ana)).isEqualTo("70.00");
        assertThat(balance(bia)).isEqualTo("0.00");

        String transferId = created.getBody().get("id").asText();
        conclusions.settle(TransferId.of(UUID.fromString(transferId)));

        assertThat(balance(ana)).isEqualTo("70.00");
        assertThat(balance(bia)).isEqualTo("30.00");

        JsonNode statement = http.getForObject(ACCOUNTS + "/" + bia + "/statement", JsonNode.class);
        assertThat(statement).hasSize(1);
        assertThat(statement.get(0).get("kind").asText()).isEqualTo("TRANSFER_SETTLEMENT");
        assertThat(statement.get(0).get("amount").asText()).isEqualTo("30.00");
        assertThat(http.getForObject(TRANSFERS + "/" + transferId, JsonNode.class).get("status").asText())
                .isEqualTo("SETTLED");
    }

    @Test
    void failedTransferReturnsMoney() {
        String ana = openAccount("Ana Souza");
        String bia = openAccount("Bia Lima");
        String key = registerEmail(bia);
        deposit(ana, "50.00");
        String transferId = requestTransfer(ana, key, "20.00").getBody().get("id").asText();

        conclusions.fail(TransferId.of(UUID.fromString(transferId)), "Banco externo indisponível");

        JsonNode failed = http.getForObject(TRANSFERS + "/" + transferId, JsonNode.class);
        assertThat(failed.get("status").asText()).isEqualTo("FAILED");
        assertThat(failed.get("failureReason").asText()).isEqualTo("Banco externo indisponível");
        assertThat(balance(ana)).isEqualTo("50.00");
        assertThat(balance(bia)).isEqualTo("0.00");
    }

    @Test
    void generatesRandomPixKey() {
        String ana = openAccount("Ana Souza");

        ResponseEntity<JsonNode> response = post(ACCOUNTS + "/" + ana + "/pix-keys",
                Collections.singletonMap("type", "RANDOM"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody().get("pixKeys").get(0).get("type").asText()).isEqualTo("RANDOM");
        assertThat(http.getForObject(ACCOUNTS + "/" + ana, JsonNode.class).get("pixKeys")).hasSize(1);
    }

    @Test
    void mapsBusinessErrors() {
        String ana = openAccount("Ana Souza");
        String bia = openAccount("Bia Lima");
        String key = registerEmail(bia);
        deposit(ana, "10.00");

        assertError(requestTransfer(ana, key, "10.01"), HttpStatus.UNPROCESSABLE_ENTITY, "INSUFFICIENT_FUNDS");
        assertError(requestTransfer(bia, key, "1.00"), HttpStatus.UNPROCESSABLE_ENTITY, "SAME_ACCOUNT");
        assertError(requestTransfer(UUID.randomUUID().toString(), key, "1.00"), HttpStatus.NOT_FOUND,
                "ACCOUNT_NOT_FOUND");
        assertError(requestTransfer(ana, "ninguem@example.com", "1.00"), HttpStatus.NOT_FOUND, "PIX_KEY_NOT_FOUND");
        assertError(requestTransfer(ana, key, "1.999"), HttpStatus.BAD_REQUEST, "INVALID_AMOUNT");
        assertError(post(ACCOUNTS + "/" + ana + "/pix-keys", pixKey("EMAIL", key)), HttpStatus.CONFLICT,
                "PIX_KEY_ALREADY_REGISTERED");
        assertThat(balance(ana)).isEqualTo("10.00");
    }

    @Test
    void cannotConcludeTransferTwice() {
        String ana = openAccount("Ana Souza");
        String key = registerEmail(openAccount("Bia Lima"));
        deposit(ana, "10.00");
        TransferId transferId = TransferId.of(UUID.fromString(
                requestTransfer(ana, key, "5.00").getBody().get("id").asText()));
        conclusions.settle(transferId);

        assertThatThrownBy(() -> conclusions.settle(transferId)).isInstanceOf(DomainException.class);
        assertThatThrownBy(() -> conclusions.fail(transferId, "erro")).isInstanceOf(DomainException.class);
        assertThat(balance(ana)).isEqualTo("5.00");
    }

    @Test
    void doesNotExposeManualConclusionEndpoints() {
        String ana = openAccount("Ana Souza");
        String key = registerEmail(openAccount("Bia Lima"));
        deposit(ana, "10.00");
        String transferId = requestTransfer(ana, key, "5.00").getBody().get("id").asText();

        assertThat(post(TRANSFERS + "/" + transferId + "/simulate-settlement", Collections.emptyMap())
                .getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(http.getForObject(TRANSFERS + "/" + transferId, JsonNode.class).get("status").asText())
                .isEqualTo("PENDING");
    }

    @Test
    void savesEventInSameTransactionAsTransfer() {
        String ana = openAccount("Ana Souza");
        String key = registerEmail(openAccount("Bia Lima"));
        deposit(ana, "10.00");
        String transferId = requestTransfer(ana, key, "5.00").getBody().get("id").asText();
        requestTransfer(ana, key, "50.00");

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM outbox_events WHERE transfer_id = ? "
                + "AND event_type = 'TRANSFER_REQUESTED' AND published_at IS NULL", Long.class,
                UUID.fromString(transferId))).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM transfers t WHERE payer_id = ? AND NOT EXISTS "
                + "(SELECT 1 FROM outbox_events o WHERE o.transfer_id = t.id)", Long.class,
                UUID.fromString(ana))).isZero();
    }

    @Test
    void mapsInvalidRequests() {
        ResponseEntity<JsonNode> blank = post(ACCOUNTS, Collections.singletonMap("holderName", " "));
        assertError(blank, HttpStatus.BAD_REQUEST, "VALIDATION_ERROR");
        assertThat(blank.getBody().get("fields").get("holderName").asText()).isEqualTo("Informe o nome do titular");

        assertError(http.getForEntity(ACCOUNTS + "/nao-e-uuid", JsonNode.class), HttpStatus.BAD_REQUEST,
                "INVALID_REQUEST");
        assertError(post(ACCOUNTS + "/" + openAccount("Ana Souza") + "/pix-keys",
                Collections.singletonMap("type", "INEXISTENTE")), HttpStatus.BAD_REQUEST, "INVALID_REQUEST");
        assertError(http.getForEntity(ACCOUNTS + "/" + UUID.randomUUID(), JsonNode.class), HttpStatus.NOT_FOUND,
                "ACCOUNT_NOT_FOUND");
    }

    @Test
    void repeatedIdempotencyKeyReturnsOriginalTransfer() {
        String ana = openAccount("Ana Souza");
        String key = registerEmail(openAccount("Bia Lima"));
        deposit(ana, "50.00");
        String idempotencyKey = UUID.randomUUID().toString();

        ResponseEntity<JsonNode> first = requestTransfer(ana, key, "20.00", idempotencyKey);
        ResponseEntity<JsonNode> second = requestTransfer(ana, key, "20.00", idempotencyKey);

        assertThat(first.getHeaders().getFirst("Idempotent-Replayed")).isEqualTo("false");
        assertThat(second.getHeaders().getFirst("Idempotent-Replayed")).isEqualTo("true");
        assertThat(second.getBody().get("id")).isEqualTo(first.getBody().get("id"));
        assertThat(balance(ana)).isEqualTo("30.00");
        assertError(requestTransfer(ana, key, "21.00", idempotencyKey), HttpStatus.UNPROCESSABLE_ENTITY,
                "IDEMPOTENCY_KEY_REUSED");
    }

    @Test
    void repeatedDepositKeyDoesNotDepositAgain() {
        String ana = openAccount("Ana Souza");
        String idempotencyKey = UUID.randomUUID().toString();
        String path = ACCOUNTS + "/" + ana + "/deposits";

        ResponseEntity<JsonNode> first = post(path, Collections.singletonMap("amount", "10.00"), idempotencyKey);
        ResponseEntity<JsonNode> second = post(path, Collections.singletonMap("amount", "10.00"), idempotencyKey);

        assertThat(first.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(second.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(balance(ana)).isEqualTo("10.00");
    }

    @Test
    void requiresValidIdempotencyKey() {
        String ana = openAccount("Ana Souza");
        String key = registerEmail(openAccount("Bia Lima"));
        deposit(ana, "10.00");

        assertError(post(TRANSFERS, transferBody(ana, key, "1.00")), HttpStatus.BAD_REQUEST, "INVALID_REQUEST");
        assertError(requestTransfer(ana, key, "1.00", "chave inválida"), HttpStatus.BAD_REQUEST,
                "INVALID_IDEMPOTENCY_KEY");
        assertThat(balance(ana)).isEqualTo("10.00");
    }

    @Test
    void databaseRejectsLedgerChanges() {
        String ana = openAccount("Ana Souza");
        deposit(ana, "10.00");
        UUID id = UUID.fromString(ana);

        assertThatThrownBy(() -> jdbc.update("UPDATE ledger_entries SET amount_cents = 1 WHERE account_id = ?", id))
                .isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> jdbc.update("DELETE FROM ledger_entries WHERE account_id = ?", id))
                .isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> jdbc.execute("TRUNCATE ledger_entries, journal_transactions"))
                .isInstanceOf(DataAccessException.class);
        assertThat(balance(ana)).isEqualTo("10.00");
    }

    @Test
    void databaseRejectsUnbalancedJournalOnCommit() {
        UUID journal = UUID.randomUUID();

        assertThatThrownBy(() -> transactions.executeWithoutResult(status -> {
            jdbc.update("INSERT INTO journal_transactions (id, kind, reference, occurred_at) VALUES (?, ?, ?, ?)",
                    journal, "DEPOSIT", UUID.randomUUID(), Timestamp.from(Instant.now()));
            jdbc.update("INSERT INTO ledger_entries (journal_id, account_id, entry_type, amount_cents) "
                    + "VALUES (?, ?, 'CREDIT', 100)", journal, SystemAccounts.CLEARING.value());
        })).isInstanceOf(TransactionSystemException.class)
                .hasMessageContaining("desequilibrada");

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM journal_transactions WHERE id = ?", Long.class, journal))
                .isZero();
    }

    @Test
    void databaseAllowsOnlyOneConclusionPerTransfer() {
        UUID transfer = UUID.randomUUID();
        AccountId clearing = SystemAccounts.CLEARING;
        AccountId external = SystemAccounts.EXTERNAL_CASH;

        assertThatThrownBy(() -> transactions.executeWithoutResult(status -> {
            ledger.append(JournalTransaction.move(JournalKind.TRANSFER_SETTLEMENT, clearing, external,
                    Money.ofCents(100), transfer, Instant.now()));
            ledger.append(JournalTransaction.move(JournalKind.TRANSFER_REFUND, clearing, external,
                    Money.ofCents(100), transfer, Instant.now()));
        })).isInstanceOf(DuplicateKeyException.class);
    }

    @Test
    void ledgerNetsToZeroAcrossAllAccounts() {
        String ana = openAccount("Ana Souza");
        String key = registerEmail(openAccount("Bia Lima"));
        deposit(ana, "25.00");
        requestTransfer(ana, key, "5.00");

        Long net = jdbc.queryForObject("SELECT COALESCE(SUM(CASE WHEN entry_type = 'CREDIT' THEN amount_cents "
                + "ELSE -amount_cents END), 0)::bigint FROM ledger_entries", Long.class);

        assertThat(net).isZero();
    }

    private String openAccount(String holderName) {
        ResponseEntity<JsonNode> response = post(ACCOUNTS, Collections.singletonMap("holderName", holderName));
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return response.getBody().get("id").asText();
    }

    private String registerEmail(String accountId) {
        String email = "conta-" + UUID.randomUUID() + "@example.com";
        ResponseEntity<JsonNode> response = post(ACCOUNTS + "/" + accountId + "/pix-keys", pixKey("EMAIL", email));
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return email;
    }

    private void deposit(String accountId, String amount) {
        ResponseEntity<JsonNode> response = post(ACCOUNTS + "/" + accountId + "/deposits",
                Collections.singletonMap("amount", amount), UUID.randomUUID().toString());
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    }

    private String balance(String accountId) {
        return http.getForObject(ACCOUNTS + "/" + accountId + "/balance", JsonNode.class).get("balance").asText();
    }

    private ResponseEntity<JsonNode> requestTransfer(String payer, String email, String amount) {
        return requestTransfer(payer, email, amount, UUID.randomUUID().toString());
    }

    private ResponseEntity<JsonNode> requestTransfer(String payer, String email, String amount, String idempotencyKey) {
        return post(TRANSFERS, transferBody(payer, email, amount), idempotencyKey);
    }

    private static Map<String, String> transferBody(String payer, String email, String amount) {
        Map<String, String> body = new HashMap<>();
        body.put("payerAccountId", payer);
        body.put("pixKeyType", "EMAIL");
        body.put("pixKey", email);
        body.put("amount", amount);
        return body;
    }

    private static Map<String, String> pixKey(String type, String value) {
        Map<String, String> body = new HashMap<>();
        body.put("type", type);
        body.put("value", value);
        return body;
    }

    private ResponseEntity<JsonNode> post(String path, Map<String, String> body) {
        return post(path, body, null);
    }

    private ResponseEntity<JsonNode> post(String path, Map<String, String> body, String idempotencyKey) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (idempotencyKey != null) {
            headers.set("Idempotency-Key", idempotencyKey);
        }
        return http.postForEntity(path, new HttpEntity<>(body, headers), JsonNode.class);
    }

    private static void assertError(ResponseEntity<JsonNode> response, HttpStatus status, String code) {
        assertThat(response.getStatusCode()).isEqualTo(status);
        assertThat(response.getBody().get("code").asText()).isEqualTo(code);
        assertThat(response.getBody().get("status").asInt()).isEqualTo(status.value());
    }
}
