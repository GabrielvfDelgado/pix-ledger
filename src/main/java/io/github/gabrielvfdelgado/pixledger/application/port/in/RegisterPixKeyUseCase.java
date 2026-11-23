package io.github.gabrielvfdelgado.pixledger.application.port.in;

import io.github.gabrielvfdelgado.pixledger.domain.account.AccountId;
import io.github.gabrielvfdelgado.pixledger.domain.account.PixKey;

public interface RegisterPixKeyUseCase {

    AccountView register(AccountId accountId, PixKey key);
}
