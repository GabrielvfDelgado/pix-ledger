package io.github.gabrielvfdelgado.pixledger.application.port.in;

import io.github.gabrielvfdelgado.pixledger.domain.transfer.TransferId;

public interface ConcludeTransferUseCase {

    TransferView settle(TransferId transferId);

    TransferView fail(TransferId transferId, String reason);
}
