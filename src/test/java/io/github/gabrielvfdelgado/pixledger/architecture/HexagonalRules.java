package io.github.gabrielvfdelgado.pixledger.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.lang.ArchRule;

/** Regras da arquitetura hexagonal, parametrizadas para também serem testadas com exemplos inválidos. */
final class HexagonalRules {

    private HexagonalRules() {
    }

    static ArchRule domainIsPure(String base) {
        return classes().that().resideInAPackage(base + ".domain..")
                .should().onlyDependOnClassesThat()
                .resideInAnyPackage("java..", base + ".domain..")
                .as("o domínio só pode depender do Java e dele mesmo")
                .allowEmptyShould(true);
    }

    static ArchRule applicationDependsOnlyOnDomain(String base) {
        return classes().that().resideInAPackage(base + ".application..")
                .should().onlyDependOnClassesThat()
                .resideInAnyPackage("java..", base + ".domain..", base + ".application..")
                .as("a aplicação só pode depender do Java, do domínio e dela mesma")
                .allowEmptyShould(true);
    }

    static ArchRule inboundAdaptersDoNotUseOutboundAdapters(String base) {
        return noClasses().that().resideInAPackage(base + ".adapters.in..")
                .should().dependOnClassesThat().resideInAPackage(base + ".adapters.out..")
                .as("adaptadores de entrada não podem usar adaptadores de saída")
                .allowEmptyShould(true);
    }

    static ArchRule outboundAdaptersDoNotUseInboundAdapters(String base) {
        return noClasses().that().resideInAPackage(base + ".adapters.out..")
                .should().dependOnClassesThat().resideInAPackage(base + ".adapters.in..")
                .as("adaptadores de saída não podem usar adaptadores de entrada")
                .allowEmptyShould(true);
    }
}
