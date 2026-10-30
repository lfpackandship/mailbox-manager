# Mailbox Manager

A desktop app for keeping track of rented mailboxes: who holds each box, how
to reach them, which business names receive mail there, and when each rental
ends. It runs on macOS, Windows, and Linux, and keeps all data on your own
computer.

- Add, edit, renew, close, and search boxes, with each box's rental history.
- See what's due on **Renewals** and the **Calendar**, and print renewal
  reminders and the price sheet.
- Track payments, key deposits, mail holds, dated notes, and forwarding
  addresses, and print forwarding labels.
- Keep a box inventory with sizes and prices, including price changes from a
  later date.
- Import boxes from a spreadsheet, and print or save any list as one.
- Back up every day, to a second folder and to Google Drive if you like.

The [user guide](docs/user-guide.md) explains each screen, and where your data
and backups are kept. See the
[releases page](https://github.com/lfpackandship/mailbox-manager/releases) for
what's new in each release.

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

The installers aren't signed, so the first time you open the app:

- **Windows:** if "Windows protected your PC" appears, click **More info**,
  then **Run anyway**.
- **Mac:** drag the app into **Applications**. If macOS says it can't check
  it, click **Done**, then go to **System Settings → Privacy & Security** and
  click **Open Anyway**. You may need to do this again after an upgrade.
- **Linux:** make the AppImage executable (`chmod +x`) and run it. If FUSE
  isn't available, add `--appimage-extract-and-run`.

To upgrade, install the new version the same way. Your data carries over
automatically. Each release also has jars for running the app with your own
Java; see [Running the jar instead](docs/user-guide.md#running-the-jar-instead).

## Development

You need a JDK, version 17 or newer. Maven comes with the wrapper (`./mvnw`,
or `mvnw.cmd` on Windows).

| Task | Command |
| ---- | ------- |
| Run the app | `./mvnw javafx:run` |
| Run it for debugging | `./mvnw javafx:run@debug` (waits for a debugger on port 5005) |
| Run the tests | `./mvnw test` |
| Build a runnable jar | `./mvnw clean package` (writes `target/mailbox-manager-<version>-shaded.jar`) |
| Build this computer's installer | `packaging/package.sh <platform>`, such as `mac-arm64` (writes to `dist/`) |

Running the app from source uses your real data folder, so anything you change
while trying it out changes your real records.

Installers are built with the JDK's `jpackage`, which bundles that JDK's Java
(release builds use Java 25); on Windows it also needs the
[WiX Toolset](https://wixtoolset.org/) and a bash shell such as Git Bash. Each
installer can only be built on its own system, so releases are built by
GitHub Actions.

### Tests

Always run the tests with `./mvnw test` (`.\mvnw.cmd test` on Windows). Maven
points them at a throwaway data folder under `target/`, and they refuse to run
anywhere else, which is why they fail from an IDE's test runner instead of
touching your real database. Some tests open windows and press keys, so they
need a display; CI uses a virtual one. The Google Drive tests use a fake
Google server (`FakeGoogle`), so they need no account or internet.

### Google Drive setup

Google Drive backups need the app's own Google client ID and secret, which are
kept out of the repository. Without them the app works, but Settings says
Google Drive isn't available. To set them up once:

1. In [Google Cloud Console](https://console.cloud.google.com/), create a
   project and enable the **Google Drive API**.
2. Under **Google Auth Platform**, fill in the app's name and contact email.
   Choose **Internal** for a Google Workspace organization; otherwise
   **External**, and **Publish** it when ready (while it's in testing, Google
   signs it out every 7 days).
3. Under **Data Access**, add the scope `.../auth/drive.file`, the only one
   the app asks for. It's non-sensitive, so it needs no security review.
4. Under **Clients**, create an OAuth client of type **Desktop app**.
5. For local builds, copy `packaging/google-oauth.properties.example` to
   `src/main/resources/org/lfps/mailboxes/drive/google-oauth.properties` and
   fill it in. For release builds, add the repository secrets
   `GOOGLE_CLIENT_ID` and `GOOGLE_CLIENT_SECRET`.

Google doesn't treat a desktop app's secret as truly secret; it's kept out of
the repository so that copies of the code don't use your Google project.

### Project layout

```
src/main/java/org/lfps/mailboxes/
  App.java        start-up: data folder, database schema, backups, main window
  Launcher.java   entry point of the jar and installers, which hands off to App
  data/           database access, one repository per kind of record
  drive/          Google Drive backups
  importing/      Import Boxes: the template, reading .xlsx and .csv, checking rows
  model/          boxes, rentals, addresses, labels, notes, and mail holds
  view/           one class per screen, plus the menu bar, Settings, and printing
  util/           validation, box number sorting, money, and error messages
packaging/        package.sh builds the installer; icons/ has the app icon
docs/             the user guide
```

To add a setting, declare it in `data/Setting.java`, add a field for it in
`view/SettingsView.java`, and read it with `SettingsRepository`. To add a menu
item, edit `buildMenuBar` in `view/AppWindow.java`.

### Issues and branches

Work is planned in GitHub issues. Each issue gets its own branch, created from
the issue's page (Development → Create a branch), and comes back to `main`
through a pull request reviewed and merged on GitHub. Only the release commit
goes straight to `main`. Instructions for AI coding agents are in
[AGENTS.md](AGENTS.md), which Claude Code reads through `CLAUDE.md`.

### Releasing

A release is made when it's ready, from whatever has been merged into `main`.

1. Set the version in `pom.xml` and commit it as `Release x.y.z`. It must be
   three numbers, because the macOS installer accepts nothing else.
2. Push to `main` and wait for the tests to pass in GitHub Actions.
3. Tag the commit and push the tag, which must match `pom.xml`:

   ```sh
   git tag -a vx.y.z -m "Mailbox Manager x.y.z"
   git push origin vx.y.z
   ```

GitHub Actions then builds the installers and jars on each system, checks
that each one starts, and attaches them to a GitHub release for the tag.
Replace its generated notes with a friendlier summary of what's new. Running
the **Build** workflow by hand from the Actions tab builds the installers
without releasing them, to try out first, or rebuilds an existing tag's files.
