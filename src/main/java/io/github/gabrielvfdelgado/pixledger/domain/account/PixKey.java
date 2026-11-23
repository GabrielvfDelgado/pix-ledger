package io.github.gabrielvfdelgado.pixledger.domain.account;

import io.github.gabrielvfdelgado.pixledger.domain.shared.DomainException;
import io.github.gabrielvfdelgado.pixledger.domain.shared.ErrorCode;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;

/** Chave Pix normalizada. O toString não mostra o valor, para a chave não vazar em logs. */
public final class PixKey {

    private static final int MAX_EMAIL_LENGTH = 77;
    private static final Pattern EMAIL = Pattern.compile("^[a-z0-9._%+-]+@[a-z0-9-]+(\\.[a-z0-9-]+)*\\.[a-z]{2,}$");
    private static final Pattern PHONE = Pattern.compile("^\\+55[1-9][0-9]9?[0-9]{8}$");
    private static final Pattern RANDOM = Pattern.compile(
            "^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$");

    private final PixKeyType type;
    private final String value;

    private PixKey(PixKeyType type, String value) {
        this.type = type;
        this.value = value;
    }

    public static PixKey of(PixKeyType type, String rawValue) {
        if (type == null || rawValue == null) {
            throw invalid();
        }
        String normalized = normalize(type, rawValue);
        if (!isValid(type, normalized)) {
            throw invalid();
        }
        return new PixKey(type, normalized);
    }

    public static PixKey random() {
        return new PixKey(PixKeyType.RANDOM, UUID.randomUUID().toString());
    }

    public PixKeyType type() {
        return type;
    }

    public String value() {
        return value;
    }

    private static String normalize(PixKeyType type, String rawValue) {
        String trimmed = rawValue.trim();
        switch (type) {
            case CPF:
                return trimmed.replaceAll("[.-]", "");
            case PHONE:
                return trimmed.replaceAll("[\\s()-]", "");
            default:
                return trimmed.toLowerCase(Locale.ROOT);
        }
    }

    private static boolean isValid(PixKeyType type, String value) {
        switch (type) {
            case CPF:
                return isValidCpf(value);
            case EMAIL:
                return value.length() <= MAX_EMAIL_LENGTH && EMAIL.matcher(value).matches();
            case PHONE:
                return PHONE.matcher(value).matches();
            default:
                return RANDOM.matcher(value).matches();
        }
    }

    private static boolean isValidCpf(String cpf) {
        if (!cpf.matches("\\d{11}") || cpf.chars().distinct().count() == 1) {
            return false;
        }
        return checkDigit(cpf, 9) == cpf.charAt(9) - '0' && checkDigit(cpf, 10) == cpf.charAt(10) - '0';
    }

    private static int checkDigit(String cpf, int length) {
        int sum = 0;
        for (int i = 0; i < length; i++) {
            sum += (cpf.charAt(i) - '0') * (length + 1 - i);
        }
        int remainder = sum % 11;
        return remainder < 2 ? 0 : 11 - remainder;
    }

    private static DomainException invalid() {
        return new DomainException(ErrorCode.INVALID_PIX_KEY, "Chave Pix inválida");
    }

    @Override
    public boolean equals(Object other) {
        if (!(other instanceof PixKey)) {
            return false;
        }
        PixKey key = (PixKey) other;
        return type == key.type && value.equals(key.value);
    }

    @Override
    public int hashCode() {
        return Objects.hash(type, value);
    }

    @Override
    public String toString() {
        return "PixKey[" + type + "]";
    }
}
