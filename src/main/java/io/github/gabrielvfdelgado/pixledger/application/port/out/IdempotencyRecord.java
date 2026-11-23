package io.github.gabrielvfdelgado.pixledger.application.port.out;

import java.util.Objects;
import java.util.UUID;

/** O que foi pedido com uma chave de idempotência e qual recurso essa chave produziu. */
public final class IdempotencyRecord {

    private final String fingerprint;
    private final UUID resourceId;

    public IdempotencyRecord(String fingerprint, UUID resourceId) {
        this.fingerprint = Objects.requireNonNull(fingerprint, "fingerprint");
        this.resourceId = Objects.requireNonNull(resourceId, "resourceId");
    }

    public String fingerprint() {
        return fingerprint;
    }

    public UUID resourceId() {
        return resourceId;
    }
}
