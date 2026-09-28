# Angels Care

A JavaFX + Spring Boot desktop application with a local SQLite database. Developed on macOS, deployed to Windows
as a native installer.

---

## Running on Windows

The application is distributed as a single installer file. Nothing else needs to be installed first
— Java is bundled inside the application, so there is no separate JDK or JRE to set up.

### Installing

1. Download the installer, e.g. `AngelsCare-1.0.57.exe` (the last number goes up with every build).
2. Double-click it. Windows will probably show a blue box reading **"Windows protected your PC"**.
   Click **More info**, then **Run anyway**. This appears because the installer is not code-signed,
   not because anything is wrong with it.
3. Click through the installer. It installs into your own user profile, so it does **not** ask for
   an administrator password.
4. When it finishes, there is an **Angels Care** icon on the desktop and an entry under
   **Start menu → Angels Care**.

### Running

Double-click the desktop icon. The window opens on the list of school years; its footer shows the
version, e.g. "Version 1.0.57".
If anything goes wrong during start-up, a window titled "Something went wrong" appears instead,
naming the log file to send (see below). On the very first run the database is created, so that run
takes slightly longer.

### Updating to a new version

Run the new installer the same way. It replaces the program and keeps your data: the database is
not part of the installation (see [Where your data lives](#where-your-data-lives)), and the new
version brings it up to date the first time it starts. An installer is only accepted as an update
if its version is higher than the one installed.

### Uninstalling

**Settings → Apps → Installed apps → AngelsCare → Uninstall.** Your data is not removed — see
[Where your data lives](#where-your-data-lives).

### If nothing happens when you run it

Every launch writes a log file *before* the window appears, so it exists even when nothing is drawn
on screen:

```
C:\Users\<your name>\AngelsCareData\angels-care-startup.log
```

To find it: open File Explorer, click the address bar, type `%USERPROFILE%\AngelsCareData` and press
Enter. That file records the Java version, the paths in use, and the full error. Send it to whoever
is maintaining the app.

One known cause of "the installer runs but nothing opens": an **older version installed
system-wide**. Uninstall any previous AngelsCare from Settings → Apps first, then reinstall.

---

## Where your data lives

The database is a single file in your home folder, created on first launch:

| Platform | Location |
|---|---|
| Windows | `C:\Users\<name>\AngelsCareData\angels-care.db` |
| macOS | `/Users/<name>/AngelsCareData/angels-care.db` |

This is deliberately **outside** the install directory, so upgrading or uninstalling never destroys
data. Two things follow from that:

- **Back up that file** to back up your data. Copying it is enough.
- **Nothing is shared.** There is no server. Each machine has its own database, and one person's
  entries are not visible to anyone else.

---

## Development (macOS)

Requires JDK 21. Gradle downloads a matching toolchain automatically if you do not have one.

```bash
./gradlew run                   # run from source
./gradlew test                  # run the tests
./gradlew jpackageImage         # build AngelsCare.app on the Mac, to check packaging locally
./gradlew jpackage              # build a native installer for the machine you are on
```

New features are written spec-first and test-first: see `CLAUDE.md` and `docs/specs/`.

### Building the Windows installer

The Windows installer **cannot be built on the Mac** — `jpackage` produces packages only for the
platform it runs on. It is built by GitHub Actions instead:

1. Push to `main`, or go to **Actions → Build Windows Installer → Run workflow**.
2. Wait for the run to finish (roughly 5–10 minutes).
3. Open the run and download the **`angels-care-windows-installer`** artifact from the Artifacts box
   at the bottom.
4. GitHub wraps artifacts in a zip, so unzip it once to get `AngelsCare-1.0.<run>.exe`, where
   `<run>` is the workflow run's number. That `.exe` is the file to send to users. Builds made on
   your own machine are always `1.0.0`, lower than any CI build, so don't send those.

Artifact downloads require repository access, so to give the installer to someone outside the repo,
download it yourself and pass the file along.

---

## How it is packaged

Three distinct things get built, and only the last is what users receive:

1. **Runtime image** — a trimmed-down JVM (built by jlink via the `org.beryx.runtime` plugin) plus
   the application and its libraries, in a folder.
2. **App image** — the same folder with a real `AngelsCare.exe` launcher. Runs in place; installs
   nothing.
3. **Installer** (`AngelsCare-1.0.<run>.exe`) — wraps the app image so it can be copied into place, with
   shortcuts and an uninstaller registered.

Only #3 is distributed. Note that running the installer is not the same as running the application:
the installer puts the app on the machine, and the desktop shortcut starts it.

Two settings in `app/build.gradle.kts` matter more than they look:

- `--win-shortcut` and `--win-menu` create the desktop icon and Start menu entry. Without them
  `jpackage` installs the application and provides no way to launch it, which looks exactly like the
  installer having done nothing at all.
- `--win-per-user-install` installs into the user's profile rather than `Program Files`, which
  avoids both the UAC prompt and permission problems writing to the install directory.

---

## Project layout

```
app/src/main/java/org/angelscare/management/
    Launcher.java               entry point; starts the log, then JavaFX
    FxApp.java                  JavaFX application; shows the main window or an error window
    Bootstrap.java              starts Spring against the data folder; never throws
    AngelsCareApplication.java  Spring configuration root
    Diagnostics.java            start-up logging, for diagnosing failures on machines you cannot access
    common/                     shared building blocks: Ugx (money), Ids, Clock
    db/                         SQLite DataSource, device identity
    shell/ui/                   main window controller
app/src/main/resources/
    db/migration/               Flyway migrations (V1__baseline.sql, ...)
    fxml/                       JavaFX views, one folder per feature
app/src/test/java/.../support/DatabaseTest.java   base class: a fresh migrated SQLite DB per test
docs/specs/                     feature specs (TEMPLATE.md + NNN-name.md)
.claude/                        Claude Code skills (/spec, /tdd), settings, test hook
.github/workflows/build-windows.yml   runs tests on Windows for PRs; builds the installer on main
```
