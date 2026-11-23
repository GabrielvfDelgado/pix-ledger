package io.github.gabrielvfdelgado.pixledger.application.port.in;

import io.github.gabrielvfdelgado.pixledger.domain.account.AccountId;

public interface GetAccountUseCase {

    AccountView get(AccountId accountId);
}
