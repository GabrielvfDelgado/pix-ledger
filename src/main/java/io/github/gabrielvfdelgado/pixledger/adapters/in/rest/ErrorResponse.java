package io.github.gabrielvfdelgado.pixledger.adapters.in.rest;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.util.Map;

/** Formato único de erro da API. */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
final class ErrorResponse {

    public final String code;
    public final String message;
    public final int status;
    public final Instant timestamp;
    public final Map<String, String> fields;

    ErrorResponse(String code, String message, int status, Map<String, String> fields) {
        this.code = code;
        this.message = message;
        this.status = status;
        this.timestamp = Instant.now();
        this.fields = fields;
    }
}
