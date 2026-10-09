/* Licensed under MIT 2026. */
package edu.kit.kastel.mcse.ardoco.llm.util;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.github.cdimascio.dotenv.Dotenv;

/**
 * An {@link EnvironmentProvider} that reads system environment variables and, optionally, a {@code .env} file.
 * <p>
 * The following precedence is used for environment variables:
 * <ol>
 * <li>Values from system environment variables</li>
 * <li>Values from the .env file (if it exists)</li>
 * </ol>
 * Note that the system environment wins: dotenv-java resolves {@code System.getenv(key)} first and only falls back
 * to the parsed .env file, so a .env entry is used only when that variable is not already exported.
 * <p>
 * {@link #SystemEnvironment()} reads the .env file from the current working directory, while
 * {@link #SystemEnvironment(Path)} reads an explicitly given file. The file is loaded once on construction and
 * should contain key-value pairs in the format:
 * <pre>
 * KEY=value
 * </pre>
 * Instances are immutable and independent of each other; there is no shared global environment.
 */
public final class SystemEnvironment implements EnvironmentProvider {

    private static final Logger logger = LoggerFactory.getLogger(SystemEnvironment.class);

    /** The absolute path of the loaded .env file, or null if no .env file was loaded */
    private final @Nullable Path dotenvFile;
    /** The loaded .env configuration, or null if no .env file was loaded */
    private final @Nullable Dotenv dotenv;

    /**
     * Creates an environment that reads system environment variables and the {@code .env} file in the current
     * working directory, if present.
     */
    public SystemEnvironment() {
        this(Path.of(".env"), false);
    }

    /**
     * Creates an environment that reads system environment variables and the given {@code .env} file. If the file
     * does not exist, a warning is logged and only system environment variables are used.
     *
     * @param dotenvFile The path to the .env file
     */
    public SystemEnvironment(Path dotenvFile) {
        this(Objects.requireNonNull(dotenvFile, "dotenvFile must not be null"), true);
    }

    private SystemEnvironment(Path file, boolean explicit) {
        if (Files.isRegularFile(file)) {
            Path absolute = file.toAbsolutePath().normalize();
            Path directory = Objects.requireNonNullElse(absolute.getParent(), absolute.getRoot());
            this.dotenvFile = absolute;
            this.dotenv = Dotenv.configure().directory(directory.toString()).filename(absolute.getFileName().toString()).load();
        } else {
            if (explicit) {
                logger.warn("No .env file found at '{}', using system environment variables", file);
            } else {
                logger.debug("No .env file found in the working directory, using system environment variables");
            }
            this.dotenvFile = null;
            this.dotenv = null;
        }
    }

    /**
     * Retrieves an environment variable value.
     * This method:
     * <ol>
     * <li>Queries the loaded .env handle, which itself resolves {@code System.getenv(key)}
     * first and falls back to the parsed .env file</li>
     * <li>If no .env is loaded, falls back to system environment variables</li>
     * <li>Returns null if the variable is not found in either location</li>
     * </ol>
     * The effective precedence is therefore: system environment first, .env second.
     *
     * @param key The name of the environment variable to retrieve
     * @return The value of the environment variable, or null if not found
     */
    @Override
    public @Nullable String getenv(String key) {
        String dotenvValue = dotenv == null ? null : dotenv.get(key);
        if (dotenvValue != null)
            return dotenvValue;
        return System.getenv(key);
    }

    @Override
    public boolean equals(@Nullable Object o) {
        return o instanceof SystemEnvironment other && Objects.equals(dotenvFile, other.dotenvFile);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(dotenvFile);
    }

    @Override
    public String toString() {
        return "SystemEnvironment[dotenvFile=" + dotenvFile + "]";
    }
}
