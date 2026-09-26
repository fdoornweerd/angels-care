package org.angelscare.management;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

/**
 * Starts the Spring context against the database in a given data folder.
 *
 * <p>Never throws: every failure comes back as {@link StartupResult.Failed}, because an exception
 * escaping JavaFX start-up means a window that never appears.
 */
public final class Bootstrap {

    private Bootstrap() {
    }

    public static StartupResult start(Path dataDir) {
        try {
            Files.createDirectories(dataDir);
            redirectSqliteNativeLibrary(dataDir);
            Diagnostics.log("Starting Spring; data folder " + dataDir.toAbsolutePath());

            ConfigurableApplicationContext context = new SpringApplicationBuilder(AngelsCareApplication.class)
                    .web(WebApplicationType.NONE)
                    // Spring sets java.awt.headless=true by default, which a desktop app must not.
                    .headless(false)
                    .run("--angelscare.data-dir=" + dataDir.toAbsolutePath());

            Diagnostics.log("Spring started.");
            return new StartupResult.Started(context);
        } catch (Throwable t) {
            // Throwable, not Exception: a native library that fails to load arrives as an Error.
            Diagnostics.log("Start-up failed", t);
            return new StartupResult.Failed(failureMessage(t), t);
        }
    }

    /**
     * By default sqlite-jdbc unpacks its native library into java.io.tmpdir. On Windows that is a
     * per-user temp folder that antivirus software and locked-down machines routinely block from
     * holding an executable, and the failure is close to invisible: DriverManager loads JDBC
     * drivers inside a {@code catch (Throwable) { }}, so the driver is silently never registered
     * and the only symptom is "No suitable driver found".
     *
     * <p>This was added while diagnosing exactly that symptom on a Windows laptop, alongside the
     * installer fix that turned out to be the main cause, so it has never been proven to be
     * load-bearing on its own. It is kept because the failure it prevents is silent and expensive
     * to diagnose remotely. Must run before the first database connection.
     */
    private static void redirectSqliteNativeLibrary(Path dataDir) {
        try {
            File nativeDir = dataDir.resolve("native").toFile();
            nativeDir.mkdirs();
            System.setProperty("org.sqlite.tmpdir", nativeDir.getAbsolutePath());
        } catch (Throwable t) {
            Diagnostics.log("Could not redirect org.sqlite.tmpdir", t);
        }
    }

    private static String failureMessage(Throwable t) {
        Path log = Diagnostics.logFilePath();
        return "Angels Care could not start.\n\n"
                + Diagnostics.describe(rootCause(t)) + "\n\n"
                + "Please send this file to whoever maintains the app:\n"
                + (log != null ? log.toString() : "(no log file could be written)");
    }

    private static Throwable rootCause(Throwable t) {
        Throwable c = t;
        while (c.getCause() != null && c.getCause() != c) {
            c = c.getCause();
        }
        return c;
    }
}
