package io.github.gabrielvfdelgado.pixledger.domain.transfer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.gabrielvfdelgado.pixledger.domain.account.Account;
import io.github.gabrielvfdelgado.pixledger.domain.account.AccountId;
import io.github.gabrielvfdelgado.pixledger.domain.ledger.JournalKind;
import io.github.gabrielvfdelgado.pixledger.domain.ledger.JournalTransaction;
import io.github.gabrielvfdelgado.pixledger.domain.ledger.Ledger;
import io.github.gabrielvfdelgado.pixledger.domain.ledger.LedgerEntry;
import io.github.gabrielvfdelgado.pixledger.domain.ledger.SystemAccounts;
import io.github.gabrielvfdelgado.pixledger.domain.shared.DomainException;
import io.github.gabrielvfdelgado.pixledger.domain.shared.ErrorCode;
import io.github.gabrielvfdelgado.pixledger.domain.shared.Money;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.Test;

class TransferTest {

    private static final Instant NOW = Instant.parse("2026-01-01T12:00:00Z");
    private static final Instant LATER = NOW.plusSeconds(5);
    private static final Money HUNDRED = Money.ofCents(10_000);
    private static final Money THIRTY = Money.ofCents(3_000);

    private final Account payer = Account.open(AccountId.generate(), "Ana Souza", NOW);
    private final Account payee = Account.open(AccountId.generate(), "Bia Lima", NOW);
    private final List<LedgerEntry> ledger = new ArrayList<>();

    @Test
    void requestMovesValueFromPayerToClearing() {
        deposit(payer, HUNDRED);

        RequestedTransfer requested = request(THIRTY);
        post(requested.hold());

        assertThat(requested.transfer().status()).isEqualTo(TransferStatus.PENDING);
        assertThat(requested.hold().kind()).isEqualTo(JournalKind.TRANSFER_HOLD);
        assertBalances(Money.ofCents(7_000), Money.ZERO, THIRTY);
    }

    @Test
    void settlementDeliversValueToPayee() {
        deposit(payer, HUNDRED);
        RequestedTransfer requested = request(THIRTY);
        post(requested.hold());

        post(requested.transfer().settle(LATER));

        assertThat(requested.transfer().status()).isEqualTo(TransferStatus.SETTLED);
        assertThat(requested.transfer().updatedAt()).isEqualTo(LATER);
        assertBalances(Money.ofCents(7_000), THIRTY, Money.ZERO);
    }

    @Test
    void failureRefundsPayer() {
        deposit(payer, HUNDRED);
        RequestedTransfer requested = request(THIRTY);
        post(requested.hold());

        post(requested.transfer().fail(" banco indisponível ", LATER));

        assertThat(requested.transfer().status()).isEqualTo(TransferStatus.FAILED);
        assertThat(requested.transfer().failureReason()).isEqualTo("banco indisponível");
        assertBalances(HUNDRED, Money.ZERO, Money.ZERO);
    }

    @Test
    void allowsTransferOfEntireBalance() {
        deposit(payer, HUNDRED);

        post(request(HUNDRED).hold());

        assertThat(balance(payer.id())).isEqualTo(Money.ZERO);
    }

    @Test
    void rejectsInsufficientFunds() {
        deposit(payer, THIRTY);

        assertCode(() -> request(HUNDRED), ErrorCode.INSUFFICIENT_FUNDS);
    }

    @Test
    void rejectsZeroAndNegativeAmounts() {
        deposit(payer, HUNDRED);

        assertCode(() -> request(Money.ZERO), ErrorCode.INVALID_AMOUNT);
        assertCode(() -> request(Money.ofCents(-1)), ErrorCode.INVALID_AMOUNT);
    }

    @Test
    void rejectsTransferToSameAccount() {
        assertCode(() -> Transfer.request(TransferId.generate(), payer, payer, THIRTY, HUNDRED, NOW),
                ErrorCode.SAME_ACCOUNT);
    }

    @Test
    void rejectsBlockedPayerAndPayee() {
        deposit(payer, HUNDRED);

        payer.block();
        assertCode(() -> request(THIRTY), ErrorCode.ACCOUNT_BLOCKED);

        payer.activate();
        payee.block();
        assertCode(() -> request(THIRTY), ErrorCode.ACCOUNT_BLOCKED);
    }

