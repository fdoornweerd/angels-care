# Testing the Windows builds

The CI workflow (`.github/workflows/build-windows.yml`) produces **four** artifacts from the same
source, each packaged differently. Run it from the Actions tab ("Build Windows Installer" ->
"Run workflow"), then download the artifacts and hand them over one at a time.

| Artifact | What it is | How to run it |
|---|---|---|
| `angels-care-portable` | Zipped runtime image. No installer at all. | Extract the zip, open the folder, double-click **`Start Angels Care`** |
| `angels-care-portable-nostrip` | Same, built from an unstripped image with `--bind-services` | Same as above |
| `angels-care-installer` | `.exe` installer, per-user, creates shortcuts | Run it, then use the **desktop icon** or Start menu -> Angels Care |
| `angels-care-installer-console` | Same installer, app runs with a console window attached | Same, but a black console window stays open showing any error |

Both `installer` artifacts also contain an **`AngelsCare\` folder** alongside the installer `.exe`.
That folder is the app image: a real `AngelsCare.exe` that runs in place, with nothing to install.
It is the same program the installer would have installed, so it is worth trying on its own.

## The three things jpackage and jlink produce

They are a chain, and only the last one is an installer:

1. **jlink image** (`portableZip` -> `AngelsCare-portable.zip`) - a stripped-down JVM plus the app's
   modules, in a plain folder. Started by `Start Angels Care.bat` at the top of the extracted folder.
   No Windows integration whatsoever.
2. **app image** (`jpackageImage` -> `build/jpackage/AngelsCare/`) - the same folder, but with a real
   `AngelsCare.exe` launcher in place of the `.bat`. Still nothing to install.
3. **installer** (`jpackage` -> `build/jpackage/AngelsCare-*.exe`) - wraps the app image in a
   Windows installer that copies it into place, writes registry entries, creates the shortcuts and
   registers an uninstaller.

The `.exe` that kept flashing and disappearing was #3. Running an installer is not running the app.

**Try `portable` first.** It removes the installer, UAC, Program Files and the Start menu from the
equation entirely, so if it works we know the problem was packaging, not the code.

None of these can be sent as a single loose file except the two installers. The `.bat` launcher and
the app image's `AngelsCare.exe` are both only a few kilobytes and do nothing without the ~200 MB
folder of JVM files beside them - they have to travel as the whole zip.

## Before installing either `.exe`

Uninstall any previous "AngelsCare" from Settings -> Apps -> Installed apps. The old build was a
per-machine install; the new ones are per-user, and having both around causes confusing behaviour.

## The log file

Every build now writes `C:\Users\<name>\AngelsCareData\angels-care-startup.log` on every launch,
before the window appears. If nothing at all shows up on screen, **that file is the thing to send
back** — it records the Java version, the paths, whether the SQLite driver class loaded, whether the
native library is present in the image, and the full stack trace of each failed connection attempt.

The same text is also shown inside the app window, so a screenshot works too.

---

# Cleaning up once a variant works

The whole point of the four variants is to be thrown away. The startup log names the answer
directly - look for the line beginning `Connected via`, e.g.:

```
Connected via DriverManager (ServiceLoader); rows = 3
```

## Step 1: collapse the packaging

In `app/build.gradle.kts`, `packagingVariant` drives everything. Delete the branching and keep only
what the working variant used:

| Variant that worked | What that proves | What to keep |
|---|---|---|
| `portable` | The runtime image was always fine; the **installer** was the problem | Keep the default `jlinkOptions` (stripped). Keep the `installerOptions` block - `--win-shortcut` / `--win-menu` were the missing piece. Delete the `nostrip` branch. |
| `portable-nostrip` but not `portable` | `--strip-debug` / `--compress 2` were breaking the image | Make `listOf("--bind-services")` the only `jlinkOptions`. Delete the stripped branch. |
| `installer` | Shortcuts + per-user install were the fix | Hardcode `installerOptions`, delete `packagingVariant`, `wantsConsole` and `wantsStrippedImage`. |
| `installer-console` but not `installer` | Something still fails, and only the console reveals it | Do **not** clean up yet - read the console output first. |

Then drop the matrix in `.github/workflows/build-windows.yml` back to a single job with the one
task, and delete this file.

## Step 2: collapse the database code

`Database.testConnection()` tries four strategies so that one of them survives. Once the log says
which one connects, delete the other three and keep that single code path. Specifically:

- **Strategy 1 (`DriverManager`)** won - delete strategies 2-4 and the `newDriver()` helper. The
  original code was correct; the packaging was the bug.
- **Strategy 2, 3 or 4** won - keep that one as the only implementation. It means `ServiceLoader`
  driver discovery does not survive this packaging, so the explicit path is permanent, not a
  workaround to remove later.

Keep in all cases, regardless of which strategy won:

- the `org.sqlite.tmpdir` redirect in the `static` block (unpacking the native `.dll` into the app's
  own directory instead of the system temp dir)
- `catch (Throwable ...)` rather than `catch (SQLException ...)`, so an `UnsatisfiedLinkError` is
  reported instead of vanishing
- **no** rethrowing `static` initializer - that is what turned a database error into a blank screen

## Step 3: decide about the logging

`Diagnostics` and the error-reporting window in `Main` are independent of the fix. Worth keeping
while the app is still being handed to other people's machines; the log file is the difference
between "it doesn't work" and a diagnosis. Trim `logEnvironment()` down, or delete the class and
restore the simple `Label`, once the app is stable.
