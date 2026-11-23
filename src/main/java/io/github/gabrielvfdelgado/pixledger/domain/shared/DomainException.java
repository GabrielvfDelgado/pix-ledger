package io.github.gabrielvfdelgado.pixledger.domain.shared;

import java.util.Objects;

/** Violação de uma regra de negócio. */
public class DomainException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final ErrorCode code;

    public DomainException(ErrorCode code, String message) {
        super(message);
        this.code = Objects.requireNonNull(code, "code");
    }

    public ErrorCode code() {
        return code;
    }
}
