package io.github.gabrielvfdelgado.pixledger.application.port.in;

import io.github.gabrielvfdelgado.pixledger.domain.account.AccountId;
import io.github.gabrielvfdelgado.pixledger.domain.shared.Money;

public interface DepositUseCase {

    /** Retorna o saldo atual da conta. Repetir a mesma chave não deposita de novo. */
    IdempotentResult<Money> deposit(IdempotencyKey key, AccountId accountId, Money amount);
}
