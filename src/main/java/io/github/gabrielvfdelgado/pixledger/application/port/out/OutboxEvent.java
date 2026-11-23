package io.github.gabrielvfdelgado.pixledger.application.port.out;

import io.github.gabrielvfdelgado.pixledger.domain.transfer.TransferId;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Evento aguardando publicação. */
public final class OutboxEvent {

    private final UUID id;
    private final TransferEventType type;
    private final TransferId transferId;
    private final Instant occurredAt;

    public OutboxEvent(UUID id, TransferEventType type, TransferId transferId, Instant occurredAt) {
        this.id = Objects.requireNonNull(id, "id");
        this.type = Objects.requireNonNull(type, "type");
        this.transferId = Objects.requireNonNull(transferId, "transferId");
        this.occurredAt = Objects.requireNonNull(occurredAt, "occurredAt");
    }

    public static OutboxEvent of(TransferEventType type, TransferId transferId, Instant occurredAt) {
        return new OutboxEvent(UUID.randomUUID(), type, transferId, occurredAt);
    }

    public UUID id() {
        return id;
    }

    public TransferEventType type() {
        return type;
    }

    public TransferId transferId() {
        return transferId;
    }

    public Instant occurredAt() {
        return occurredAt;
    }
}
