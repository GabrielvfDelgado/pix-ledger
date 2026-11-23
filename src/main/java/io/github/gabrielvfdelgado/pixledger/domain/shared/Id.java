package io.github.gabrielvfdelgado.pixledger.domain.shared;

import java.util.Objects;
import java.util.UUID;

/** Base dos identificadores tipados, para um id de conta nunca ser confundido com outro. */
public abstract class Id {

    private final UUID value;

    protected Id(UUID value) {
        this.value = Objects.requireNonNull(value, "value");
    }

    public UUID value() {
        return value;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        return other != null && getClass() == other.getClass() && value.equals(((Id) other).value);
    }

    @Override
    public int hashCode() {
        return Objects.hash(getClass(), value);
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
