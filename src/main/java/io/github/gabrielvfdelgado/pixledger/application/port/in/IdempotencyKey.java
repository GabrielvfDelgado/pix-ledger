package io.github.gabrielvfdelgado.pixledger.application.port.in;

import io.github.gabrielvfdelgado.pixledger.domain.shared.DomainException;
import io.github.gabrielvfdelgado.pixledger.domain.shared.ErrorCode;
import java.util.regex.Pattern;

/** Identifica uma intenção do cliente: repetir a mesma chave não repete o efeito. */
public final class IdempotencyKey {

    private static final Pattern VALID = Pattern.compile("^[A-Za-z0-9._:-]{1,128}$");

    private final String value;

    private IdempotencyKey(String value) {
        this.value = value;
    }

    public static IdempotencyKey of(String value) {
        if (value == null || !VALID.matcher(value).matches()) {
            throw new DomainException(ErrorCode.INVALID_IDEMPOTENCY_KEY,
                    "A chave de idempotência deve ter até 128 letras, números ou . _ : -");
        }
        return new IdempotencyKey(value);
    }

    public String value() {
        return value;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof IdempotencyKey && value.equals(((IdempotencyKey) other).value);
    }

    @Override
    public int hashCode() {
        return value.hashCode();
    }

    @Override
    public String toString() {
        return value;
    }
}
