package io.github.gabrielvfdelgado.pixledger.domain.shared;

import java.math.BigDecimal;

/** Valor em reais guardado em centavos. Pode ser negativo para representar saldos e efeitos contábeis. */
public final class Money implements Comparable<Money> {

    public static final Money ZERO = new Money(0);

    private static final int CENTS_SCALE = 2;

    private final long cents;

    private Money(long cents) {
        this.cents = cents;
    }

    public static Money ofCents(long cents) {
        return cents == 0 ? ZERO : new Money(cents);
    }

    /** Converte um valor como "12.34". Mais de duas casas decimais é recusado, sem arredondar. */
    public static Money parse(String amount) {
        if (amount == null) {
            throw new DomainException(ErrorCode.INVALID_AMOUNT, "Informe um valor");
        }
        try {
            return ofCents(new BigDecimal(amount.trim()).movePointRight(CENTS_SCALE).longValueExact());
        } catch (ArithmeticException | NumberFormatException e) {
            throw new DomainException(ErrorCode.INVALID_AMOUNT, "Valor inválido");
        }
    }

    public long cents() {
        return cents;
    }

    public Money plus(Money other) {
        try {
            return ofCents(Math.addExact(cents, other.cents));
        } catch (ArithmeticException e) {
            throw new DomainException(ErrorCode.AMOUNT_OUT_OF_RANGE, "Valor fora do limite");
        }
    }

    public Money minus(Money other) {
        try {
            return ofCents(Math.subtractExact(cents, other.cents));
        } catch (ArithmeticException e) {
            throw new DomainException(ErrorCode.AMOUNT_OUT_OF_RANGE, "Valor fora do limite");
        }
    }

    public boolean isPositive() {
        return cents > 0;
    }

    public boolean isNegative() {
        return cents < 0;
    }

    public boolean isLessThan(Money other) {
        return cents < other.cents;
    }

    @Override
    public int compareTo(Money other) {
        return Long.compare(cents, other.cents);
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof Money && cents == ((Money) other).cents;
    }

    @Override
    public int hashCode() {
        return Long.hashCode(cents);
    }

    @Override
    public String toString() {
        return BigDecimal.valueOf(cents, CENTS_SCALE).toPlainString();
    }
}
