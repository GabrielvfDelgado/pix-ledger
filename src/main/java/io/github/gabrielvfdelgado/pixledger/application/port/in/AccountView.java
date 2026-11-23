package io.github.gabrielvfdelgado.pixledger.application.port.in;

import io.github.gabrielvfdelgado.pixledger.domain.account.Account;
import io.github.gabrielvfdelgado.pixledger.domain.account.AccountId;
import io.github.gabrielvfdelgado.pixledger.domain.account.AccountStatus;
import io.github.gabrielvfdelgado.pixledger.domain.account.PixKey;
import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/** Retrato imutável de uma conta para quem está fora do hexágono. */
public final class AccountView {

    private final AccountId id;
    private final String holderName;
    private final AccountStatus status;
    private final Set<PixKey> pixKeys;
    private final Instant createdAt;

    private AccountView(Account account) {
        this.id = account.id();
        this.holderName = account.holderName();
        this.status = account.status();
        this.pixKeys = Collections.unmodifiableSet(new LinkedHashSet<>(account.pixKeys()));
        this.createdAt = account.createdAt();
    }

    public static AccountView from(Account account) {
        return new AccountView(account);
    }

    public AccountId id() {
        return id;
    }

    public String holderName() {
        return holderName;
    }

    public AccountStatus status() {
        return status;
    }

    public Set<PixKey> pixKeys() {
        return pixKeys;
    }

    public Instant createdAt() {
        return createdAt;
    }
}
