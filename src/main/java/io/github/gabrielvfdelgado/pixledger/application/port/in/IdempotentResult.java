package io.github.gabrielvfdelgado.pixledger.application.port.in;

/** Resultado de uma operação idempotente, indicando se ela foi só repetida. */
public final class IdempotentResult<T> {

    private final T value;
    private final boolean replayed;

    private IdempotentResult(T value, boolean replayed) {
        this.value = value;
        this.replayed = replayed;
    }

    public static <T> IdempotentResult<T> created(T value) {
        return new IdempotentResult<>(value, false);
    }

    public static <T> IdempotentResult<T> replayed(T value) {
        return new IdempotentResult<>(value, true);
    }

    public T value() {
        return value;
    }

    public boolean replayed() {
        return replayed;
    }
}
