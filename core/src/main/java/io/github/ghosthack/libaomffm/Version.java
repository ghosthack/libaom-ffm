package io.github.ghosthack.libaomffm;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/** Artifact version used to key the extracted-native cache. */
public final class Version {
    public static final String VERSION = load();

    private Version() {}

    private static String load() {
        try (InputStream in = Version.class.getResourceAsStream("version.properties")) {
            Properties properties = new Properties();
            properties.load(in);
            String version = properties.getProperty("version");
            if (version == null || version.isBlank()) {
                throw new IllegalStateException("version.properties has no version entry");
            }
            return version;
        } catch (IOException | NullPointerException e) {
            throw new IllegalStateException("failed to read version.properties", e);
        }
    }
}

