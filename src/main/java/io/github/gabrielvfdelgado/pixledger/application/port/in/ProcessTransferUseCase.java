package io.github.gabrielvfdelgado.pixledger.application.port.in;

import io.github.gabrielvfdelgado.pixledger.domain.transfer.TransferId;

public interface ProcessTransferUseCase {

    /** Pode ser chamado mais de uma vez: transferências já concluídas são ignoradas. */
    void process(TransferId transferId);

    /** Encerra a transferência e devolve o valor quando as tentativas se esgotam. */
    void failAfterRetries(TransferId transferId);
}
