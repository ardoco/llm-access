/* Licensed under MIT 2026. */
package edu.kit.kastel.mcse.ardoco.llm.util;

import java.nio.file.Files;
import java.nio.file.Path;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.github.cdimascio.dotenv.Dotenv;

/**
 * A utility class for managing environment variables in the application.
 * This class provides functionality to:
 * <ul>
 * <li>Load environment variables from a .env file</li>
 * <li>Fall back to system environment variables if .env is not available</li>
 * <li>Retrieve environment variables with or without null checks</li>
 * </ul>
 *
 * The class uses the following precedence for environment variables:
 * <ol>
 * <li>Values from the .env file (if it exists)</li>
 * <li>Values from system environment variables</li>
 * </ol>
 *
 * The .env file should be placed in the root directory of the project and should
 * contain key-value pairs in the format:
 * <pre>
 * KEY=value
 * </pre>
 */
public class SystemEnvironment implements EnvironmentProvider {

    private static SystemEnvironment INSTANCE = null;

    private static final Logger logger = LoggerFactory.getLogger(SystemEnvironment.class);
    /** The loaded .env configuration, or null if no .env file exists */
    private @Nullable Dotenv dotenv;

    private SystemEnvironment() {
        dotenv = load();
    }

    /**
     * Returns the singleton instance of the SystemEnvironment class.
     *
     * @return The singleton instance
     */
    public static SystemEnvironment getInstance() {
        if (INSTANCE == null) {
            INSTANCE = new SystemEnvironment();
        }
        return INSTANCE;
    }

    @Override
    public @Nullable String getenv(String key) {
        String dotenvValue = dotenv == null ? null : dotenv.get(key);
        if (dotenvValue != null)
            return dotenvValue;
        return System.getenv(key);
    }

    @Override
    public String getenvNonNull(String key) {
        String env = getenv(key);
        if (env == null) {
            throw new IllegalStateException("environment variable %s is missing, use '.env' or your system to set it up".formatted(key));
        }
        return env;
    }

    /**
     * Loads the .env file configuration.
     * This method:
     * <ol>
     * <li>Checks if a .env file exists in the project root</li>
     * <li>If found, loads and returns the configuration</li>
     * <li>If not found, logs a message and returns null</li>
     * </ol>
     *
     * The method is synchronized to ensure thread safety during the initial loading.
     *
     * @return The loaded Dotenv configuration, or null if no .env file exists
     */
    private synchronized @Nullable Dotenv load() {
        if (dotenv != null) {
            return dotenv;
        }

        if (Files.exists(Path.of(".env"))) {
            return Dotenv.configure().load();
        } else {
            logger.info("No .env file found, using system environment variables");
            return null;
        }
    }

    /**
     * Overwrites the current .env configuration with a new one from the specified path.
     * This method:
     * <ol>
     * <li>Checks if a .env file exists at the given path</li>
     * <li>If found, loads and sets the new configuration</li>
     * <li>If not found, logs a warning and retains the existing configuration</li>
     * </ol>
     *
     * The method is synchronized to ensure thread safety when updating the configuration.
     *
     * @param path The path to the new .env file
     */
    public synchronized void overwrite(Path path) {
        if (Files.exists(path)) {
            String directory;
            if (path.getParent() != null) {
                directory = path.getParent().toAbsolutePath().toString();
            } else {
                directory = Path.of("").toAbsolutePath().toString();
            }

            dotenv = Dotenv.configure().directory(directory).filename(path.getFileName().toString()).load();
        } else {
            logger.warn("No .env file found at '{}', using system environment variables", path);
        }
    }
}
