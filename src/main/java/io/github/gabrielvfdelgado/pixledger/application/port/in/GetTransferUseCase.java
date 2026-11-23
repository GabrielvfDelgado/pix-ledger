package io.github.gabrielvfdelgado.pixledger.application.port.in;

import io.github.gabrielvfdelgado.pixledger.domain.transfer.TransferId;

public interface GetTransferUseCase {

    TransferView get(TransferId transferId);
}
