/* Licensed under MIT 2026. */
package edu.kit.kastel.mcse.ardoco.llm.cache;

import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

/**
 * Drops all registered {@link LocalCache} instances after each test and test class, so that the shutdown hook does not try to flush into deleted temporary
 * directories. Registered globally via JUnit extension autodetection.
 */
public class ClearLocalCachesExtension implements AfterEachCallback, AfterAllCallback {
    @Override
    public void afterEach(ExtensionContext context) {
        LocalCache.clearInstances();
    }

    @Override
    public void afterAll(ExtensionContext context) {
        LocalCache.clearInstances();
    }
}
