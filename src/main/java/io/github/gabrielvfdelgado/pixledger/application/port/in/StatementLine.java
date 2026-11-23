package io.github.gabrielvfdelgado.pixledger.application.port.in;

import io.github.gabrielvfdelgado.pixledger.domain.ledger.EntryType;
import io.github.gabrielvfdelgado.pixledger.domain.ledger.JournalId;
import io.github.gabrielvfdelgado.pixledger.domain.ledger.JournalKind;
import io.github.gabrielvfdelgado.pixledger.domain.shared.Money;
import java.time.Instant;
import java.util.UUID;

/** Uma linha do extrato de uma conta. */
public final class StatementLine {

    private final JournalId journalId;
    private final JournalKind kind;
    private final EntryType type;
    private final Money amount;
    private final UUID reference;
    private final Instant occurredAt;

    public StatementLine(JournalId journalId, JournalKind kind, EntryType type, Money amount, UUID reference,
            Instant occurredAt) {
        this.journalId = journalId;
        this.kind = kind;
        this.type = type;
        this.amount = amount;
        this.reference = reference;
        this.occurredAt = occurredAt;
    }

    public JournalId journalId() {
        return journalId;
    }

    public JournalKind kind() {
        return kind;
    }

    public EntryType type() {
        return type;
    }

    public Money amount() {
        return amount;
    }

    public UUID reference() {
        return reference;
    }

    public Instant occurredAt() {
        return occurredAt;
    }
}
