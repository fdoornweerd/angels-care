# Testing the Windows builds

The CI workflow (`.github/workflows/build-windows.yml`) produces **four** artifacts from the same
source, each packaged differently. Run it from the Actions tab ("Build Windows Installer" ->
"Run workflow"), then download the artifacts and hand them over one at a time.

| Artifact | What it is | How to run it |
|---|---|---|
| `angels-care-portable` | Zipped runtime image. No installer at all. | Unzip, open `image\bin\`, double-click **`AngelsCare.bat`** |
| `angels-care-portable-nostrip` | Same, built from an unstripped image with `--bind-services` | Same as above |
| `angels-care-installer` | `.exe` installer, per-user, creates shortcuts | Run it, then use the **desktop icon** or Start menu -> Angels Care |
| `angels-care-installer-console` | Same installer, app runs with a console window attached | Same, but a black console window stays open showing any error |

**Try `portable` first.** It removes the installer, UAC, Program Files and the Start menu from the
equation entirely, so if it works we know the problem was packaging, not the code.

## Before installing either `.exe`

Uninstall any previous "AngelsCare" from Settings -> Apps -> Installed apps. The old build was a
per-machine install; the new ones are per-user, and having both around causes confusing behaviour.

## The log file

Every build now writes `C:\Users\<name>\AngelsCareData\angels-care-startup.log` on every launch,
before the window appears. If nothing at all shows up on screen, **that file is the thing to send
back** — it records the Java version, the paths, whether the SQLite driver class loaded, whether the
native library is present in the image, and the full stack trace of each failed connection attempt.

The same text is also shown inside the app window, so a screenshot works too.
