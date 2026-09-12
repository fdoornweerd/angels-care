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
import java.util.ArrayList;
import java.util.List;

/**
 * Collects everything we need to understand a start-up failure on a machine we cannot
 * attach a debugger to. Nothing in here is allowed to throw: a broken logger must never
 * be the reason the application fails to start.
 */
public final class Diagnostics {

    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("HH:mm:ss.SSS");
    private static final List<String> LINES = new ArrayList<>();
    private static Path logFile;

    private Diagnostics() {
    }

    /** Directory the app owns: the database, the log and the extracted native library all live here. */
    public static File appDataDir() {
        File dir = new File(System.getProperty("user.home", "."), "AngelsCareData");
        try {
            if (!dir.exists()) {
                dir.mkdirs();
            }
        } catch (Throwable ignored) {
            // fall through; callers handle an unusable directory
        }
        return dir;
    }

    public static synchronized void log(String message) {
        String line = LocalDateTime.now().format(STAMP) + "  " + message;
        LINES.add(line);
        System.out.println(line);
        append(line);
    }

    public static synchronized void log(String message, Throwable t) {
        log(message + "  ->  " + describe(t));
        log(stackTrace(t));
    }

    /** Everything logged so far, for display inside the window. */
    public static synchronized String transcript() {
        return String.join("\n", LINES);
    }

    public static synchronized Path logFilePath() {
        return logFile;
    }

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

    /** Environment facts that differ between the developer's Mac and the packaged Windows build. */
    public static void logEnvironment() {
        log("=== Angels Care start-up " + LocalDateTime.now() + " ===");
        log("log file             : " + logFile);
        String[] props = {
            "java.version", "java.vendor", "java.home", "os.name", "os.arch", "os.version",
            "user.home", "user.name", "java.io.tmpdir", "jdk.module.path", "java.class.path"
        };
        for (String p : props) {
            log(p + " = " + System.getProperty(p));
        }
        log("module of Main       : " + Main.class.getModule().getName());
        log("app data dir         : " + appDataDir() + " (writable=" + appDataDir().canWrite() + ")");
        log("temp dir writable    : " + tempDirWritable());
    }

    private static String tempDirWritable() {
        try {
            Path probe = Files.createTempFile("angels-care-probe", ".tmp");
            Files.deleteIfExists(probe);
            return "yes";
        } catch (Throwable t) {
            return "NO -> " + describe(t);
        }
    }

    /** Opens the log file. Safe to call more than once. */
    public static synchronized void startLogFile() {
        try {
            logFile = appDataDir().toPath().resolve("angels-care-startup.log");
            Files.writeString(logFile, "", StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        } catch (Throwable t) {
            logFile = null;
            System.out.println("Could not open log file: " + describe(t));
        }
    }

    private static void append(String line) {
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
}
