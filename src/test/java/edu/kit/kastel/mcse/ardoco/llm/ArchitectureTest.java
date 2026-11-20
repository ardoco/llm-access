/* Licensed under MIT 2026. */
package edu.kit.kastel.mcse.ardoco.llm;

import static com.tngtech.archunit.lang.SimpleConditionEvent.violated;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;

import java.util.Set;
import java.util.stream.Collectors;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaModifier;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;

import edu.kit.kastel.mcse.ardoco.llm.cache.CacheKey;
import edu.kit.kastel.mcse.ardoco.llm.cache.CacheParameter;

/**
 * Architecture tests that guard the correctness of the cache.
 * <p>
 * A configuration attribute that is missing from a {@link CacheKey} or from {@link CacheParameter#parameters()} makes
 * different configurations share cache entries or cache files. This silently returns wrong cached results, so these
 * rules enforce completeness structurally.
 */
@AnalyzeClasses(packages = "edu.kit.kastel.mcse.ardoco.llm", importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {

    /**
     * Each {@link CacheKey} implementation must have a static {@code of(CacheParameter, String)} method that reads all
     * attributes of the {@link CacheParameter}.
     * <p>
     * If an attribute (model name, seed, temperature, ...) is not read, it is not part of the key. Entries created with
     * different configurations would then collide and return wrong cached results.
     */
    @ArchTest
    static final ArchRule cacheKeysMustHaveOfMethodWithCacheParameter = classes().that()
            .implement(CacheKey.class)
            .and()
            .areNotInterfaces()
            .should(new ArchCondition<>("have a static 'of' method that takes a CacheParameter and String, and reads all CacheParameter attributes") {
                @Override
                public void check(JavaClass javaClass, ConditionEvents events) {
                    var ofMethods = javaClass.getMethods()
                            .stream()
                            .filter(m -> m.getName().equals("of"))
                            .filter(m -> m.getModifiers().contains(JavaModifier.STATIC))
                            .filter(m -> m.getRawParameterTypes().size() == 2)
                            .filter(m -> m.getRawParameterTypes().get(0).isAssignableTo(CacheParameter.class))
                            .filter(m -> m.getRawParameterTypes().get(1).isAssignableTo(String.class))
                            .toList();

                    if (ofMethods.isEmpty()) {
                        events.add(violated(javaClass, "Class %s does not have a static 'of' method with signature: of(CacheParameter, String)".formatted(
                                javaClass.getFullName())));
                        return;
                    }

                    for (var ofMethod : ofMethods) {
                        var cacheParameterType = ofMethod.getRawParameterTypes().get(0);

                        var parameterMethods = cacheParameterType.getMethods()
                                .stream()
                                .filter(m -> !m.getOwner().isEquivalentTo(Object.class))
                                // parameters() generates the cache file name and is not used in key creation
                                .filter(m -> !m.getName().equals("parameters"))
                                // mode() is fixed per key type (a ChatCacheKey is always CHAT) and only selects the file name prefix
                                .filter(m -> !m.getName().equals("mode"))
                                // createCacheKey() is the factory that calls of(), not the other way round
                                .filter(m -> !m.getName().equals("createCacheKey"))
                                .filter(m -> !m.getName().equals("equals"))
                                .filter(m -> !m.getName().equals("hashCode"))
                                .filter(m -> !m.getName().equals("toString"))
                                .toList();

                        Set<String> calledMethodNames = ofMethod.getMethodCallsFromSelf()
                                .stream()
                                .map(call -> call.getTarget().getName())
                                .collect(Collectors.toSet());

                        for (var parameterMethod : parameterMethods) {
                            if (!calledMethodNames.contains(parameterMethod.getName())) {
                                events.add(violated(javaClass, "Method %s.of() does not read CacheParameter attribute '%s'".formatted(javaClass.getSimpleName(),
                                        parameterMethod.getName())));
                            }
                        }
                    }
                }
            });

    /**
     * Each {@link CacheParameter} implementation must have a {@code parameters()} method that accesses all its fields.
     * <p>
     * {@code parameters()} determines the cache file name. A field that is missing there makes different configurations
     * share one cache file and silently return wrong cached results.
     */
    @ArchTest
    static final ArchRule cacheParametersMustUseAllFieldsInParametersMethod = classes().that()
            .implement(CacheParameter.class)
            .and()
            .areNotInterfaces()
            .should(new ArchCondition<>("have a parameters() method that accesses all fields") {
                @Override
                public void check(JavaClass javaClass, ConditionEvents events) {
                    var parametersMethod = javaClass.getMethods()
                            .stream()
                            .filter(m -> m.getName().equals("parameters"))
                            .filter(m -> m.getRawParameterTypes().isEmpty())
                            .findFirst();

                    if (parametersMethod.isEmpty()) {
                        events.add(violated(javaClass, "Class %s does not have a parameters() method".formatted(javaClass.getFullName())));
                        return;
                    }

                    var fields = javaClass.getAllFields().stream().filter(f -> !f.getModifiers().contains(JavaModifier.STATIC)).toList();

                    var method = parametersMethod.get();

                    Set<String> accessedFieldNames = method.getFieldAccesses().stream().map(access -> access.getTarget().getName()).collect(Collectors.toSet());
                    // Record accessor methods
                    Set<String> calledMethodNames = method.getMethodCallsFromSelf()
                            .stream()
                            .map(call -> call.getTarget().getName())
                            .collect(Collectors.toSet());

                    for (var field : fields) {
                        String fieldName = field.getName();
                        if (!accessedFieldNames.contains(fieldName) && !calledMethodNames.contains(fieldName)) {
                            events.add(violated(javaClass, "Method %s.parameters() does not access field '%s'".formatted(javaClass.getSimpleName(),
                                    fieldName)));
                        }
                    }
                }
            });
}
