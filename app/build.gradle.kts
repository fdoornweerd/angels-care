/*
 * Angels Care - JavaFX + SQLite desktop app.
 *
 * Packaging is deliberately parameterised so we can ship several differently-packaged builds of
 * the same code at once and find out which one survives on a locked-down Windows machine:
 *
 *   ./gradlew jpackage -PpackagingVariant=installer          -> .exe installer, per-user, shortcuts
 *   ./gradlew jpackage -PpackagingVariant=installer-console  -> same, but opens a console window
 *   ./gradlew jlinkZip -PpackagingVariant=portable           -> .zip, unzip and run, no installer
 *   ./gradlew jlinkZip -PpackagingVariant=portable-nostrip   -> .zip, unstripped image + bound services
 */

plugins {
    // Apply the application plugin to add support for building a CLI application in Java.
    application
    id("org.openjfx.javafxplugin") version "0.1.0"
    id("org.beryx.jlink") version "3.0.1"
}

group = "org.angelscare"
version = "1.0.0"

repositories {
    // Use Maven Central for resolving dependencies.
    mavenCentral()
}

dependencies {
    // Use JUnit test framework.
    testImplementation(libs.junit)

    // This dependency is used by the application.
    implementation(libs.guava)
    implementation("org.xerial:sqlite-jdbc:3.46.0.0")
}

// Apply a specific Java toolchain to ease working on different environments.
java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

javafx {
    version = "21.0.2"
    modules = listOf("javafx.controls")
}

val packagingVariant = (findProperty("packagingVariant") as String?) ?: "installer"
val wantsConsole = packagingVariant.contains("console")
val wantsStrippedImage = !packagingVariant.contains("nostrip")
val onWindows = System.getProperty("os.name").lowercase().contains("win")

val jlinkOptions = if (wantsStrippedImage) {
    listOf("--strip-debug", "--compress", "2", "--no-header-files", "--no-man-pages")
} else {
    // An unstripped image, plus --bind-services so that jlink pulls in every service provider it
    // can see. sqlite-jdbc publishes its driver as a java.sql.Driver service; if jlink is dropping
    // that binding, this variant is the one that will work.
    listOf("--bind-services")
}

jlink {
    imageZip.set(layout.buildDirectory.file("distributions/AngelsCare-$packagingVariant.zip"))
    options.set(jlinkOptions)
    launcher {
        name = "AngelsCare"
    }
    jpackage {
        installerName = "AngelsCare-$packagingVariant"
        appVersion = project.version.toString()
        // The --win-* switches below are rejected outright by jpackage on macOS, so they are only
        // applied on Windows; that keeps `./gradlew jpackage` usable for local testing on the Mac.
        if (onWindows) {
            installerType = "exe"
            imageOptions = buildList<String> {
                // --win-console makes the launcher a console app, so stdout/stderr and any stack
                // trace stay on screen instead of vanishing with the process.
                if (wantsConsole) add("--win-console")
            }
            installerOptions = listOf(
                // Install under the user's profile: no UAC prompt, no Program Files permissions.
                "--win-per-user-install",
                // Without these two, jpackage installs the app and creates NO way to launch it -
                // no desktop icon, no Start menu entry. That alone looks like "nothing happens".
                "--win-shortcut",
                "--win-menu",
                "--win-menu-group", "Angels Care",
                "--win-dir-chooser"
            )
        }
    }
}

application {
    // Define the main class for the application.
    mainModule = "org.angelscare.management"
    mainClass = "org.angelscare.management.Main"
}
