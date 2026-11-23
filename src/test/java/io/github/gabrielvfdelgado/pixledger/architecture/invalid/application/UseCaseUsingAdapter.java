package io.github.gabrielvfdelgado.pixledger.architecture.invalid.application;

import io.github.gabrielvfdelgado.pixledger.architecture.invalid.adapters.out.RepositoryUsingController;

/** Exemplo inválido: caso de uso acoplado a um adaptador. */
public class UseCaseUsingAdapter {

    private final RepositoryUsingController repository = new RepositoryUsingController();

    public RepositoryUsingController repository() {
        return repository;
    }
}
