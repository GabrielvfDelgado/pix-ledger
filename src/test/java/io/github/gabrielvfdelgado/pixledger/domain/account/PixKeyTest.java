package io.github.gabrielvfdelgado.pixledger.domain.account;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.gabrielvfdelgado.pixledger.domain.shared.DomainException;
import io.github.gabrielvfdelgado.pixledger.domain.shared.ErrorCode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class PixKeyTest {

    @ParameterizedTest
    @CsvSource({
        "CPF, 111.444.777-35, 11144477735",
        "EMAIL, ' Ana@Example.COM ', ana@example.com",
        "PHONE, +55 (21) 90000-0000, +5521900000000",
        "RANDOM, 123E4567-E89B-12D3-A456-426614174000, 123e4567-e89b-12d3-a456-426614174000",
    })
    void normalizesValidKeys(PixKeyType type, String raw, String expected) {
        assertThat(PixKey.of(type, raw).value()).isEqualTo(expected);
    }

    @ParameterizedTest
    @CsvSource({
        "CPF, 11111111111",
        "CPF, 11144477736",
        "CPF, 1114447773",
        "EMAIL, ana@",
        "EMAIL, ana@example",
        "PHONE, 21900000000",
        "PHONE, +5501900000000",
        "RANDOM, not-a-uuid",
    })
    void rejectsInvalidKeys(PixKeyType type, String raw) {
        assertThatThrownBy(() -> PixKey.of(type, raw))
                .isInstanceOfSatisfying(DomainException.class,
                        e -> assertThat(e.code()).isEqualTo(ErrorCode.INVALID_PIX_KEY));
    }

    @Test
    void doesNotExposeValueInToString() {
        assertThat(PixKey.of(PixKeyType.CPF, "11144477735").toString())
                .isEqualTo("PixKey[CPF]")
                .doesNotContain("11144477735");
    }

    @Test
    void generatesValidRandomKey() {
        PixKey key = PixKey.random();

        assertThat(PixKey.of(PixKeyType.RANDOM, key.value())).isEqualTo(key);
    }
}
