package io.github.gabrielvfdelgado.pixledger.domain.account;

import io.github.gabrielvfdelgado.pixledger.domain.shared.Id;
import java.util.UUID;

public final class AccountId extends Id {

    private AccountId(UUID value) {
        super(value);
    }

    public static AccountId of(UUID value) {
        return new AccountId(value);
    }

    public static AccountId generate() {
        return new AccountId(UUID.randomUUID());
    }
}