    @Test
    void cannotConcludeTransferTwice() {
        deposit(payer, HUNDRED);
        Transfer settled = request(THIRTY).transfer();
        settled.settle(LATER);
        Transfer failed = request(THIRTY).transfer();
        failed.fail("erro", LATER);

        assertCode(() -> settled.settle(LATER), ErrorCode.INVALID_TRANSFER_STATE);
        assertCode(() -> settled.fail("erro", LATER), ErrorCode.INVALID_TRANSFER_STATE);
        assertCode(() -> failed.settle(LATER), ErrorCode.INVALID_TRANSFER_STATE);
        assertCode(() -> failed.fail("erro", LATER), ErrorCode.INVALID_TRANSFER_STATE);
    }

    @Test
    void failureRequiresReasonAndKeepsTransferPending() {
        deposit(payer, HUNDRED);
        Transfer transfer = request(THIRTY).transfer();

        assertCode(() -> transfer.fail("  ", LATER), ErrorCode.INVALID_FAILURE_REASON);

        assertThat(transfer.status()).isEqualTo(TransferStatus.PENDING);
    }

    @Test
    void randomOperationsNeverCreateMoneyOrOverdraw() {
        Random random = new Random(42);
        deposit(payer, HUNDRED);
        deposit(payee, HUNDRED);
        List<Transfer> pending = new ArrayList<>();

        for (int i = 0; i < 2_000; i++) {
            int action = random.nextInt(3);
            if (action == 0 || pending.isEmpty()) {
                boolean fromPayer = random.nextBoolean();
                Account from = fromPayer ? payer : payee;
                Account to = fromPayer ? payee : payer;
                Money amount = Money.ofCents(1 + random.nextInt(4_000));
                if (!balance(from.id()).isLessThan(amount)) {
                    RequestedTransfer requested = Transfer.request(TransferId.generate(), from, to, amount,
                            balance(from.id()), NOW);
                    post(requested.hold());
                    pending.add(requested.transfer());
                }
            } else {
                Transfer transfer = pending.remove(random.nextInt(pending.size()));
                post(action == 1 ? transfer.settle(LATER) : transfer.fail("falha simulada", LATER));
            }

            assertThat(balance(payer.id()).isNegative()).isFalse();
            assertThat(balance(payee.id()).isNegative()).isFalse();
            assertThat(balance(SystemAccounts.CLEARING).isNegative()).isFalse();
            assertThat(Ledger.netOf(ledger)).isEqualTo(Money.ZERO);
        }

        pending.forEach(transfer -> post(transfer.settle(LATER)));

        assertThat(balance(SystemAccounts.CLEARING)).isEqualTo(Money.ZERO);
        assertThat(balance(payer.id()).plus(balance(payee.id()))).isEqualTo(HUNDRED.plus(HUNDRED));
    }

    private RequestedTransfer request(Money amount) {
        return Transfer.request(TransferId.generate(), payer, payee, amount, balance(payer.id()), NOW);
    }

    private void deposit(Account account, Money amount) {
        post(JournalTransaction.deposit(account.id(), amount, UUID.randomUUID(), NOW));
    }

    private void post(JournalTransaction transaction) {
        ledger.addAll(transaction.entries());
    }

    private Money balance(AccountId accountId) {
        return Ledger.balanceOf(accountId, ledger);
    }

    private void assertBalances(Money expectedPayer, Money expectedPayee, Money expectedClearing) {
        assertThat(balance(payer.id())).isEqualTo(expectedPayer);
        assertThat(balance(payee.id())).isEqualTo(expectedPayee);
        assertThat(balance(SystemAccounts.CLEARING)).isEqualTo(expectedClearing);
        assertThat(Ledger.netOf(ledger)).isEqualTo(Money.ZERO);
    }

    private static void assertCode(ThrowingCallable action, ErrorCode code) {
        assertThatThrownBy(action)
                .isInstanceOfSatisfying(DomainException.class, e -> assertThat(e.code()).isEqualTo(code));
    }
}
