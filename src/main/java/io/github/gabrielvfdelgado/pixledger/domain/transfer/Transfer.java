package io.github.gabrielvfdelgado.pixledger.domain.transfer;

import io.github.gabrielvfdelgado.pixledger.domain.account.Account;
import io.github.gabrielvfdelgado.pixledger.domain.account.AccountId;
import io.github.gabrielvfdelgado.pixledger.domain.ledger.JournalKind;
import io.github.gabrielvfdelgado.pixledger.domain.ledger.JournalTransaction;
import io.github.gabrielvfdelgado.pixledger.domain.ledger.SystemAccounts;
import io.github.gabrielvfdelgado.pixledger.domain.shared.DomainException;
import io.github.gabrielvfdelgado.pixledger.domain.shared.ErrorCode;
import io.github.gabrielvfdelgado.pixledger.domain.shared.Money;
import java.time.Instant;
import java.util.Objects;

/**
 * Ciclo de vida de uma transferência.
 *
 * <p>Ao ser pedida, o valor sai do pagador e fica na conta de compensação. Depois, ele vai
 * para o recebedor se a transferência for liquidada, ou volta ao pagador se ela falhar.
 */
public final class Transfer {

    private final TransferId id;
    private final AccountId payerId;
    private final AccountId payeeId;
    private final Money amount;
    private final Instant requestedAt;
    private TransferStatus status;
    private String failureReason;
    private Instant updatedAt;

    private Transfer(TransferId id, AccountId payerId, AccountId payeeId, Money amount, TransferStatus status,
            String failureReason, Instant requestedAt, Instant updatedAt) {
        this.id = Objects.requireNonNull(id, "id");
        this.payerId = Objects.requireNonNull(payerId, "payerId");
        this.payeeId = Objects.requireNonNull(payeeId, "payeeId");
        this.amount = Objects.requireNonNull(amount, "amount");
        this.status = Objects.requireNonNull(status, "status");
        this.failureReason = failureReason;
        this.requestedAt = Objects.requireNonNull(requestedAt, "requestedAt");
        this.updatedAt = Objects.requireNonNull(updatedAt, "updatedAt");
    }

    /** O saldo informado deve ter sido lido com a conta do pagador protegida contra concorrência. */
    public static RequestedTransfer request(TransferId id, Account payer, Account payee, Money amount,
            Money payerBalance, Instant now) {
        if (payer.id().equals(payee.id())) {
            throw new DomainException(ErrorCode.SAME_ACCOUNT, "As contas de origem e destino devem ser diferentes");
        }
        if (!amount.isPositive()) {
            throw new DomainException(ErrorCode.INVALID_AMOUNT, "O valor da transferência deve ser positivo");
        }
        payer.ensureActive();
        payee.ensureActive();
        if (payerBalance.isLessThan(amount)) {
            throw new DomainException(ErrorCode.INSUFFICIENT_FUNDS, "Saldo insuficiente");
        }
        Transfer transfer = new Transfer(id, payer.id(), payee.id(), amount, TransferStatus.PENDING, null, now, now);
        JournalTransaction hold = JournalTransaction.move(JournalKind.TRANSFER_HOLD, payer.id(),
                SystemAccounts.CLEARING, amount, id.value(), now);
        return new RequestedTransfer(transfer, hold);
    }

    /** Recria uma transferência já persistida. */
    public static Transfer restore(TransferId id, AccountId payerId, AccountId payeeId, Money amount,
            TransferStatus status, String failureReason, Instant requestedAt, Instant updatedAt) {
        return new Transfer(id, payerId, payeeId, amount, status, failureReason, requestedAt, updatedAt);
    }

    public JournalTransaction settle(Instant now) {
        ensurePending();
        status = TransferStatus.SETTLED;
        updatedAt = now;
        return JournalTransaction.move(JournalKind.TRANSFER_SETTLEMENT, SystemAccounts.CLEARING, payeeId, amount,
                id.value(), now);
    }

    public JournalTransaction fail(String reason, Instant now) {
        if (reason == null || reason.trim().isEmpty()) {
            throw new DomainException(ErrorCode.INVALID_FAILURE_REASON, "Informe o motivo da falha");
        }
        ensurePending();
        status = TransferStatus.FAILED;
        failureReason = reason.trim();
        updatedAt = now;
        return JournalTransaction.move(JournalKind.TRANSFER_REFUND, SystemAccounts.CLEARING, payerId, amount,
                id.value(), now);
    }

    private void ensurePending() {
        if (status != TransferStatus.PENDING) {
            throw new DomainException(ErrorCode.INVALID_TRANSFER_STATE, "A transferência já foi concluída");
        }
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
