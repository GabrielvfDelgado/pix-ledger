package io.github.gabrielvfdelgado.pixledger.domain.ledger;

import io.github.gabrielvfdelgado.pixledger.domain.account.AccountId;
import io.github.gabrielvfdelgado.pixledger.domain.shared.DomainException;
import io.github.gabrielvfdelgado.pixledger.domain.shared.ErrorCode;
import io.github.gabrielvfdelgado.pixledger.domain.shared.Money;
import java.util.Objects;

/** Lançamento imutável em uma conta. */
public final class LedgerEntry {

    private final AccountId accountId;
    private final EntryType type;
    private final Money amount;

    public LedgerEntry(AccountId accountId, EntryType type, Money amount) {
        this.accountId = Objects.requireNonNull(accountId, "accountId");
        this.type = Objects.requireNonNull(type, "type");
        this.amount = Objects.requireNonNull(amount, "amount");
        if (!amount.isPositive()) {
            throw new DomainException(ErrorCode.INVALID_AMOUNT, "O valor do lançamento deve ser positivo");
        }
    }

    public static LedgerEntry debit(AccountId accountId, Money amount) {
        return new LedgerEntry(accountId, EntryType.DEBIT, amount);
    }

    public static LedgerEntry credit(AccountId accountId, Money amount) {
        return new LedgerEntry(accountId, EntryType.CREDIT, amount);
    }

    /** Efeito no saldo: crédito soma e débito subtrai. */
    public Money signedAmount() {
        return type == EntryType.CREDIT ? amount : Money.ZERO.minus(amount);
    }

    public AccountId accountId() {
        return accountId;
    }

    public EntryType type() {
        return type;
    }

    public Money amount() {
        return amount;
    }

    @Override
    public boolean equals(Object other) {
        if (!(other instanceof LedgerEntry)) {
            return false;
        }
        LedgerEntry entry = (LedgerEntry) other;
        return accountId.equals(entry.accountId) && type == entry.type && amount.equals(entry.amount);
    }

    @Override
    public int hashCode() {
        return Objects.hash(accountId, type, amount);
    }

    @Override
    public String toString() {
        return type + " " + amount + " " + accountId;
    }
}
