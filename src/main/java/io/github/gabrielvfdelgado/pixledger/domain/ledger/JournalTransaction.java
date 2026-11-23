package io.github.gabrielvfdelgado.pixledger.domain.ledger;

import io.github.gabrielvfdelgado.pixledger.domain.account.AccountId;
import io.github.gabrielvfdelgado.pixledger.domain.shared.DomainException;
import io.github.gabrielvfdelgado.pixledger.domain.shared.ErrorCode;
import io.github.gabrielvfdelgado.pixledger.domain.shared.Money;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Conjunto imutável de lançamentos que precisa sempre fechar em zero. */
public final class JournalTransaction {

    private static final int MIN_ENTRIES = 2;

    private final JournalId id;
    private final JournalKind kind;
    private final UUID reference;
    private final List<LedgerEntry> entries;
    private final Instant occurredAt;

    private JournalTransaction(JournalId id, JournalKind kind, UUID reference, Collection<LedgerEntry> entries,
            Instant occurredAt) {
        this.id = Objects.requireNonNull(id, "id");
        this.kind = Objects.requireNonNull(kind, "kind");
        this.reference = Objects.requireNonNull(reference, "reference");
        this.entries = Collections.unmodifiableList(new ArrayList<>(entries));
        this.occurredAt = Objects.requireNonNull(occurredAt, "occurredAt");
        if (this.entries.size() < MIN_ENTRIES || !Ledger.netOf(this.entries).equals(Money.ZERO)) {
            throw new DomainException(ErrorCode.UNBALANCED_TRANSACTION, "Os débitos e créditos precisam ser iguais");
        }
    }

    public static JournalTransaction deposit(AccountId accountId, Money amount, UUID reference, Instant now) {
        return move(JournalKind.DEPOSIT, SystemAccounts.EXTERNAL_CASH, accountId, amount, reference, now);
    }

    /** Move o valor de uma conta para outra em um único lançamento duplo. */
    public static JournalTransaction move(JournalKind kind, AccountId from, AccountId to, Money amount, UUID reference,
            Instant now) {
        if (from.equals(to)) {
            throw new DomainException(ErrorCode.SAME_ACCOUNT, "As contas de origem e destino devem ser diferentes");
        }
        List<LedgerEntry> entries = Arrays.asList(LedgerEntry.debit(from, amount), LedgerEntry.credit(to, amount));
        return new JournalTransaction(JournalId.generate(), kind, reference, entries, now);
    }

    /** Recria uma transação persistida, validando novamente que ela fecha em zero. */
    public static JournalTransaction restore(JournalId id, JournalKind kind, UUID reference,
            Collection<LedgerEntry> entries, Instant occurredAt) {
        return new JournalTransaction(id, kind, reference, entries, occurredAt);
    }

    public JournalId id() {
        return id;
    }

    public JournalKind kind() {
        return kind;
    }

    public UUID reference() {
        return reference;
    }

    public List<LedgerEntry> entries() {
        return entries;
    }

    public Instant occurredAt() {
        return occurredAt;
    }
}
