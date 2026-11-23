package io.github.gabrielvfdelgado.pixledger.domain.shared;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class MoneyTest {

    @Test
    void parsesDecimalWithoutFloatingPointErrors() {
        assertThat(Money.parse("0.10").plus(Money.parse("0.20"))).isEqualTo(Money.ofCents(30));
        assertThat(Money.parse("12.34").cents()).isEqualTo(1234);
    }

    @ParameterizedTest
    @ValueSource(strings = {"1.234", "abc", "", "1e30"})
    void rejectsInvalidAmounts(String amount) {
        assertThatThrownBy(() -> Money.parse(amount))
                .isInstanceOfSatisfying(DomainException.class,
                        e -> assertThat(e.code()).isEqualTo(ErrorCode.INVALID_AMOUNT));
    }

    @Test
    void rejectsOverflowInsteadOfWrappingAround() {
        assertThatThrownBy(() -> Money.ofCents(Long.MAX_VALUE).plus(Money.ofCents(1)))
                .isInstanceOfSatisfying(DomainException.class,
                        e -> assertThat(e.code()).isEqualTo(ErrorCode.AMOUNT_OUT_OF_RANGE));
        assertThatThrownBy(() -> Money.ofCents(Long.MIN_VALUE).minus(Money.ofCents(1)))
                .isInstanceOf(DomainException.class);
    }

    @Test
    void formatsWithTwoDecimals() {
        assertThat(Money.ofCents(5).toString()).isEqualTo("0.05");
        assertThat(Money.ofCents(-1250).toString()).isEqualTo("-12.50");
    }
}
