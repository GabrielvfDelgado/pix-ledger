package io.github.gabrielvfdelgado.pixledger.application.port.in;

import io.github.gabrielvfdelgado.pixledger.domain.account.AccountId;
import io.github.gabrielvfdelgado.pixledger.domain.shared.Money;
import io.github.gabrielvfdelgado.pixledger.domain.transfer.Transfer;
import io.github.gabrielvfdelgado.pixledger.domain.transfer.TransferId;
import io.github.gabrielvfdelgado.pixledger.domain.transfer.TransferStatus;
import java.time.Instant;

/** Retrato imutável de uma transferência para quem está fora do hexágono. */
public final class TransferView {

    private final TransferId id;
    private final AccountId payerId;
    private final AccountId payeeId;
    private final Money amount;
    private final TransferStatus status;
    private final String failureReason;
    private final Instant requestedAt;
    private final Instant updatedAt;

    private TransferView(Transfer transfer) {
        this.id = transfer.id();
        this.payerId = transfer.payerId();
        this.payeeId = transfer.payeeId();
        this.amount = transfer.amount();
        this.status = transfer.status();
        this.failureReason = transfer.failureReason();
        this.requestedAt = transfer.requestedAt();
        this.updatedAt = transfer.updatedAt();
    }

    public static TransferView from(Transfer transfer) {
        return new TransferView(transfer);
    }

    public TransferId id() {
        return id;
    }

    public AccountId payerId() {
        return payerId;
    }

    public AccountId payeeId() {
        return payeeId;
    }

    public Money amount() {
        return amount;
    }

    public TransferStatus status() {
        return status;
    }

    public String failureReason() {
        return failureReason;
    }

    public Instant requestedAt() {
        return requestedAt;
    }

    public Instant updatedAt() {
        return updatedAt;
    }
}
