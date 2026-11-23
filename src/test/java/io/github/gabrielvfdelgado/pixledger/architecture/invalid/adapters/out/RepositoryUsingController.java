package io.github.gabrielvfdelgado.pixledger.architecture.invalid.adapters.out;

import io.github.gabrielvfdelgado.pixledger.architecture.invalid.adapters.in.ControllerUsingRepository;

/** Exemplo inválido: adaptador de saída usando um adaptador de entrada. */
public class RepositoryUsingController {

    public Object load(ControllerUsingRepository controller) {
        return controller.toString();
    }
}
