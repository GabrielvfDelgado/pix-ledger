package io.github.gabrielvfdelgado.pixledger.domain.ledger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.gabrielvfdelgado.pixledger.domain.account.AccountId;
import io.github.gabrielvfdelgado.pixledger.domain.shared.DomainException;
import io.github.gabrielvfdelgado.pixledger.domain.shared.ErrorCode;
import io.github.gabrielvfdelgado.pixledger.domain.shared.Money;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class JournalTransactionTest {

    private static final Instant NOW = Instant.parse("2026-01-01T12:00:00Z");

    private final AccountId ana = AccountId.generate();
    private final AccountId bia = AccountId.generate();

    @Test
    void depositCreditsAccountAgainstExternalCash() {
        JournalTransaction deposit = JournalTransaction.deposit(ana, Money.ofCents(1000), UUID.randomUUID(), NOW);

        assertThat(Ledger.balanceOf(ana, deposit.entries())).isEqualTo(Money.ofCents(1000));
        assertThat(Ledger.balanceOf(SystemAccounts.EXTERNAL_CASH, deposit.entries())).isEqualTo(Money.ofCents(-1000));
        assertThat(Ledger.netOf(deposit.entries())).isEqualTo(Money.ZERO);
    }

    @Test
    void rejectsUnbalancedTransaction() {
        assertUnbalanced(LedgerEntry.debit(ana, Money.ofCents(100)), LedgerEntry.credit(bia, Money.ofCents(99)));
    }

    @Test
    void rejectsTransactionWithSingleEntry() {
        assertThatThrownBy(() -> JournalTransaction.restore(JournalId.generate(), JournalKind.DEPOSIT,
                UUID.randomUUID(), Collections.singletonList(LedgerEntry.credit(ana, Money.ofCents(100))), NOW))
                .isInstanceOfSatisfying(DomainException.class,
                        e -> assertThat(e.code()).isEqualTo(ErrorCode.UNBALANCED_TRANSACTION));
    }

    @Test
    void acceptsBalancedTransactionWithSeveralEntries() {
        JournalTransaction transaction = JournalTransaction.restore(JournalId.generate(), JournalKind.DEPOSIT,
                UUID.randomUUID(), Arrays.asList(
                        LedgerEntry.debit(SystemAccounts.EXTERNAL_CASH, Money.ofCents(300)),
                        LedgerEntry.credit(ana, Money.ofCents(100)),
                        LedgerEntry.credit(bia, Money.ofCents(200))), NOW);

        assertThat(Ledger.netOf(transaction.entries())).isEqualTo(Money.ZERO);
    }

    @Test
    void rejectsMoveToSameAccount() {
        assertThatThrownBy(() -> JournalTransaction.move(JournalKind.TRANSFER_HOLD, ana, ana, Money.ofCents(100),
                UUID.randomUUID(), NOW))
                .isInstanceOfSatisfying(DomainException.class,
                        e -> assertThat(e.code()).isEqualTo(ErrorCode.SAME_ACCOUNT));
    }

    @Test
    void rejectsZeroOrNegativeEntry() {
        assertThatThrownBy(() -> LedgerEntry.credit(ana, Money.ZERO)).isInstanceOf(DomainException.class);
        assertThatThrownBy(() -> LedgerEntry.debit(ana, Money.ofCents(-1))).isInstanceOf(DomainException.class);
    }

    @Test
    void entriesAreImmutable() {
        JournalTransaction deposit = JournalTransaction.deposit(ana, Money.ofCents(100), UUID.randomUUID(), NOW);

        assertThatThrownBy(() -> deposit.entries().clear()).isInstanceOf(UnsupportedOperationException.class);
    }

    private void assertUnbalanced(LedgerEntry... entries) {
        assertThatThrownBy(() -> JournalTransaction.restore(JournalId.generate(), JournalKind.DEPOSIT,
                UUID.randomUUID(), Arrays.asList(entries), NOW))
                .isInstanceOfSatisfying(DomainException.class,
                        e -> assertThat(e.code()).isEqualTo(ErrorCode.UNBALANCED_TRANSACTION));
    }
}
