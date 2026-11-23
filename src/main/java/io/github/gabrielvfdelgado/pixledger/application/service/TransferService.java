package io.github.gabrielvfdelgado.pixledger.application.service;

import io.github.gabrielvfdelgado.pixledger.application.port.in.ConcludeTransferUseCase;
import io.github.gabrielvfdelgado.pixledger.application.port.in.GetTransferUseCase;
import io.github.gabrielvfdelgado.pixledger.application.port.in.IdempotencyKey;
import io.github.gabrielvfdelgado.pixledger.application.port.in.IdempotentResult;
import io.github.gabrielvfdelgado.pixledger.application.port.in.RequestTransferUseCase;
import io.github.gabrielvfdelgado.pixledger.application.port.in.TransferView;
import io.github.gabrielvfdelgado.pixledger.application.port.out.AccountRepository;
import io.github.gabrielvfdelgado.pixledger.application.port.out.LedgerRepository;
import io.github.gabrielvfdelgado.pixledger.application.port.out.OutboxEvent;
import io.github.gabrielvfdelgado.pixledger.application.port.out.OutboxRepository;
import io.github.gabrielvfdelgado.pixledger.application.port.out.TransactionRunner;
import io.github.gabrielvfdelgado.pixledger.application.port.out.TransferEventType;
import io.github.gabrielvfdelgado.pixledger.application.port.out.TransferRepository;
import io.github.gabrielvfdelgado.pixledger.domain.account.Account;
import io.github.gabrielvfdelgado.pixledger.domain.account.AccountId;
import io.github.gabrielvfdelgado.pixledger.domain.account.PixKey;
import io.github.gabrielvfdelgado.pixledger.domain.ledger.JournalTransaction;
import io.github.gabrielvfdelgado.pixledger.domain.shared.DomainException;
import io.github.gabrielvfdelgado.pixledger.domain.shared.ErrorCode;
import io.github.gabrielvfdelgado.pixledger.domain.shared.Money;
import io.github.gabrielvfdelgado.pixledger.domain.transfer.RequestedTransfer;
import io.github.gabrielvfdelgado.pixledger.domain.transfer.Transfer;
import io.github.gabrielvfdelgado.pixledger.domain.transfer.TransferId;
import java.time.Clock;
import java.util.Arrays;
import java.util.function.Function;

public final class TransferService implements RequestTransferUseCase, ConcludeTransferUseCase, GetTransferUseCase {

    private final AccountRepository accounts;
    private final TransferRepository transfers;
    private final LedgerRepository ledger;
    private final OutboxRepository outbox;
    private final TransactionRunner transactions;
    private final IdempotentExecutor idempotent;
    private final Clock clock;

    public TransferService(AccountRepository accounts, TransferRepository transfers, LedgerRepository ledger,
            OutboxRepository outbox, TransactionRunner transactions, IdempotentExecutor idempotent, Clock clock) {
        this.accounts = accounts;
        this.transfers = transfers;
        this.ledger = ledger;
        this.outbox = outbox;
        this.transactions = transactions;
        this.idempotent = idempotent;
        this.clock = clock;
    }

    @Override
    public IdempotentResult<TransferView> request(IdempotencyKey key, AccountId payerId, PixKey payeeKey,
            Money amount) {
        String fingerprint = Fingerprint.of(payerId, payeeKey.type(), payeeKey.value(), amount.cents());
        return idempotent.execute("transfer:" + payerId, key, fingerprint,
                id -> create(TransferId.of(id), payerId, payeeKey, amount),
                id -> TransferView.from(requireTransfer(TransferId.of(id))));
    }

    private TransferView create(TransferId id, AccountId payerId, PixKey payeeKey, Money amount) {
        AccountId payeeId = accounts.findByPixKey(payeeKey)
                .orElseThrow(() -> new DomainException(ErrorCode.PIX_KEY_NOT_FOUND, "Chave Pix não encontrada"))
                .id();
        accounts.lockForUpdate(Arrays.asList(payerId, payeeId));
        Account payer = requireAccount(payerId);
        Account payee = requireAccount(payeeId);
        RequestedTransfer requested = Transfer.request(id, payer, payee, amount, ledger.balanceOf(payerId),
                clock.instant());
        transfers.save(requested.transfer());
        ledger.append(requested.hold());
        outbox.add(OutboxEvent.of(TransferEventType.TRANSFER_REQUESTED, id, clock.instant()));
        return TransferView.from(requested.transfer());
    }

    @Override
    public TransferView settle(TransferId transferId) {
        return conclude(transferId, TransferEventType.TRANSFER_SETTLED,
                transfer -> transfer.settle(clock.instant()));
    }

    @Override
    public TransferView fail(TransferId transferId, String reason) {
        return conclude(transferId, TransferEventType.TRANSFER_FAILED,
                transfer -> transfer.fail(reason, clock.instant()));
    }

    @Override
    public TransferView get(TransferId transferId) {
        return transactions.inTransaction(() -> TransferView.from(requireTransfer(transferId)));
    }

    private TransferView conclude(TransferId transferId, TransferEventType eventType,
            Function<Transfer, JournalTransaction> transition) {
        return transactions.inTransaction(() -> {
            Transfer transfer = transfers.findByIdForUpdate(transferId).orElseThrow(TransferService::transferNotFound);
            JournalTransaction journal = transition.apply(transfer);
            transfers.save(transfer);
            ledger.append(journal);
            outbox.add(OutboxEvent.of(eventType, transferId, clock.instant()));
            return TransferView.from(transfer);
        });
    }

    private Account requireAccount(AccountId accountId) {
        return accounts.findById(accountId)
                .orElseThrow(() -> new DomainException(ErrorCode.ACCOUNT_NOT_FOUND, "Conta não encontrada"));
    }

    private Transfer requireTransfer(TransferId transferId) {
        return transfers.findById(transferId).orElseThrow(TransferService::transferNotFound);
    }

    private static DomainException transferNotFound() {
        return new DomainException(ErrorCode.TRANSFER_NOT_FOUND, "Transferência não encontrada");
    }
}
