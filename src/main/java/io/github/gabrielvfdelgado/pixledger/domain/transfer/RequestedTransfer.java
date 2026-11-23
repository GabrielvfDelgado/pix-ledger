package io.github.gabrielvfdelgado.pixledger.domain.transfer;

import io.github.gabrielvfdelgado.pixledger.domain.ledger.JournalTransaction;
import java.util.Objects;

/** Transferência recém-criada e o lançamento que reserva o valor do pagador. */
public final class RequestedTransfer {

    private final Transfer transfer;
    private final JournalTransaction hold;

    RequestedTransfer(Transfer transfer, JournalTransaction hold) {
        this.transfer = Objects.requireNonNull(transfer, "transfer");
        this.hold = Objects.requireNonNull(hold, "hold");
    }

    public Transfer transfer() {
        return transfer;
    }

    public JournalTransaction hold() {
        return hold;
    }
}
