package io.github.gabrielvfdelgado.pixledger.architecture.invalid.adapters.in;

import io.github.gabrielvfdelgado.pixledger.architecture.invalid.adapters.out.RepositoryUsingController;

/** Exemplo inválido: adaptador de entrada usando um adaptador de saída. */
public class ControllerUsingRepository {

    public Object load(RepositoryUsingController repository) {
        return repository.toString();
    }
}
