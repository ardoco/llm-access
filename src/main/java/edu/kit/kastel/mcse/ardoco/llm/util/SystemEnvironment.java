/* Licensed under MIT 2026. */
package edu.kit.kastel.mcse.ardoco.llm.util;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.github.cdimascio.dotenv.Dotenv;
import io.github.cdimascio.dotenv.DotenvEntry;

/**
 * An {@link EnvironmentProvider} that reads system environment variables and, optionally, a {@code .env} file.
 * <p>
 * The following precedence is used for environment variables:
 * <ol>
 * <li>Values from system environment variables</li>
 * <li>Values from the .env file (if it exists)</li>
 * </ol>
 * A .env entry is therefore used only when that variable is not already exported.
 * <p>
 * {@link #SystemEnvironment()} reads the .env file from the current working directory, while
 * {@link #SystemEnvironment(Path)} reads an explicitly given file. The path is resolved on construction, but the file
 * is read lazily when first needed (the first lookup not answered by a system environment variable, or a comparison)
 * and then kept for the lifetime of the instance, so creating an instance is cheap
 * and a missing or malformed file only matters once a value is actually requested. The file should contain key-value
 * pairs in the format:
 * <pre>
 * KEY=value
 * </pre>
 * Instances are thread-safe and independent of each other; there is no shared global environment.
 */
public final class SystemEnvironment implements EnvironmentProvider {

    private static final Logger logger = LoggerFactory.getLogger(SystemEnvironment.class);

    /** The absolute path of the .env file to read */
    private final Path dotenvFile;
    /** Whether the file was given explicitly; a missing explicit file is logged as a warning */
    private final boolean explicit;
    /** Guards the lazy loading of {@link #dotenvValues} */
    private final Object loadLock = new Object();
    /** The entries declared in the .env file (empty if there is no such file), or null until first needed */
    private volatile @Nullable Map<String, String> dotenvValues;

    /**
     * Creates an environment that reads system environment variables and the {@code .env} file in the current
     * working directory, if present.
     */
    public SystemEnvironment() {
        this(Path.of(".env"), false);
    }

    /**
     * Creates an environment that reads system environment variables and the given {@code .env} file. If the file
     * does not exist, a warning is logged when the file is first needed and only system environment variables are used.
     *
     * @param dotenvFile The path to the .env file
     */
    public SystemEnvironment(Path dotenvFile) {
        this(Objects.requireNonNull(dotenvFile, "dotenvFile must not be null"), true);
    }

    private SystemEnvironment(Path file, boolean explicit) {
        this.dotenvFile = file.toAbsolutePath().normalize();
        this.explicit = explicit;
    }

    /**
     * Retrieves an environment variable value.
     * This method:
     * <ol>
     * <li>Returns the system environment variable, if set</li>
     * <li>Otherwise returns the entry of the .env file, loading the file on the first call</li>
     * <li>Returns null if the variable is not found in either location</li>
     * </ol>
     *
     * @param key The name of the environment variable to retrieve
     * @return The value of the environment variable, or null if not found
     * @throws io.github.cdimascio.dotenv.DotenvException if the .env file exists but cannot be read or parsed
     */
    @Override
    public @Nullable String getenv(String key) {
        String systemValue = System.getenv(key);
        if (systemValue != null)
            return systemValue;
        return dotenvValues().get(key);
    }

    /**
     * Retrieves an environment variable value, requiring it to be non-null. In contrast to the default
     * implementation, the exception message names the system environment and the .env file as places to set it.
     *
     * @param key The name of the environment variable to retrieve
     * @return The value of the environment variable
     * @throws IllegalStateException if the variable is not set
     */
    @Override
    public String getenvNonNull(String key) {
        String value = getenv(key);
        if (value == null) {
            throw new IllegalStateException("environment variable %s is missing, set it in the system environment or in '%s'".formatted(key, dotenvFile));
        }
        return value;
    }

    /**
     * Returns the entries declared in the .env file, loading the file on the first call. Concurrent first calls
     * load the file only once; if loading fails, the next call tries again.
     *
     * @return The entries declared in the .env file, or an empty map if there is no such file
     */
    private Map<String, String> dotenvValues() {
        Map<String, String> values = dotenvValues;
        if (values == null) {
            synchronized (loadLock) {
                values = dotenvValues;
                if (values == null) {
                    values = loadDotenvValues();
                    dotenvValues = values;
                }
            }
        }
        return values;
    }

    private Map<String, String> loadDotenvValues() {
        if (!Files.isRegularFile(dotenvFile)) {
            if (explicit) {
                logger.warn("No .env file found at '{}', using system environment variables", dotenvFile);
            } else {
                logger.debug("No .env file found in the working directory, using system environment variables");
            }
            return Map.of();
        }
        Path directory = Objects.requireNonNullElse(dotenvFile.getParent(), dotenvFile.getRoot());
        // dotenv-java strips a trailing ".env" from the directory (e.g. "conf.env" becomes "conf"); a trailing
        // separator keeps such directory names intact.
        Dotenv dotenv = Dotenv.configure().directory(directory + File.separator).filename(dotenvFile.getFileName().toString()).load();
        return dotenv.entries(Dotenv.Filter.DECLARED_IN_ENV_FILE).stream().collect(Collectors.toUnmodifiableMap(DotenvEntry::getKey, DotenvEntry::getValue));
    }

    /**
     * Two system environments are equal if they refer to the same .env file and have loaded the same entries from
     * it. The system environment variables are not compared, as they are shared by all instances of a process. Both
     * instances load their .env file for the comparison if they have not done so yet; as each instance keeps the
     * entries it loaded first, instances created before and after a change of the file may differ.
     *
     * @param o The object to compare with
     * @return {@code true} if {@code o} is a system environment with the same .env file and the same entries
     * @throws io.github.cdimascio.dotenv.DotenvException if a .env file exists but cannot be read or parsed
     */
    @Override
    public boolean equals(@Nullable Object o) {
        if (this == o)
            return true;
        return o instanceof SystemEnvironment other && dotenvFile.equals(other.dotenvFile) && dotenvValues().equals(other.dotenvValues());
    }

    /**
     * Computes a hash code from the .env file path and its loaded entries, consistent with {@link #equals(Object)}.
     * The .env file is loaded if this has not happened yet.
     *
     * @return The hash code of this environment
     * @throws io.github.cdimascio.dotenv.DotenvException if the .env file exists but cannot be read or parsed
     */
    @Override
    public int hashCode() {
        return Objects.hash(dotenvFile, dotenvValues());
    }

    @Override
    public String toString() {
        return "SystemEnvironment[dotenvFile=" + dotenvFile + "]";
    }
}
