/* Licensed under MIT 2026. */
package edu.kit.kastel.mcse.ardoco.llm.util;

import static com.tngtech.archunit.base.DescribedPredicate.describe;
import static com.tngtech.archunit.base.DescribedPredicate.not;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.assignableTo;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noFields;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaMethodCall;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import edu.kit.kastel.mcse.ardoco.llm.cache.CacheManager;
import edu.kit.kastel.mcse.ardoco.llm.embedding.EmbeddingConfiguration;
import edu.kit.kastel.mcse.ardoco.llm.embedding.EmbeddingCreator;

/**
 * Guards against reintroducing a global environment: an {@link EnvironmentProvider} must always be passed as a
 * constructor parameter or held in an instance field, never in a static field (for example a singleton). Likewise,
 * the library must use the {@link CacheManager} it is given (and thereby that manager's environment) instead of
 * falling back to the default instance; only the convenience overload
 * {@link EmbeddingCreator#create(EmbeddingConfiguration)} may resolve the default instance.
 */
@AnalyzeClasses(packages = "edu.kit.kastel.mcse.ardoco.llm", importOptions = ImportOption.DoNotIncludeTests.class)
class NoGlobalEnvironmentTest {

    @ArchTest
    static final ArchRule noStaticEnvironmentFields = noFields().that()
            .areStatic()
            .should()
            .haveRawType(assignableTo(EnvironmentProvider.class))
            .because("environments must be injected, not shared globally");

    private static final DescribedPredicate<JavaMethodCall> CALLS_DEFAULT_CACHE_MANAGER = describe("CacheManager.getDefaultInstance()", call -> call.getTarget()
            .getOwner()
            .isEquivalentTo(CacheManager.class) && call.getTarget().getName().equals("getDefaultInstance"));

    private static final DescribedPredicate<JavaMethodCall> IN_CONVENIENCE_OVERLOAD = describe("EmbeddingCreator.create(EmbeddingConfiguration)", call -> call
            .getOrigin()
            .getFullName()
            .equals(EmbeddingCreator.class.getName() + ".create(" + EmbeddingConfiguration.class.getName() + ")"));

    @ArchTest
    static final ArchRule defaultCacheManagerOnlyInConvenienceOverload = noClasses().should()
            .callMethodWhere(CALLS_DEFAULT_CACHE_MANAGER.and(not(IN_CONVENIENCE_OVERLOAD)))
            .because("the cache manager (and with it the cache environment) must be injected");
}
