package io.github.gabrielvfdelgado.pixledger.adapters.in.rest;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.github.gabrielvfdelgado.pixledger.domain.account.PixKeyType;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;

/** Corpos das requisições. Valores monetários são texto para não passarem por ponto flutuante. */
final class Requests {

    private Requests() {
    }

    static final class OpenAccount {

        @NotBlank(message = "Informe o nome do titular")
        @Schema(example = "Ana Souza")
        public final String holderName;

        @JsonCreator
        OpenAccount(@JsonProperty("holderName") String holderName) {
            this.holderName = holderName;
        }
    }

    static final class RegisterPixKey {

        @NotNull(message = "Informe o tipo da chave")
        public final PixKeyType type;

        @Schema(description = "Opcional quando o tipo é RANDOM", example = "ana@example.com")
        public final String value;

        @JsonCreator
        RegisterPixKey(@JsonProperty("type") PixKeyType type, @JsonProperty("value") String value) {
            this.type = type;
            this.value = value;
        }
    }

    static final class Deposit {

        @NotBlank(message = "Informe o valor")
        @Schema(example = "100.00")
        public final String amount;

        @JsonCreator
        Deposit(@JsonProperty("amount") String amount) {
            this.amount = amount;
        }
    }

    static final class CreateTransfer {

        @NotNull(message = "Informe a conta pagadora")
        public final UUID payerAccountId;

        @NotNull(message = "Informe o tipo da chave")
        public final PixKeyType pixKeyType;

        @NotBlank(message = "Informe a chave Pix")
        @Schema(example = "bia@example.com")
        public final String pixKey;

        @NotBlank(message = "Informe o valor")
        @Schema(example = "30.00")
        public final String amount;

        @JsonCreator
        CreateTransfer(@JsonProperty("payerAccountId") UUID payerAccountId,
                @JsonProperty("pixKeyType") PixKeyType pixKeyType, @JsonProperty("pixKey") String pixKey,
                @JsonProperty("amount") String amount) {
            this.payerAccountId = payerAccountId;
            this.pixKeyType = pixKeyType;
            this.pixKey = pixKey;
            this.amount = amount;
        }
    }
}
