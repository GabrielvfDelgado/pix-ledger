package io.github.gabrielvfdelgado.pixledger.domain.ledger;

import io.github.gabrielvfdelgado.pixledger.domain.shared.Id;
import java.util.UUID;

public final class JournalId extends Id {

    private JournalId(UUID value) {
        super(value);
    }

    public static JournalId of(UUID value) {
        return new JournalId(value);
    }

    public static JournalId generate() {
        return new JournalId(UUID.randomUUID());
    }
}
