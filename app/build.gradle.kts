/*
 * Angels Care - JavaFX + Spring Boot desktop app with a local SQLite database.
 *
 *   ./gradlew run        run it from source
 *   ./gradlew test       run the tests
 *   ./gradlew jpackage   build a native installer for the platform you are on
 *
 * The Windows installer is built by CI, not locally - see README.md.
 */

plugins {
    application
    alias(libs.plugins.javafx)
    // Not org.beryx.jlink: that plugin requires a fully modular (JPMS) application, and Spring Boot
    // is not modular. org.beryx.runtime builds the same trimmed JVM + jpackage installer from a
    // classpath application.
    alias(libs.plugins.runtime)
}

group = "org.angelscare"
version = "1.0.0"

repositories {
    mavenCentral()
}

dependencies {
    implementation(platform(libs.spring.boot.bom))
    implementation(libs.spring.boot.starter)
    implementation(libs.spring.boot.starter.jdbc)
    implementation(libs.spring.boot.starter.flyway)
    implementation(libs.sqlite.jdbc)

    testImplementation(platform(libs.spring.boot.bom))
    testImplementation(libs.spring.boot.starter.test)
    testRuntimeOnly(libs.junit.platform.launcher)
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

javafx {
    version = libs.versions.javafx.get()
    modules = listOf("javafx.controls", "javafx.fxml")
}

tasks.test {
    useJUnitPlatform()
}

application {
    // Launcher, not FxApp: a main class that extends javafx.application.Application refuses to
    // start when JavaFX is on the classpath rather than the module path, which it is here.
    mainClass = "org.angelscare.management.Launcher"
}

val onWindows = System.getProperty("os.name").lowercase().contains("win")

runtime {
    options.set(listOf("--strip-debug", "--compress", "2", "--no-header-files", "--no-man-pages"))
    // The JDK modules the trimmed runtime keeps. Spring, Hikari and sqlite-jdbc need more than the
    // application code suggests; re-check with `./gradlew suggestModules` after adding dependencies.
    modules.set(listOf(
        "java.base", "java.desktop", "java.instrument", "java.logging", "java.management",
        "java.naming", "java.prefs", "java.scripting", "java.sql", "java.xml",
        "jdk.jfr", "jdk.unsupported", "jdk.crypto.ec"
    ))
    jpackage {
        imageName = "AngelsCare"
        appVersion = project.version.toString()
        // The --win-* switches are rejected outright by jpackage on macOS, so they are applied only
        // on Windows. That keeps `./gradlew jpackage` working on the Mac for local testing.
        if (onWindows) {
            installerType = "exe"
            installerName = "AngelsCare"
            installerOptions = listOf(
                // Install into the user's own profile. No admin rights, no UAC prompt, and no
                // permission problems from writing under Program Files.
                "--win-per-user-install",
                // Without these two, jpackage installs the application and creates no way to start
                // it: no desktop icon and no Start menu entry. The installer appears to do nothing.
                "--win-shortcut",
                "--win-menu",
                "--win-menu-group", "Angels Care",
                "--win-dir-chooser"
            )
        }
    }
}
