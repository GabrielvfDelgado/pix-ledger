package io.github.gabrielvfdelgado.pixledger.domain.account;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.gabrielvfdelgado.pixledger.domain.shared.DomainException;
import io.github.gabrielvfdelgado.pixledger.domain.shared.ErrorCode;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class AccountTest {

    private static final Instant NOW = Instant.parse("2026-01-01T12:00:00Z");

    private final Account account = Account.open(AccountId.generate(), "  Ana Souza ", NOW);

    @Test
    void opensActiveAccountWithTrimmedName() {
        assertThat(account.status()).isEqualTo(AccountStatus.ACTIVE);
        assertThat(account.holderName()).isEqualTo("Ana Souza");
    }

    @Test
    void rejectsInvalidHolder() {
        assertCode(() -> Account.open(AccountId.generate(), " a ", NOW), ErrorCode.INVALID_HOLDER);
    }

    @Test
    void rejectsDuplicatedPixKey() {
        PixKey key = PixKey.random();
        account.addPixKey(key);

        assertCode(() -> account.addPixKey(key), ErrorCode.PIX_KEY_ALREADY_REGISTERED);
    }

    @Test
    void limitsPixKeys() {
        for (int i = 0; i < Account.MAX_PIX_KEYS; i++) {
            account.addPixKey(PixKey.random());
        }

        assertCode(() -> account.addPixKey(PixKey.random()), ErrorCode.PIX_KEY_LIMIT_REACHED);
    }

    @Test
    void removesOnlyExistingPixKey() {
        PixKey key = PixKey.random();
        account.addPixKey(key);
        account.removePixKey(key);

        assertThat(account.pixKeys()).isEmpty();
        assertCode(() -> account.removePixKey(key), ErrorCode.PIX_KEY_NOT_FOUND);
    }

    @Test
    void blockedAccountCannotAddKeysUntilActivated() {
        account.block();
        assertCode(() -> account.addPixKey(PixKey.random()), ErrorCode.ACCOUNT_BLOCKED);

        account.activate();
        account.addPixKey(PixKey.random());

        assertThat(account.pixKeys()).hasSize(1);
    }

    @Test
    void pixKeysCannotBeChangedFromOutside() {
        assertThatThrownBy(() -> account.pixKeys().add(PixKey.random()))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    private static void assertCode(org.assertj.core.api.ThrowableAssert.ThrowingCallable action, ErrorCode code) {
        assertThatThrownBy(action)
                .isInstanceOfSatisfying(DomainException.class, e -> assertThat(e.code()).isEqualTo(code));
    }
}
