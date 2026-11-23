package io.github.gabrielvfdelgado.pixledger.domain.account;

import io.github.gabrielvfdelgado.pixledger.domain.shared.DomainException;
import io.github.gabrielvfdelgado.pixledger.domain.shared.ErrorCode;
import java.time.Instant;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

/** Conta do titular. O saldo não fica aqui: ele sempre é calculado a partir do livro-razão. */
public final class Account {

    public static final int MAX_PIX_KEYS = 5;

    private static final int MIN_HOLDER_LENGTH = 3;
    private static final int MAX_HOLDER_LENGTH = 120;

    private final AccountId id;
    private final String holderName;
    private final Instant createdAt;
    private final Set<PixKey> pixKeys;
    private AccountStatus status;

    private Account(AccountId id, String holderName, AccountStatus status, Collection<PixKey> pixKeys,
            Instant createdAt) {
        this.id = Objects.requireNonNull(id, "id");
        this.holderName = validHolder(holderName);
        this.status = Objects.requireNonNull(status, "status");
        this.pixKeys = new LinkedHashSet<>(pixKeys);
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
    }

    public static Account open(AccountId id, String holderName, Instant now) {
        return new Account(id, holderName, AccountStatus.ACTIVE, Collections.<PixKey>emptyList(), now);
    }

    /** Recria uma conta já persistida. */
    public static Account restore(AccountId id, String holderName, AccountStatus status, Collection<PixKey> pixKeys,
            Instant createdAt) {
        return new Account(id, holderName, status, pixKeys, createdAt);
    }

    /** A unicidade da chave entre contas é garantida pelo caso de uso e pelo banco. */
    public void addPixKey(PixKey key) {
        Objects.requireNonNull(key, "key");
        ensureActive();
        if (pixKeys.contains(key)) {
            throw new DomainException(ErrorCode.PIX_KEY_ALREADY_REGISTERED, "Chave Pix já cadastrada");
        }
        if (pixKeys.size() >= MAX_PIX_KEYS) {
            throw new DomainException(ErrorCode.PIX_KEY_LIMIT_REACHED, "Limite de chaves Pix atingido");
        }
        pixKeys.add(key);
    }

    public void removePixKey(PixKey key) {
        if (!pixKeys.remove(key)) {
            throw new DomainException(ErrorCode.PIX_KEY_NOT_FOUND, "Chave Pix não encontrada");
        }
    }

    public void block() {
        status = AccountStatus.BLOCKED;
    }

    public void activate() {
        status = AccountStatus.ACTIVE;
    }

    public void ensureActive() {
        if (status != AccountStatus.ACTIVE) {
            throw new DomainException(ErrorCode.ACCOUNT_BLOCKED, "Conta bloqueada");
        }
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
        return Collections.unmodifiableSet(pixKeys);
    }

    public Instant createdAt() {
        return createdAt;
    }

    private static String validHolder(String holderName) {
        String name = holderName == null ? "" : holderName.trim();
        if (name.length() < MIN_HOLDER_LENGTH || name.length() > MAX_HOLDER_LENGTH) {
            throw new DomainException(ErrorCode.INVALID_HOLDER,
                    "O nome do titular deve ter entre " + MIN_HOLDER_LENGTH + " e " + MAX_HOLDER_LENGTH + " caracteres");
        }
        return name;
    }
}
