package org.angelscare.management;

import java.io.File;
import java.io.InputStream;
import java.sql.Connection;
import java.sql.Driver;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.Properties;

public class Database {

    private static final String DB_URL = "jdbc:sqlite:" + getDbPath();

    static {
        // sqlite-jdbc unpacks a native .dll out of its jar at first use. The default target is
        // java.io.tmpdir, which on a locked-down or antivirus-guarded Windows profile is often
        // not writable/executable - and the resulting failure surfaces only as the misleading
        // "No suitable driver found". Point it at a directory we know we can write to.
        try {
            File nativeDir = new File(Diagnostics.appDataDir(), "native");
            if (!nativeDir.exists()) {
                nativeDir.mkdirs();
            }
            System.setProperty("org.sqlite.tmpdir", nativeDir.getAbsolutePath());
        } catch (Throwable t) {
            Diagnostics.log("Could not set org.sqlite.tmpdir", t);
        }
        // NOTE: deliberately no Class.forName() that rethrows here. A throwing static
        // initializer turns a recoverable database problem into an ExceptionInInitializerError
        // that kills the window before it is ever shown.
    }

    private static String getDbPath() {
        File appDataDir = Diagnostics.appDataDir();
        return new File(appDataDir, "angels-care.db").getAbsolutePath();
    }

    public static String dbUrl() {
        return DB_URL;
    }

    /**
     * Tries every way we know of to get a SQLite connection and reports what happened.
     * Never throws.
     */
    public static String testConnection() {
        Diagnostics.log("Database URL: " + DB_URL);
        Diagnostics.log("sqlite driver class present: " + driverClassStatus());
        Diagnostics.log("sqlite native library in image: " + nativeLibraryStatus());
        Diagnostics.log("drivers registered with DriverManager: " + registeredDrivers());

        Throwable last = null;

        // 1. The normal route: DriverManager finds the driver through ServiceLoader.
        try {
            return runProbe(DriverManager.getConnection(DB_URL), "DriverManager (ServiceLoader)");
        } catch (Throwable t) {
            last = t;
            Diagnostics.log("Strategy 1 (DriverManager) failed", t);
        }

        // 2. Load the driver class explicitly, register it, retry DriverManager.
        try {
            Driver driver = newDriver();
            DriverManager.registerDriver(driver);
            Diagnostics.log("Registered " + driver.getClass().getName() + " manually");
            return runProbe(DriverManager.getConnection(DB_URL), "DriverManager after manual registration");
        } catch (Throwable t) {
            last = t;
            Diagnostics.log("Strategy 2 (manual registration) failed", t);
        }

        // 3. Skip DriverManager entirely and ask the driver for a connection directly.
        try {
            Driver driver = newDriver();
            Connection conn = driver.connect(DB_URL, new Properties());
            if (conn == null) {
                throw new IllegalStateException("driver.connect() returned null for " + DB_URL);
            }
            return runProbe(conn, "org.sqlite.JDBC.connect() direct");
        } catch (Throwable t) {
            last = t;
            Diagnostics.log("Strategy 3 (direct driver.connect) failed", t);
        }

        // 4. SQLiteDataSource - a different code path again inside sqlite-jdbc.
        try {
            Class<?> dsClass = Class.forName("org.sqlite.SQLiteDataSource");
            Object ds = dsClass.getConstructor().newInstance();
            dsClass.getMethod("setUrl", String.class).invoke(ds, DB_URL);
            Connection conn = (Connection) dsClass.getMethod("getConnection").invoke(ds);
            return runProbe(conn, "SQLiteDataSource");
        } catch (Throwable t) {
            last = t;
            Diagnostics.log("Strategy 4 (SQLiteDataSource) failed", t);
        }

        return "SQLite connection FAILED (all 4 strategies).\n"
                + "Last error: " + Diagnostics.describe(last) + "\n"
                + "Database file: " + DB_URL;
    }

    private static Driver newDriver() throws Exception {
        Class<?> cls = Class.forName("org.sqlite.JDBC");
        return (Driver) cls.getConstructor().newInstance();
    }

    private static String runProbe(Connection conn, String how) throws Exception {
        try (Connection c = conn; Statement stmt = c.createStatement()) {
            stmt.execute("CREATE TABLE IF NOT EXISTS students (id INTEGER PRIMARY KEY, name TEXT)");
            stmt.execute("INSERT INTO students (name) VALUES ('Test Student')");
            try (ResultSet rs = stmt.executeQuery("SELECT COUNT(*) AS count FROM students")) {
                rs.next();
                int count = rs.getInt("count");
                Diagnostics.log("Connected via " + how + "; rows = " + count);
                return "SQLite connected via " + how + ".\n"
                        + "Rows in students table: " + count + "\n\nSUCCESS!";
            }
        }
    }

    private static String driverClassStatus() {
        try {
            Class<?> cls = Class.forName("org.sqlite.JDBC");
            return "yes (module=" + cls.getModule().getName() + ")";
        } catch (Throwable t) {
            return "NO -> " + Diagnostics.describe(t);
        }
    }

    /**
     * sqlite-jdbc ships its Windows .dll as a resource inside the jar. jlink/jpackage can drop or
     * encapsulate that resource, which is one of the ways this breaks only in the packaged build.
     */
    private static String nativeLibraryStatus() {
        String resource = "org/sqlite/native/Windows/x86_64/sqlitejdbc.dll";
        try {
            Class<?> cls = Class.forName("org.sqlite.JDBC");
            try (InputStream in = cls.getModule().getResourceAsStream(resource)) {
                if (in != null) {
                    return "found " + resource;
                }
            }
            try (InputStream in = cls.getClassLoader().getResourceAsStream(resource)) {
                if (in != null) {
                    return "found (via classloader) " + resource;
                }
            }
            // A named module can encapsulate resources, so "not visible" is not proof of "absent" -
            // but combined with the strategy failures below it points straight at the packaging.
            return "NOT VISIBLE: " + resource + " (missing from the runtime image, or encapsulated)";
        } catch (Throwable t) {
            return "could not check -> " + Diagnostics.describe(t);
        }
    }

    private static String registeredDrivers() {
        try {
            StringBuilder sb = new StringBuilder();
            DriverManager.drivers().forEach(d -> sb.append(d.getClass().getName()).append(' '));
            return sb.length() == 0 ? "(none)" : sb.toString().trim();
        } catch (Throwable t) {
            return "could not list -> " + Diagnostics.describe(t);
        }
    }
}
