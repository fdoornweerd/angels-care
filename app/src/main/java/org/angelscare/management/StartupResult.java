package org.angelscare.management;

import org.springframework.context.ConfigurableApplicationContext;

/** The outcome of {@link Bootstrap#start}: either a running context, or a message to show. */
public sealed interface StartupResult {

    record Started(ConfigurableApplicationContext context) implements StartupResult {
    }

    /** {@code message} is written for a non-technical user and names the log file. */
    record Failed(String message, Throwable cause) implements StartupResult {
    }
}
