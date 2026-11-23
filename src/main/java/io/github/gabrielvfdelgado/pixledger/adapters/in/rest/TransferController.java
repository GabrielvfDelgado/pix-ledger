package io.github.gabrielvfdelgado.pixledger.adapters.in.rest;

import io.github.gabrielvfdelgado.pixledger.application.port.in.GetTransferUseCase;
import io.github.gabrielvfdelgado.pixledger.application.port.in.IdempotencyKey;
import io.github.gabrielvfdelgado.pixledger.application.port.in.IdempotentResult;
import io.github.gabrielvfdelgado.pixledger.application.port.in.RequestTransferUseCase;
import io.github.gabrielvfdelgado.pixledger.application.port.in.TransferView;
import io.github.gabrielvfdelgado.pixledger.domain.account.AccountId;
import io.github.gabrielvfdelgado.pixledger.domain.account.PixKey;
import io.github.gabrielvfdelgado.pixledger.domain.shared.Money;
import io.github.gabrielvfdelgado.pixledger.domain.transfer.TransferId;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.net.URI;
import java.util.UUID;
import javax.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@RestController
@RequestMapping("/api/v1/transfers")
@Tag(name = "Transferências")
class TransferController {

    private final RequestTransferUseCase requestTransfer;
    private final GetTransferUseCase getTransfer;

    TransferController(RequestTransferUseCase requestTransfer, GetTransferUseCase getTransfer) {
        this.requestTransfer = requestTransfer;
        this.getTransfer = getTransfer;
    }

    @PostMapping
    @Operation(summary = "Pede uma transferência por chave Pix",
            description = "Reserva o valor e processa de forma assíncrona. No banco simulado, centavos ,13 recusam, "
                    + ",98 falham duas vezes antes de aprovar e ,99 ficam indisponíveis até o estorno.")
    ResponseEntity<Responses.TransferResponse> create(@RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody Requests.CreateTransfer request) {
        IdempotentResult<TransferView> result = requestTransfer.request(IdempotencyKey.of(idempotencyKey),
                AccountId.of(request.payerAccountId), PixKey.of(request.pixKeyType, request.pixKey),
                Money.parse(request.amount));
        URI location = ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}")
                .buildAndExpand(result.value().id().value()).toUri();
        return ResponseEntity.accepted().location(location)
                .header("Idempotent-Replayed", String.valueOf(result.replayed()))
                .body(new Responses.TransferResponse(result.value()));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Consulta uma transferência")
    Responses.TransferResponse get(@PathVariable UUID id) {
        return new Responses.TransferResponse(getTransfer.get(TransferId.of(id)));
    }
}
