package org.angelscare.management;

import javafx.application.Application;

/**
 * The program entry point. Deliberately not the JavaFX {@link Application} itself: when JavaFX is
 * on the classpath (it is - this app is not a JPMS module), a main class that extends Application
 * refuses to start with "JavaFX runtime components are missing".
 */
public final class Launcher {

    private Launcher() {
    }

    public static void main(String[] args) {
        // Before anything else, so that a crash during start-up still leaves a log behind.
        Diagnostics.startLogFile();
        Thread.setDefaultUncaughtExceptionHandler(
                (thread, t) -> Diagnostics.log("Uncaught exception on thread " + thread.getName(), t));
        try {
            Application.launch(FxApp.class, args);
        } catch (Throwable t) {
            Diagnostics.log("FATAL: application failed to launch", t);
            System.exit(1);
        }
    }
}
