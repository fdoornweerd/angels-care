package org.angelscare.management;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class AppVersionTest {

    @Test
    @DisplayName("the build's version is known to the app and is one Windows Installer accepts")
    void versionIsAValidInstallerVersion() {
        String version = AppVersion.current();

        // Windows Installer upgrades compare major.minor.build, each a plain number, with major
        // and minor at most 255 and build at most 65535. Anything else breaks the upgrade check.
        assertThat(version).matches("\\d+\\.\\d+\\.\\d+");
        String[] parts = version.split("\\.");
        assertThat(Integer.parseInt(parts[0])).isBetween(0, 255);
        assertThat(Integer.parseInt(parts[1])).isBetween(0, 255);
        assertThat(Integer.parseInt(parts[2])).isBetween(0, 65535);
    }

    @Test
    @DisplayName("CI builds are numbered by the workflow run, so each installer is newer than the last")
    void ciBuildsUseTheRunNumber() {
        String runNumber = System.getenv("GITHUB_RUN_NUMBER");

        assertThat(AppVersion.current())
                .isEqualTo(runNumber == null ? "1.0.0" : "1.0." + runNumber);
    }
}
