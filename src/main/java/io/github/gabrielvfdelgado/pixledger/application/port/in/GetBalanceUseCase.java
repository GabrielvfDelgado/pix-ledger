package io.github.gabrielvfdelgado.pixledger.application.port.in;

import io.github.gabrielvfdelgado.pixledger.domain.account.AccountId;
import io.github.gabrielvfdelgado.pixledger.domain.shared.Money;

public interface GetBalanceUseCase {

    Money balanceOf(AccountId accountId);
}
