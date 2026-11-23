package io.github.gabrielvfdelgado.pixledger.domain.transfer;

import io.github.gabrielvfdelgado.pixledger.domain.shared.Id;
import java.util.UUID;

public final class TransferId extends Id {

    private TransferId(UUID value) {
        super(value);
    }

    public static TransferId of(UUID value) {
        return new TransferId(value);
    }

    public static TransferId generate() {
        return new TransferId(UUID.randomUUID());
    }
}
