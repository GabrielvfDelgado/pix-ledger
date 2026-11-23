package io.github.gabrielvfdelgado.pixledger.application.port.out;

import io.github.gabrielvfdelgado.pixledger.domain.transfer.Transfer;
import io.github.gabrielvfdelgado.pixledger.domain.transfer.TransferId;
import java.util.Optional;

public interface TransferRepository {

    void save(Transfer transfer);

    Optional<Transfer> findById(TransferId id);

    /** Busca a transferência e a mantém travada até o fim da transação. */
    Optional<Transfer> findByIdForUpdate(TransferId id);
}
