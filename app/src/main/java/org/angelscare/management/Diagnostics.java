package org.angelscare.management;

import java.io.File;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Writes a start-up log to {@code <user home>/AngelsCareData/angels-care-startup.log}.
 *
 * <p>This exists because the application is installed on machines nobody can attach a debugger to.
 * When a packaged build fails before its window appears, there is otherwise nothing at all to go on
 * - no console, no error, no window. The log file is written from the very first line of
 * {@code main}, so it survives failures that happen before anything is drawn, and asking for that
 * one file is enough to diagnose a problem remotely.
 *
 * <p>Nothing here may throw: a broken logger must never be the reason the application fails to
 * start.
 */
public final class Diagnostics {

    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("HH:mm:ss.SSS");
    private static Path logFile;

    private Diagnostics() {
    }

    /**
     * The application's data folder, created if missing. Holds the database, this log, and the
     * native SQLite library. Lives in the user's home directory, outside the install directory, so
     * that uninstalling or upgrading never deletes anyone's data.
     */
    public static File appDataDir() {
        File dir = new File(System.getProperty("user.home", "."), "AngelsCareData");
        try {
            if (!dir.exists()) {
                dir.mkdirs();
            }
        } catch (Throwable ignored) {
            // callers handle an unusable directory
        }
        return dir;
    }

    public static synchronized void startLogFile() {
        try {
            logFile = appDataDir().toPath().resolve("angels-care-startup.log");
            Files.writeString(logFile, "", StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            log("=== Angels Care " + LocalDateTime.now() + " ===");
            log("java " + System.getProperty("java.version")
                    + " on " + System.getProperty("os.name")
                    + " " + System.getProperty("os.arch"));
            log("data folder: " + appDataDir());
        } catch (Throwable t) {
            logFile = null;
            System.out.println("Could not open log file: " + describe(t));
        }
    }

    public static synchronized void log(String message) {
        String line = LocalDateTime.now().format(STAMP) + "  " + message;
        System.out.println(line);
        if (logFile == null) {
            return;
        }
        try {
            Files.writeString(logFile, line + System.lineSeparator(), StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (Throwable ignored) {
            // a failing log write must never break start-up
        }
    }

    public static synchronized void log(String message, Throwable t) {
        log(message + "  ->  " + describe(t));
        log(stackTrace(t));
    }

    public static synchronized Path logFilePath() {
        return logFile;
    }

    /** The exception and its causes on one line, for showing to a non-technical user. */
    public static String describe(Throwable t) {
        if (t == null) {
            return "(no exception)";
        }
        StringBuilder sb = new StringBuilder();
        for (Throwable c = t; c != null; c = c.getCause()) {
            if (sb.length() > 0) {
                sb.append(" | caused by ");
            }
            sb.append(c.getClass().getName()).append(": ").append(c.getMessage());
            if (c.getCause() == c) {
                break;
            }
        }
        return sb.toString();
    }

    public static String stackTrace(Throwable t) {
        if (t == null) {
            return "";
        }
        Writer w = new StringWriter();
        t.printStackTrace(new PrintWriter(w));
        return w.toString();
    }
}
