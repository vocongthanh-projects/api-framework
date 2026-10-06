package vn.ots.qc.consumer.template.config;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/** Loads committed defaults, ignored local overrides, system properties, then environment variables. */
public final class ConsumerConfig {
    private static final ConsumerConfig INSTANCE = load();
    private final Properties values;

    private ConsumerConfig(Properties values) { this.values = values; }

    public static ConsumerConfig get() { return INSTANCE; }

    public String get(String key, String defaultValue) {
        String value = System.getProperty(key);
        if (isSet(value)) return value.trim();
        value = System.getenv(toEnvironmentName(key));
        if (isSet(value)) return value.trim();
        value = values.getProperty(key);
        return isSet(value) ? value.trim() : defaultValue;
    }

    public String require(String key) {
        String value = get(key, null);
        if (!isSet(value)) throw new IllegalStateException("Missing required configuration: " + key);
        return value;
    }

    private static ConsumerConfig load() {
        Properties properties = new Properties();
        try (InputStream input = ConsumerConfig.class.getClassLoader().getResourceAsStream("config.properties")) {
            if (input != null) properties.load(input);
        } catch (IOException e) {
            throw new IllegalStateException("Unable to read config.properties", e);
        }
        Path local = Path.of("config-local.properties");
        if (Files.isRegularFile(local)) {
            try (InputStream input = Files.newInputStream(local)) {
                properties.load(input);
            } catch (IOException e) {
                throw new IllegalStateException("Unable to read config-local.properties", e);
            }
        }
        return new ConsumerConfig(properties);
    }

    private static String toEnvironmentName(String key) {
        return key.replaceAll("([a-z0-9])([A-Z])", "$1_$2")
                .replaceAll("[^A-Za-z0-9]+", "_").toUpperCase(java.util.Locale.ROOT);
    }

    private static boolean isSet(String value) { return value != null && !value.isBlank(); }
}
