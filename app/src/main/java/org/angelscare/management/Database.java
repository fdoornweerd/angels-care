package org.angelscare.management;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;

/**
 * The application's local SQLite database.
 *
 * <h2>Where the database lives</h2>
 *
 * There is no server and no shared database. Every machine gets its own file, created on first
 * launch inside a folder named {@code AngelsCareData} in the current user's home directory:
 *
 * <pre>
 *   macOS (development)   /Users/&lt;you&gt;/AngelsCareData/angels-care.db
 *   Windows (deployed)    C:\Users\&lt;name&gt;\AngelsCareData\angels-care.db
 * </pre>
 *
 * The path is derived from the {@code user.home} system property, so it is correct on both without
 * any per-platform branching. Two consequences worth remembering:
 *
 * <ul>
 *   <li>Data does <em>not</em> travel with the installer. Each person's database starts empty, and
 *       nothing one user enters is visible to another.</li>
 *   <li>Uninstalling the application does not delete the database. The folder is deliberately
 *       outside the install directory so that reinstalling or upgrading never destroys data.</li>
 * </ul>
 *
 * <h2>Why the two machines behave differently</h2>
 *
 * The Java code is identical on both, but it runs in two quite different environments:
 *
 * <table border="1">
 *   <caption>Runtime differences</caption>
 *   <tr><th></th><th>Mac (development)</th><th>Windows (deployed)</th></tr>
 *   <tr><td>Started by</td><td>{@code ./gradlew run}</td><td>Desktop shortcut from the installer</td></tr>
 *   <tr><td>JVM</td><td>Your installed JDK 21</td><td>A trimmed JVM bundled inside the app by jlink</td></tr>
 *   <tr><td>sqlite-jdbc is</td><td>A jar on the module path</td><td>A module baked into the runtime image</td></tr>
 *   <tr><td>Native library</td><td>{@code libsqlitejdbc.dylib} (Mac/aarch64)</td><td>{@code sqlitejdbc.dll} (Windows/x86_64)</td></tr>
 * </table>
 *
 * That last row is the part that catches people out. sqlite-jdbc is not pure Java: it carries a
 * compiled binary for every platform inside its jar and, on the first connection, unpacks the one
 * matching the current OS to disk before loading it. The unpacking is where a working Mac build and
 * a failing Windows build diverge, which is what the {@code static} block below is about.
 */
public class Database {

    private static final String DB_URL = "jdbc:sqlite:" + getDbPath();

    static {
        // By default sqlite-jdbc unpacks its native library into java.io.tmpdir. On Windows that is
        // a per-user temp folder that antivirus software and locked-down machines routinely block
        // from holding an executable, and the failure is close to invisible: DriverManager loads
        // JDBC drivers inside a `catch (Throwable) { }`, so the driver is silently never registered
        // and the only symptom is "No suitable driver found" - a message that points at the URL
        // rather than at the real problem.
        //
        // Unpacking into the application's own data folder instead avoids depending on the temp
        // directory at all. This is cheap insurance: it was added while diagnosing exactly that
        // symptom on a Windows laptop, alongside the installer fix that turned out to be the main
        // cause, so it has never been proven to be load-bearing on its own. It is kept because the
        // failure it prevents is silent and expensive to diagnose remotely.
        try {
            File nativeDir = new File(Diagnostics.appDataDir(), "native");
            if (!nativeDir.exists()) {
                nativeDir.mkdirs();
            }
            System.setProperty("org.sqlite.tmpdir", nativeDir.getAbsolutePath());
        } catch (Throwable t) {
            Diagnostics.log("Could not redirect org.sqlite.tmpdir", t);
        }
        // Nothing in this block may throw. A static initializer that throws becomes an
        // ExceptionInInitializerError on the first touch of this class - which happens inside
        // Main.start(), before the window is shown - so a recoverable database problem would take
        // the whole application down with no window and no error message.
    }

    private static String getDbPath() {
        return new File(Diagnostics.appDataDir(), "angels-care.db").getAbsolutePath();
    }

    /**
     * Opens the database, making sure the schema exists, and reports what happened.
     * Returns a human-readable message rather than throwing, so the caller can always show it.
     */
    public static String testConnection() {
        Diagnostics.log("Opening database: " + DB_URL);
        try (Connection conn = DriverManager.getConnection(DB_URL);
             Statement stmt = conn.createStatement()) {

            stmt.execute("CREATE TABLE IF NOT EXISTS students (id INTEGER PRIMARY KEY, name TEXT)");
            stmt.execute("INSERT INTO students (name) VALUES ('Test Student')");

            try (ResultSet rs = stmt.executeQuery("SELECT COUNT(*) AS count FROM students")) {
                rs.next();
                int count = rs.getInt("count");
                Diagnostics.log("Connected; rows in students = " + count);
                return "SQLite connected.\nRows in students table: " + count + "\n\nSUCCESS!";
            }
        } catch (Throwable t) {
            // Throwable, not SQLException: if the native library cannot be loaded the failure
            // arrives as an UnsatisfiedLinkError or ExceptionInInitializerError, neither of which
            // is an SQLException. Catching only SQLException would let those escape unreported.
            Diagnostics.log("Database connection failed", t);
            return "SQLite connection FAILED.\n"
                    + Diagnostics.describe(t) + "\n\n"
                    + "Database file: " + DB_URL;
        }
    }
}
