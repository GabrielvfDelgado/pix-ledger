package io.github.gabrielvfdelgado.pixledger.adapters.in.rest;

import io.github.gabrielvfdelgado.pixledger.application.port.in.AccountView;
import io.github.gabrielvfdelgado.pixledger.application.port.in.DepositUseCase;
import io.github.gabrielvfdelgado.pixledger.application.port.in.GetAccountUseCase;
import io.github.gabrielvfdelgado.pixledger.application.port.in.GetBalanceUseCase;
import io.github.gabrielvfdelgado.pixledger.application.port.in.GetStatementUseCase;
import io.github.gabrielvfdelgado.pixledger.application.port.in.IdempotencyKey;
import io.github.gabrielvfdelgado.pixledger.application.port.in.IdempotentResult;
import io.github.gabrielvfdelgado.pixledger.application.port.in.OpenAccountUseCase;
import io.github.gabrielvfdelgado.pixledger.application.port.in.RegisterPixKeyUseCase;
import io.github.gabrielvfdelgado.pixledger.domain.account.AccountId;
import io.github.gabrielvfdelgado.pixledger.domain.account.PixKey;
import io.github.gabrielvfdelgado.pixledger.domain.account.PixKeyType;
import io.github.gabrielvfdelgado.pixledger.domain.shared.Money;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import javax.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@RestController
@RequestMapping("/api/v1/accounts")
@Tag(name = "Contas")
class AccountController {

    private final OpenAccountUseCase openAccount;
    private final GetAccountUseCase getAccount;
    private final RegisterPixKeyUseCase registerPixKey;
    private final DepositUseCase deposit;
    private final GetBalanceUseCase getBalance;
    private final GetStatementUseCase getStatement;

    AccountController(OpenAccountUseCase openAccount, GetAccountUseCase getAccount,
            RegisterPixKeyUseCase registerPixKey, DepositUseCase deposit, GetBalanceUseCase getBalance,
            GetStatementUseCase getStatement) {
        this.openAccount = openAccount;
        this.getAccount = getAccount;
        this.registerPixKey = registerPixKey;
        this.deposit = deposit;
        this.getBalance = getBalance;
        this.getStatement = getStatement;
    }

    @PostMapping
    @Operation(summary = "Abre uma conta")
    ResponseEntity<Responses.AccountResponse> open(@Valid @RequestBody Requests.OpenAccount request) {
        AccountView account = openAccount.open(request.holderName);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}")
                .buildAndExpand(account.id().value()).toUri();
        return ResponseEntity.created(location).body(new Responses.AccountResponse(account));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Consulta uma conta")
    Responses.AccountResponse get(@PathVariable UUID id) {
        return new Responses.AccountResponse(getAccount.get(AccountId.of(id)));
    }

    @PostMapping("/{id}/pix-keys")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Cadastra uma chave Pix")
    Responses.AccountResponse registerKey(@PathVariable UUID id, @Valid @RequestBody Requests.RegisterPixKey request) {
        PixKey key = request.type == PixKeyType.RANDOM && request.value == null
                ? PixKey.random()
                : PixKey.of(request.type, request.value);
        return new Responses.AccountResponse(registerPixKey.register(AccountId.of(id), key));
    }

    @PostMapping("/{id}/deposits")
    @Operation(summary = "Faz um depósito simulado. Repetir a Idempotency-Key não deposita de novo")
    ResponseEntity<Responses.BalanceResponse> deposit(@PathVariable UUID id,
            @RequestHeader("Idempotency-Key") String idempotencyKey, @Valid @RequestBody Requests.Deposit request) {
        AccountId accountId = AccountId.of(id);
        IdempotentResult<Money> result = deposit.deposit(IdempotencyKey.of(idempotencyKey), accountId,
                Money.parse(request.amount));
        return ResponseEntity.status(result.replayed() ? HttpStatus.OK : HttpStatus.CREATED)
                .header("Idempotent-Replayed", String.valueOf(result.replayed()))
                .body(new Responses.BalanceResponse(accountId, result.value()));
    }

    @GetMapping("/{id}/balance")
    @Operation(summary = "Consulta o saldo")
    Responses.BalanceResponse balance(@PathVariable UUID id) {
        AccountId accountId = AccountId.of(id);
        return new Responses.BalanceResponse(accountId, getBalance.balanceOf(accountId));
    }

    @GetMapping("/{id}/statement")
    @Operation(summary = "Consulta o extrato, com os lançamentos mais recentes primeiro")
    List<Responses.StatementLineResponse> statement(@PathVariable UUID id,
            @RequestParam(defaultValue = "20") int limit) {
        return getStatement.statementOf(AccountId.of(id), limit).stream()
                .map(Responses.StatementLineResponse::new)
                .collect(Collectors.toList());
    }
}
