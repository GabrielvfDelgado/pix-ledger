package io.github.gabrielvfdelgado.pixledger.application.port.out;

import io.github.gabrielvfdelgado.pixledger.domain.account.Account;
import io.github.gabrielvfdelgado.pixledger.domain.account.AccountId;
import io.github.gabrielvfdelgado.pixledger.domain.account.PixKey;
import java.util.Collection;
import java.util.Optional;

public interface AccountRepository {

    void save(Account account);

    Optional<Account> findById(AccountId id);

    Optional<Account> findByPixKey(PixKey key);

    /**
     * Trava as contas até o fim da transação, sempre na mesma ordem para evitar deadlock.
     * Contas inexistentes são ignoradas.
     */
    void lockForUpdate(Collection<AccountId> ids);
}
