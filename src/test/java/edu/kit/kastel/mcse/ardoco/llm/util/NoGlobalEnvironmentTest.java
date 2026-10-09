/* Licensed under MIT 2026. */
package edu.kit.kastel.mcse.ardoco.llm.util;

import static com.tngtech.archunit.core.domain.JavaClass.Predicates.assignableTo;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noFields;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

/**
 * Guards against reintroducing a global environment: an {@link EnvironmentProvider} must always be passed as a
 * constructor parameter or held in an instance field, never in a static field (for example a singleton).
 */
@AnalyzeClasses(packages = "edu.kit.kastel.mcse.ardoco.llm", importOptions = ImportOption.DoNotIncludeTests.class)
class NoGlobalEnvironmentTest {

    @ArchTest
    static final ArchRule noStaticEnvironmentFields = noFields().that()
            .areStatic()
            .should()
            .haveRawType(assignableTo(EnvironmentProvider.class))
            .because("environments must be injected, not shared globally");

    @ArchTest
    static final ArchRule systemEnvironmentIsNoSingleton = noFields().that()
            .areDeclaredIn(SystemEnvironment.class)
            .and()
            .areStatic()
            .should()
            .haveRawType(SystemEnvironment.class)
            .because("SystemEnvironment must not be a singleton");
}
