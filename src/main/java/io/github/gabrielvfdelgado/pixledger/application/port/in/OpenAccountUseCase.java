package io.github.gabrielvfdelgado.pixledger.application.port.in;

public interface OpenAccountUseCase {

    AccountView open(String holderName);
}
