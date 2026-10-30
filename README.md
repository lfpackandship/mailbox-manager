# Mailbox Manager

A desktop app for keeping track of rented mailboxes: who holds each box, how
to reach them, which business names receive mail there, and when each rental
ends. It runs on macOS, Windows, and Linux, and keeps all data on your own
computer.

See the [releases page](https://github.com/lfpackandship/mailbox-manager/releases)
for what's new in each release.

## Installing

Download the installer for your computer from the
[latest release](https://github.com/lfpackandship/mailbox-manager/releases/latest).
Java is built in, so there's nothing else to install.

| Computer                              | File                                            |
| ------------------------------------- | ----------------------------------------------- |
| Windows                               | `mailbox-manager-<version>-windows-x64.msi`     |
| Mac with Apple silicon (M1 and later) | `mailbox-manager-<version>-mac-arm64.dmg`       |
| Mac with Intel                        | `mailbox-manager-<version>-mac-x64.dmg`         |
| Linux                                 | `mailbox-manager-<version>-linux-x64.AppImage`  |

### Windows

Open the `.msi`. It installs for your user account only, so it doesn't need
an administrator password, and adds Mailbox Manager to the Start menu and the
desktop.

The first time, Windows may show "Windows protected your PC", because the
installer isn't signed. Click **More info**, then **Run anyway**.

### Mac

Open the `.dmg` and drag **Mailbox Manager** into the **Applications** folder.

The first time you open it, macOS may say it can't check the app for malicious
software, because the app isn't signed with an Apple developer account. Click
**Done**, then go to **System Settings → Privacy & Security**, scroll down to
the message about Mailbox Manager, and click **Open Anyway**. After that it
opens normally. You may need to do this again after installing a new version.

### Linux

Make the AppImage executable and run it:

```sh
chmod +x mailbox-manager-<version>-linux-x64.AppImage
./mailbox-manager-<version>-linux-x64.AppImage
```

If it won't start because FUSE isn't available (common in containers and some
minimal systems), run it with `--appimage-extract-and-run` added to the end.

### Upgrading

Install the new version the same way. On Windows it replaces the old one; on
a Mac, replace the app in Applications. Your data is stored separately (see
[Your data and backups](docs/user-guide.md#your-data-and-backups)) and
carries over automatically.

### Running the jar instead

Each release also includes a jar for each system (`…-<platform>.jar`), which
needs Java 17 or newer installed; Java 25 from
[Oracle](https://www.oracle.com/java/technologies/downloads/) or
[Adoptium](https://adoptium.net/) is recommended. The Java on java.com is
version 8, which is too old. Run it with `java -jar <file>.jar`, or
double-click it. If double-clicking shows "A Java Exception has occurred",
check that `java -version` reports 17 or newer.

## Using the app

The [user guide](docs/user-guide.md) explains each screen, and where your
data and backups are kept.

## Development

You need a JDK, version 17 or newer. Maven is included through the wrapper
(`./mvnw`, or `mvnw.cmd` on Windows), so there's nothing else to install for
running, testing, or building the jar.

Building an installer uses the `jpackage` tool from that JDK, and bundles that
JDK's Java into the installer (release builds use Java 25). On Windows it also
needs the [WiX Toolset](https://wixtoolset.org/) and a bash shell such as Git
Bash. Each installer can only be built on its own system, so releases are
built by GitHub Actions rather than locally.

| Task | Command |
| ---- | ------- |
| Run the app | `./mvnw javafx:run` (or the Run button in your IDE on `App`) |
| Run the app for debugging | `./mvnw javafx:run@debug`, which waits for a debugger to attach on port 5005 |
| Run the tests | `./mvnw test` |
| Build a runnable jar | `./mvnw clean package`, which produces `target/mailbox-manager-<version>-shaded.jar` |
| Build the installer for this computer | `packaging/package.sh <platform>` (e.g. `mac-arm64`), which writes it to `dist/` |

Running the app from source uses your real data folder, so anything you add or
delete while testing changes your real records.

### Tests

Always run tests with `./mvnw test`. Maven points them at a throwaway data
folder under `target/`, and the tests refuse to run anywhere else, which is why
they fail from an IDE's test runner instead of touching your real database.
On Windows, run `.\mvnw.cmd test`; a Windows-only profile in `pom.xml` also
points `APPDATA`, which the app uses there instead of the home folder, at the
throwaway folder.

Some tests open real windows and press buttons and keys in code, so windows
briefly appear on screen while the tests run. They don't move the mouse. In CI
they run under a virtual display.

The Google Drive tests run against a fake Google server on your computer
(`FakeGoogle`), so they don't need a Google account or the internet.

### Google Drive setup

Connecting Google Drive needs the app's own Google client ID and secret, which
are kept out of the repository. Without them the app works, but Settings says
Google Drive isn't available. To set them up once:

1. In [Google Cloud Console](https://console.cloud.google.com/), create a
   project and enable the **Google Drive API** for it.
2. Under **Google Auth Platform**, fill in the app's name and contact email.
   Choose **Internal** if the account is part of a Google Workspace
   organization; otherwise choose **External** and, when it's ready,
   **Publish** the app. While an External app is still in testing, Google
   signs it out every 7 days.
3. Under **Data Access**, add the scope `.../auth/drive.file` (if it isn't
   listed, enable the Google Drive API first). It's non-sensitive, so
   publishing doesn't need Google's security review. The app asks for only
   this one, so Google's sign-in page has no checkboxes to miss.
4. Under **Clients**, create an OAuth client of type **Desktop app**.
5. For local builds, copy `packaging/google-oauth.properties.example` to
   `src/main/resources/org/lfps/mailboxes/drive/google-oauth.properties` and
   fill in the client ID and secret. For release builds, add them as the
   repository secrets `GOOGLE_CLIENT_ID` and `GOOGLE_CLIENT_SECRET`.

Google treats a desktop app's client secret as not truly secret, since anyone
can pull it out of the app; it's kept out of the repository only so that
copies of the code don't use your Google project.

### Project layout

```
src/main/java/org/lfps/mailboxes/
  App.java          start-up: data folder, database schema, daily backup, main window
  data/             database access: Database, MailboxRepository, RentalHistoryRepository,
                    BoxInventoryRepository, PriceRepository, SettingsRepository, Setting,
                    LabelRepository (forwarding label numbers and records)
  drive/            Google Drive backups: DriveBackup (connect, upload, download),
                    GoogleDrive (talks to Google), DriveAccount (the saved sign-in)
  importing/        Import Boxes: ImportTemplate (the .xlsx template), ImportReader (reads .xlsx
                    and .csv), ImportCheck (checks each row), ImportRow, and ImportColumn
  model/            Mailbox, ForwardingAddress, RentalPeriod, InventoryBox, PrintedLabel,
                    DepositOutcome, DatedNote, and MailHold
  view/             one class per screen, plus AppWindow (menu bar, text size), SettingsView,
                    GoogleDriveRow, RestoreView, PriceSheet (the printed price sheet and
                    reminders), ForwardingLabel and ForwardingLabelView (forwarding labels),
                    DatedNotesEditor and MailHoldEditor (sections of Edit Box),
                    KeyboardShortcutsView, Printing, and TableOutput (printing and saving lists)
  util/             input validation, box number sorting, money, rental lengths, error
                    messages, and runtime info
  Launcher.java     the entry point of the jar and installers, which hands off to App
packaging/
  package.sh        builds the installer for the current system with jpackage
  icons/            app icon: icon.svg is the source; the .png, .ico and .icns are made from it
```

To add a setting, declare it in `data/Setting.java`, add a field for it in
`view/SettingsView.java`, and read it with `SettingsRepository` where it's
used. To add a menu item, edit `buildMenuBar` in `view/AppWindow.java`.

### Issues and branches

Work is planned in GitHub issues. Each issue gets its own branch, created
from the issue's page (Development → Create a branch) or with `gh issue
develop <number>`, and comes back to `main` through a pull request that's
reviewed and merged on GitHub. Nothing is committed to `main` directly
except the release commit. Once a pull request is merged, its branch is
deleted.

Instructions for AI coding agents, including these rules, are in
[AGENTS.md](AGENTS.md). Claude Code reads them through `CLAUDE.md`.

### Releasing

A release is made when it's ready, from whatever has been merged into
`main` by then.

1. Update the version in `pom.xml` and commit it (`Release x.y.z`). Versions
   must be three numbers, x.y.z, because the macOS installer accepts nothing
   else.
2. Push to `main` and wait for the tests to pass in GitHub Actions.
3. Tag the commit and push the tag. The tag must match the version in
   `pom.xml`, or the build stops before releasing anything.

   ```sh
   git tag -a vx.y.z -m "Mailbox Manager x.y.z"
   git push origin vx.y.z
   ```

GitHub Actions then builds the installers and jars on Windows, Linux, and both
kinds of Mac, installs and starts each app to check that it launches, and
attaches them all to a GitHub release for the tag. The release notes are
generated from commit titles; edit the release on GitHub to replace them with
a friendlier summary of what's new.

From the Actions tab you can also run the **Build** workflow by hand. With the
tag left empty it builds the installers without releasing them, so you can
download them from the run and try them out first. With an existing tag
entered, it rebuilds that tag's release files.

The installers aren't code-signed, which is why users see a one-time warning
on Windows and macOS. Signing needs an Apple Developer account for macOS and a
code-signing certificate for Windows.
