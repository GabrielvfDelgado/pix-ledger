package io.github.gabrielvfdelgado.pixledger.adapters.in.rest;

import io.github.gabrielvfdelgado.pixledger.domain.shared.DomainException;
import io.github.gabrielvfdelgado.pixledger.domain.shared.ErrorCode;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

@RestControllerAdvice
class ApiExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger LOG = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(DomainException.class)
    ResponseEntity<Object> handleDomain(DomainException exception) {
        HttpStatus status = statusOf(exception.code());
        return respond(status, exception.code().name(), exception.getMessage(), Collections.emptyMap());
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    ResponseEntity<Object> handleConflict(DataIntegrityViolationException exception) {
        LOG.warn("Conflito de integridade: {}", exception.getMostSpecificCause().getClass().getSimpleName());
        return respond(HttpStatus.CONFLICT, "CONFLICT", "A operação conflita com o estado atual",
                Collections.emptyMap());
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<Object> handleUnexpected(Exception exception) {
        LOG.error("Erro inesperado", exception);
        return respond(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "Erro interno", Collections.emptyMap());
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException exception,
            HttpHeaders headers, HttpStatus status, WebRequest request) {
        Map<String, String> fields = new LinkedHashMap<>();
        for (FieldError error : exception.getBindingResult().getFieldErrors()) {
            fields.putIfAbsent(error.getField(), error.getDefaultMessage());
        }
        return respond(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Dados inválidos", fields);
    }

    @Override
    protected ResponseEntity<Object> handleExceptionInternal(Exception exception, Object body, HttpHeaders headers,
            HttpStatus status, WebRequest request) {
        String code = status.is4xxClientError() ? "INVALID_REQUEST" : "INTERNAL_ERROR";
        String message = status.is4xxClientError() ? "Requisição inválida" : "Erro interno";
        ErrorResponse error = new ErrorResponse(code, message, status.value(), Collections.emptyMap());
        return new ResponseEntity<>(error, headers, status);
    }

    private static ResponseEntity<Object> respond(HttpStatus status, String code, String message,
            Map<String, String> fields) {
        return ResponseEntity.status(status).body(new ErrorResponse(code, message, status.value(), fields));
    }

    private static HttpStatus statusOf(ErrorCode code) {
        switch (code) {
            case ACCOUNT_NOT_FOUND:
            case TRANSFER_NOT_FOUND:
            case PIX_KEY_NOT_FOUND:
                return HttpStatus.NOT_FOUND;
            case PIX_KEY_ALREADY_REGISTERED:
            case INVALID_TRANSFER_STATE:
                return HttpStatus.CONFLICT;
            case INSUFFICIENT_FUNDS:
            case ACCOUNT_BLOCKED:
            case SAME_ACCOUNT:
            case PIX_KEY_LIMIT_REACHED:
            case IDEMPOTENCY_KEY_REUSED:
                return HttpStatus.UNPROCESSABLE_ENTITY;
            default:
                return HttpStatus.BAD_REQUEST;
        }
    }
}
