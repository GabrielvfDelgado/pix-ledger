package io.github.gabrielvfdelgado.pixledger.application.service;

import io.github.gabrielvfdelgado.pixledger.application.port.in.ConcludeTransferUseCase;
import io.github.gabrielvfdelgado.pixledger.application.port.in.GetTransferUseCase;
import io.github.gabrielvfdelgado.pixledger.application.port.in.ProcessTransferUseCase;
import io.github.gabrielvfdelgado.pixledger.application.port.in.TransferView;
import io.github.gabrielvfdelgado.pixledger.application.port.out.BankDecision;
import io.github.gabrielvfdelgado.pixledger.application.port.out.ExternalBank;
import io.github.gabrielvfdelgado.pixledger.domain.shared.DomainException;
import io.github.gabrielvfdelgado.pixledger.domain.shared.ErrorCode;
import io.github.gabrielvfdelgado.pixledger.domain.transfer.TransferId;
import io.github.gabrielvfdelgado.pixledger.domain.transfer.TransferStatus;

/** A chamada ao banco externo acontece fora de transação, para não prender conexões nem travas. */
public final class TransferProcessor implements ProcessTransferUseCase {

    private static final String UNAVAILABLE_REASON = "Banco externo indisponível";

    private final GetTransferUseCase transfers;
    private final ConcludeTransferUseCase conclusions;
    private final ExternalBank bank;

    public TransferProcessor(GetTransferUseCase transfers, ConcludeTransferUseCase conclusions, ExternalBank bank) {
        this.transfers = transfers;
        this.conclusions = conclusions;
        this.bank = bank;
    }

    @Override
    public void process(TransferId transferId) {
        TransferView transfer = transfers.get(transferId);
        if (transfer.status() != TransferStatus.PENDING) {
            return;
        }
        BankDecision decision = bank.pay(transferId, transfer.amount());
        concludeOnce(() -> {
            if (decision.isApproved()) {
                conclusions.settle(transferId);
            } else {
                conclusions.fail(transferId, decision.reason());
            }
        });
    }

    @Override
    public void failAfterRetries(TransferId transferId) {
        concludeOnce(() -> conclusions.fail(transferId, UNAVAILABLE_REASON));
    }

    /** Outra entrega da mesma mensagem pode ter concluído antes, e isso não é um erro. */
    private static void concludeOnce(Runnable conclusion) {
        try {
            conclusion.run();
        } catch (DomainException e) {
            if (e.code() != ErrorCode.INVALID_TRANSFER_STATE) {
                throw e;
            }
        }
    }
}
