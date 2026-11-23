package io.github.gabrielvfdelgado.pixledger.architecture;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.Test;

class HexagonalArchitectureTest {

    private static final String BASE = "io.github.gabrielvfdelgado.pixledger";
    private static final String INVALID = BASE + ".architecture.invalid";

    private static final JavaClasses PRODUCTION = new ClassFileImporter()
            .withImportOption(new ImportOption.DoNotIncludeTests())
            .importPackages(BASE);

    private static final JavaClasses INVALID_EXAMPLES = new ClassFileImporter().importPackages(INVALID);

    @Test
    void domainIsPure() {
        HexagonalRules.domainIsPure(BASE).check(PRODUCTION);
    }

    @Test
    void applicationDependsOnlyOnDomain() {
        HexagonalRules.applicationDependsOnlyOnDomain(BASE).check(PRODUCTION);
    }

    @Test
    void inboundAdaptersDoNotUseOutboundAdapters() {
        HexagonalRules.inboundAdaptersDoNotUseOutboundAdapters(BASE).check(PRODUCTION);
    }

    @Test
    void outboundAdaptersDoNotUseInboundAdapters() {
        HexagonalRules.outboundAdaptersDoNotUseInboundAdapters(BASE).check(PRODUCTION);
    }

    @Test
    void rejectsDomainThatDependsOnSpring() {
        assertThatThrownBy(() -> HexagonalRules.domainIsPure(INVALID).check(INVALID_EXAMPLES))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("SpringDomainService");
    }

    @Test
    void rejectsApplicationThatDependsOnAdapter() {
        assertThatThrownBy(() -> HexagonalRules.applicationDependsOnlyOnDomain(INVALID).check(INVALID_EXAMPLES))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("UseCaseUsingAdapter");
    }

    @Test
    void rejectsInboundAdapterThatUsesOutboundAdapter() {
        assertThatThrownBy(() -> HexagonalRules.inboundAdaptersDoNotUseOutboundAdapters(INVALID).check(INVALID_EXAMPLES))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("ControllerUsingRepository");
    }

    @Test
    void rejectsOutboundAdapterThatUsesInboundAdapter() {
        assertThatThrownBy(() -> HexagonalRules.outboundAdaptersDoNotUseInboundAdapters(INVALID).check(INVALID_EXAMPLES))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("RepositoryUsingController");
    }
}
