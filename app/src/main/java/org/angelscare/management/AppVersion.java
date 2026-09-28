package org.angelscare.management;

import java.io.InputStream;
import java.util.Properties;

/**
 * The version of this build, e.g. {@code 1.0.57}: written by Gradle at build time (CI numbers each
 * build), shown in the start-up log and the window's footer so a tester can say which build they
 * have.
 */
public final class AppVersion {

    private static final String RESOURCE = "/angels-care-version.properties";

    private AppVersion() {
    }

    /** Never throws (it is read on the start-up path); "unknown" if the build info is missing. */
    public static String current() {
        try (InputStream in = AppVersion.class.getResourceAsStream(RESOURCE)) {
            if (in == null) {
                return "unknown";
            }
            Properties properties = new Properties();
            properties.load(in);
            return properties.getProperty("version", "unknown").strip();
        } catch (Throwable t) {
            return "unknown";
        }
    }
}
