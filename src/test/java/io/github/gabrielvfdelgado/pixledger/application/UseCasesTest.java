package io.github.gabrielvfdelgado.pixledger.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.gabrielvfdelgado.pixledger.adapters.out.memory.InMemoryStore;
import io.github.gabrielvfdelgado.pixledger.application.port.in.AccountView;
import io.github.gabrielvfdelgado.pixledger.application.port.in.GetStatementUseCase;
import io.github.gabrielvfdelgado.pixledger.application.port.in.IdempotencyKey;
import io.github.gabrielvfdelgado.pixledger.application.port.in.IdempotentResult;
import io.github.gabrielvfdelgado.pixledger.application.port.in.StatementLine;
import io.github.gabrielvfdelgado.pixledger.application.port.in.TransferView;
import io.github.gabrielvfdelgado.pixledger.application.service.AccountService;
import io.github.gabrielvfdelgado.pixledger.application.service.IdempotentExecutor;
import io.github.gabrielvfdelgado.pixledger.application.service.TransferService;
import io.github.gabrielvfdelgado.pixledger.domain.account.Account;
import io.github.gabrielvfdelgado.pixledger.domain.account.AccountId;
import io.github.gabrielvfdelgado.pixledger.domain.account.PixKey;
import io.github.gabrielvfdelgado.pixledger.domain.account.PixKeyType;
import io.github.gabrielvfdelgado.pixledger.domain.ledger.EntryType;
import io.github.gabrielvfdelgado.pixledger.domain.ledger.JournalKind;
import io.github.gabrielvfdelgado.pixledger.domain.ledger.Ledger;
import io.github.gabrielvfdelgado.pixledger.domain.ledger.SystemAccounts;
import io.github.gabrielvfdelgado.pixledger.domain.shared.DomainException;
import io.github.gabrielvfdelgado.pixledger.domain.shared.ErrorCode;
import io.github.gabrielvfdelgado.pixledger.domain.shared.Money;
import io.github.gabrielvfdelgado.pixledger.domain.transfer.TransferId;
import io.github.gabrielvfdelgado.pixledger.domain.transfer.TransferStatus;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class UseCasesTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-01-01T12:00:00Z"), ZoneOffset.UTC);
    private static final PixKey BIA_KEY = PixKey.of(PixKeyType.EMAIL, "bia@example.com");
    private static final Money HUNDRED = Money.ofCents(10_000);
    private static final Money THIRTY = Money.ofCents(3_000);

    private InMemoryStore store;
    private AccountService accounts;
    private TransferService transfers;
    private AccountId ana;
    private AccountId bia;

    @BeforeEach
    void setUp() {
        store = new InMemoryStore();
        IdempotentExecutor idempotent = new IdempotentExecutor(store, store, store);
        accounts = new AccountService(store, store, store, idempotent, CLOCK);
        transfers = new TransferService(store, store, store, store, store, idempotent, CLOCK);
        ana = accounts.open("Ana Souza").id();
        bia = accounts.open("Bia Lima").id();
        accounts.register(bia, BIA_KEY);
    }

    @Test
    void opensAccountWithZeroBalance() {
        AccountView account = accounts.open("Caio Alves");

        assertThat(account.holderName()).isEqualTo("Caio Alves");
        assertThat(account.createdAt()).isEqualTo(CLOCK.instant());
        assertThat(accounts.balanceOf(account.id())).isEqualTo(Money.ZERO);
    }

    @Test
    void pixKeyBelongsToOnlyOneAccount() {
        assertCode(() -> accounts.register(ana, BIA_KEY), ErrorCode.PIX_KEY_ALREADY_REGISTERED);

        assertThat(store.findById(ana).get().pixKeys()).isEmpty();
    }

    @Test
    void depositIncreasesBalance() {
        assertThat(deposit(ana, HUNDRED)).isEqualTo(HUNDRED);
        assertThat(deposit(ana, THIRTY)).isEqualTo(Money.ofCents(13_000));
    }

    @Test
    void blockedAccountCannotReceiveDeposit() {
        block(ana);

        assertCode(() -> deposit(ana, HUNDRED), ErrorCode.ACCOUNT_BLOCKED);
        assertThat(accounts.balanceOf(ana)).isEqualTo(Money.ZERO);
    }

    @Test
    void transferByPixKeyReservesThenDelivers() {
        deposit(ana, HUNDRED);

        TransferView pending = request(ana, BIA_KEY, THIRTY);

        assertThat(pending.status()).isEqualTo(TransferStatus.PENDING);
        assertThat(pending.payeeId()).isEqualTo(bia);
        assertBalances(Money.ofCents(7_000), Money.ZERO, THIRTY);

        TransferView settled = transfers.settle(pending.id());

        assertThat(settled.status()).isEqualTo(TransferStatus.SETTLED);
        assertThat(transfers.get(pending.id()).status()).isEqualTo(TransferStatus.SETTLED);
        assertBalances(Money.ofCents(7_000), THIRTY, Money.ZERO);
    }

    @Test
    void failedTransferRefundsPayer() {
        deposit(ana, HUNDRED);
        TransferView pending = request(ana, BIA_KEY, THIRTY);

        TransferView failed = transfers.fail(pending.id(), "banco indisponível");

        assertThat(failed.status()).isEqualTo(TransferStatus.FAILED);
        assertThat(failed.failureReason()).isEqualTo("banco indisponível");
        assertBalances(HUNDRED, Money.ZERO, Money.ZERO);
    }

    @Test
    void repeatedConclusionDoesNotMoveMoneyAgain() {
        deposit(ana, HUNDRED);
        TransferView pending = request(ana, BIA_KEY, THIRTY);
        transfers.settle(pending.id());

        assertCode(() -> transfers.settle(pending.id()), ErrorCode.INVALID_TRANSFER_STATE);
        assertCode(() -> transfers.fail(pending.id(), "erro"), ErrorCode.INVALID_TRANSFER_STATE);

        assertBalances(Money.ofCents(7_000), THIRTY, Money.ZERO);
    }

    @Test
    void rejectedTransferLeavesNoTrace() {
        deposit(ana, THIRTY);
        int entriesBefore = store.allEntries().size();

        assertCode(() -> request(ana, BIA_KEY, HUNDRED), ErrorCode.INSUFFICIENT_FUNDS);

        assertThat(store.allEntries()).hasSize(entriesBefore);
        assertBalances(THIRTY, Money.ZERO, Money.ZERO);
    }

    @Test
    void rejectsTransferToOwnPixKey() {
        deposit(bia, HUNDRED);

        assertCode(() -> request(bia, BIA_KEY, THIRTY), ErrorCode.SAME_ACCOUNT);
    }

    @Test
    void reportsMissingResources() {
        AccountId unknown = AccountId.generate();
        PixKey unknownKey = PixKey.random();

        assertCode(() -> accounts.balanceOf(unknown), ErrorCode.ACCOUNT_NOT_FOUND);
        assertCode(() -> deposit(unknown, HUNDRED), ErrorCode.ACCOUNT_NOT_FOUND);
        assertCode(() -> accounts.statementOf(unknown, 10), ErrorCode.ACCOUNT_NOT_FOUND);
        assertCode(() -> request(unknown, BIA_KEY, THIRTY), ErrorCode.ACCOUNT_NOT_FOUND);
        assertCode(() -> request(ana, unknownKey, THIRTY), ErrorCode.PIX_KEY_NOT_FOUND);
        assertCode(() -> transfers.get(TransferId.generate()), ErrorCode.TRANSFER_NOT_FOUND);
    }

    @Test
    void systemAccountsAreNotExposedAsCustomerAccounts() {
        assertCode(() -> accounts.balanceOf(SystemAccounts.CLEARING), ErrorCode.ACCOUNT_NOT_FOUND);
        assertCode(() -> deposit(SystemAccounts.EXTERNAL_CASH, HUNDRED), ErrorCode.ACCOUNT_NOT_FOUND);
    }

    @Test
    void statementShowsNewestEntriesFirst() {
        deposit(ana, HUNDRED);
        TransferView transfer = request(ana, BIA_KEY, THIRTY);
        transfers.fail(transfer.id(), "erro");

        List<StatementLine> statement = accounts.statementOf(ana, 10);

        assertThat(statement.stream().map(StatementLine::kind).collect(Collectors.toList()))
                .containsExactly(JournalKind.TRANSFER_REFUND, JournalKind.TRANSFER_HOLD, JournalKind.DEPOSIT);
        assertThat(statement.stream().map(StatementLine::type).collect(Collectors.toList()))
                .containsExactly(EntryType.CREDIT, EntryType.DEBIT, EntryType.CREDIT);
        assertThat(statement.get(0).reference()).isEqualTo(transfer.id().value());
    }

    @Test
    void statementRespectsLimit() {
        deposit(ana, THIRTY);
        deposit(ana, THIRTY);
        deposit(ana, THIRTY);

        assertThat(accounts.statementOf(ana, 2)).hasSize(2);
        assertCode(() -> accounts.statementOf(ana, 0), ErrorCode.INVALID_STATEMENT_LIMIT);
        assertCode(() -> accounts.statementOf(ana, GetStatementUseCase.MAX_LIMIT + 1),
                ErrorCode.INVALID_STATEMENT_LIMIT);
    }

    @Test
    void transactionRollsBackEverythingOnFailure() {
        deposit(ana, HUNDRED);

        assertThatThrownBy(() -> store.inTransaction(() -> {
            request(ana, BIA_KEY, THIRTY);
            throw new IllegalStateException("falha no meio da transação");
        })).isInstanceOf(IllegalStateException.class);

        assertBalances(HUNDRED, Money.ZERO, Money.ZERO);
    }

    @Test
    void repeatedTransferKeyDoesNotChargeAgain() {
        deposit(ana, HUNDRED);
        IdempotencyKey key = IdempotencyKey.of("pedido-1");

        IdempotentResult<TransferView> first = transfers.request(key, ana, BIA_KEY, THIRTY);
        IdempotentResult<TransferView> second = transfers.request(key, ana, BIA_KEY, THIRTY);

        assertThat(first.replayed()).isFalse();
        assertThat(second.replayed()).isTrue();
        assertThat(second.value().id()).isEqualTo(first.value().id());
        assertBalances(Money.ofCents(7_000), Money.ZERO, THIRTY);
    }

    @Test
    void repeatedDepositKeyDoesNotDepositAgain() {
        IdempotencyKey key = IdempotencyKey.of("deposito-1");

        accounts.deposit(key, ana, HUNDRED);
        IdempotentResult<Money> repeated = accounts.deposit(key, ana, HUNDRED);

        assertThat(repeated.replayed()).isTrue();
        assertThat(repeated.value()).isEqualTo(HUNDRED);
    }

    @Test
    void sameKeyWithDifferentRequestIsRejected() {
        deposit(ana, HUNDRED);
        IdempotencyKey key = IdempotencyKey.of("pedido-2");
        transfers.request(key, ana, BIA_KEY, THIRTY);

        assertCode(() -> transfers.request(key, ana, BIA_KEY, Money.ofCents(1)), ErrorCode.IDEMPOTENCY_KEY_REUSED);
        assertBalances(Money.ofCents(7_000), Money.ZERO, THIRTY);
    }

    @Test
    void failedRequestReleasesKeyForRetry() {
        IdempotencyKey key = IdempotencyKey.of("pedido-3");
        assertCode(() -> transfers.request(key, ana, BIA_KEY, THIRTY), ErrorCode.INSUFFICIENT_FUNDS);
        deposit(ana, THIRTY);

        IdempotentResult<TransferView> retry = transfers.request(key, ana, BIA_KEY, THIRTY);

        assertThat(retry.replayed()).isFalse();
        assertBalances(Money.ZERO, Money.ZERO, THIRTY);
    }

    @Test
    void sameKeyCanBeUsedByDifferentPayers() {
        deposit(ana, HUNDRED);
        AccountId caio = accounts.open("Caio Alves").id();
        deposit(caio, HUNDRED);
        IdempotencyKey key = IdempotencyKey.of("pedido-4");

        assertThat(transfers.request(key, ana, BIA_KEY, THIRTY).replayed()).isFalse();
        assertThat(transfers.request(key, caio, BIA_KEY, THIRTY).replayed()).isFalse();
    }

    @Test
    void rejectsMalformedIdempotencyKey() {
        assertCode(() -> IdempotencyKey.of(""), ErrorCode.INVALID_IDEMPOTENCY_KEY);
        assertCode(() -> IdempotencyKey.of("com espaço"), ErrorCode.INVALID_IDEMPOTENCY_KEY);
        assertCode(() -> IdempotencyKey.of(null), ErrorCode.INVALID_IDEMPOTENCY_KEY);
    }

    private Money deposit(AccountId id, Money amount) {
        return accounts.deposit(newKey(), id, amount).value();
    }

    private TransferView request(AccountId payer, PixKey key, Money amount) {
        return transfers.request(newKey(), payer, key, amount).value();
    }

    private static IdempotencyKey newKey() {
        return IdempotencyKey.of(UUID.randomUUID().toString());
    }

    private void block(AccountId id) {
        Account account = store.findById(id).get();
        account.block();
        store.save(account);
    }

    private void assertBalances(Money expectedAna, Money expectedBia, Money expectedClearing) {
        assertThat(accounts.balanceOf(ana)).isEqualTo(expectedAna);
        assertThat(accounts.balanceOf(bia)).isEqualTo(expectedBia);
        assertThat(store.balanceOf(SystemAccounts.CLEARING)).isEqualTo(expectedClearing);
        assertThat(Ledger.netOf(store.allEntries())).isEqualTo(Money.ZERO);
    }

    private static void assertCode(ThrowingCallable action, ErrorCode code) {
        assertThatThrownBy(action)
                .isInstanceOfSatisfying(DomainException.class, e -> assertThat(e.code()).isEqualTo(code));
    }
}
