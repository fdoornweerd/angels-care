/*
 * Angels Care - JavaFX desktop app with a local SQLite database.
 *
 *   ./gradlew run        run it from source
 *   ./gradlew jpackage   build a native installer for the platform you are on
 *
 * The Windows installer is built by CI, not locally - see README.md.
 */

plugins {
    application
    id("org.openjfx.javafxplugin") version "0.1.0"
    id("org.beryx.jlink") version "3.0.1"
}

group = "org.angelscare"
version = "1.0.0"

repositories {
    mavenCentral()
}

dependencies {
    testImplementation(libs.junit)

    implementation(libs.guava)
    implementation("org.xerial:sqlite-jdbc:3.46.0.0")
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

javafx {
    version = "21.0.2"
    modules = listOf("javafx.controls")
}

val onWindows = System.getProperty("os.name").lowercase().contains("win")

jlink {
    options.set(listOf("--strip-debug", "--compress", "2", "--no-header-files", "--no-man-pages"))
    launcher {
        name = "AngelsCare"
    }
    jpackage {
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

application {
    mainModule = "org.angelscare.management"
    mainClass = "org.angelscare.management.Main"
}
