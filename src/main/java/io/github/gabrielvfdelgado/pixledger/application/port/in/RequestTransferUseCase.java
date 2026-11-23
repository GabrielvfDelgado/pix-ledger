package io.github.gabrielvfdelgado.pixledger.application.port.in;

import io.github.gabrielvfdelgado.pixledger.domain.account.AccountId;
import io.github.gabrielvfdelgado.pixledger.domain.account.PixKey;
import io.github.gabrielvfdelgado.pixledger.domain.shared.Money;

public interface RequestTransferUseCase {

    /** Repetir a mesma chave devolve a transferência original, sem cobrar de novo. */
    IdempotentResult<TransferView> request(IdempotencyKey key, AccountId payerId, PixKey payeeKey, Money amount);
}
