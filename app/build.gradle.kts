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
// Every CI build gets a higher version, 1.0.<workflow run number>. Windows Installer only treats a
// new installer as an upgrade of the installed one when its version is higher; with the same
// version every time it refuses ("another version of this product is already installed").
// Local builds are 1.0.0. Windows allows at most 255.255.65535, which AppVersionTest checks.
version = "1.0." + (System.getenv("GITHUB_RUN_NUMBER") ?: "0")

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
    testLogging {
        // Full messages and causes in the console, so a CI failure can be read from the log alone.
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
        events("failed")
    }
    // Unpack sqlite-jdbc's native library somewhere fixed. Left to Bootstrap it lands in each test's
    // @TempDir, and on Windows a loaded DLL cannot be deleted, so JUnit's clean-up fails.
    val sqliteNative = layout.buildDirectory.dir("sqlite-native").get().asFile
    systemProperty("org.sqlite.tmpdir", sqliteNative.absolutePath)
    doFirst { sqliteNative.mkdirs() }
}

// The version, as a resource the app reads at start-up (AppVersion) for the log and the footer.
val writeVersion by tasks.registering {
    val versionFile = layout.buildDirectory.file("generated/version/angels-care-version.properties")
    val appVersion = project.version.toString()
    inputs.property("version", appVersion)
    outputs.file(versionFile)
    doLast {
        versionFile.get().asFile.writeText("version=$appVersion\n")
    }
}
sourceSets.main {
    resources.srcDir(writeVersion.map { it.outputs.files.singleFile.parentFile })
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
        // No --win-upgrade-uuid on purpose: jpackage derives the upgrade code from the vendor and
        // the app name, which the installers already on people's PCs used too. Setting one now
        // would make Windows see a different product and install it alongside the old one. So
        // don't rename the app or set a vendor either.
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
