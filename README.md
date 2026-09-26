# Mailbox Manager

A desktop app for keeping track of rented mailboxes: who holds each box, how
to reach them, which business names receive mail there, and when each rental
ends. It runs on macOS, Windows, and Linux, and keeps all data on your own
computer.

## Download and run

1. Install Java 17 or newer, for example from [Adoptium](https://adoptium.net/).
2. Download the jar for your computer from the
   [latest release](https://github.com/wgoodzey/mailbox-manager/releases/latest):

   | Computer                              | File                                        |
   | ------------------------------------- | ------------------------------------------- |
   | Windows                               | `mailbox-manager-<version>-windows-x64.jar` |
   | Mac with Apple silicon (M1 and later) | `mailbox-manager-<version>-mac-arm64.jar`   |
   | Mac with Intel                        | `mailbox-manager-<version>-mac-x64.jar`     |
   | Linux                                 | `mailbox-manager-<version>-linux-x64.jar`   |

   Each jar only works on the system it was built for.
3. Double-click the jar, or run it from a terminal:

   ```sh
   java -jar mailbox-manager-<version>-<platform>.jar
   ```

If double-clicking shows "A Java Exception has occurred" or nothing happens,
run the jar from a terminal as above to see the actual error, and check that
`java -version` reports 17 or newer. Double-clicking uses whichever Java your
computer has set up for jar files, which may be an older one.

To upgrade, download the new jar and run it instead of the old one. Your data
is stored separately (see below) and carries over automatically.

## Using the app

The main menu has four screens:

- **Add New Box** – record a new box holder. First name, last name, box number,
  and phone number are required. Each box number can only belong to one holder.
  You can also add a business title, a nickname for the box, an email address,
  any alternate business names (DBAs) that receive mail there, and the date
  the rental ends. The 1/3/6/12 Month buttons set the end date that far from
  today.
- **Manage Boxes** – every box in one table, with a search field. Search
  matches any part of a name, business name, box number, box name, phone
  number, or email, and phone numbers match with or without formatting. Select
  a row to edit or delete it. When editing, the 1/3/6/12 Month buttons extend
  the current end date, which makes renewals quick.
- **Calendar** – a month view with each box shown on the day its rental ends.
  Hover over an entry for the holder's name.
- **Renewals** – boxes that are past due, and boxes due within the next 30 days
  (adjustable in Settings), most urgent first.

### Settings

Open **File → Settings** (⌘, on a Mac, Ctrl+, on Windows and Linux). Settings
opens in its own window, so whatever you were doing stays as it was.

- **Show boxes due within (days)** – how far ahead the Renewals screen looks.
  Default 30.
- **Daily backups to keep** – how many days of backups to keep before the
  oldest are deleted. Default 30.

The settings window also shows where your data is stored.

## Your data and backups

Everything is stored in a single database file, `mailboxes.db`, in this folder:

| System  | Folder                                            |
| ------- | ------------------------------------------------- |
| macOS   | `~/Library/Application Support/MailboxManager`    |
| Windows | `%APPDATA%\MailboxManager`                        |
| Linux   | `~/.mailbox-manager`                              |

Each day, the first time the app starts, it saves a copy of the database to
the `backups` folder inside that folder, named by date
(`mailboxes-2026-09-26.db`). If a backup can't be made, for example because the
disk is full, the app still opens, shows a warning, and tries again the next
time it starts.

The backups are on the same computer as the data, so they won't help if the
computer itself fails. Copy the data folder to a USB drive or cloud storage
from time to time.

### Restoring a backup

1. Quit Mailbox Manager.
2. In the data folder, rename `mailboxes.db` to something like
   `mailboxes-before-restore.db`, so you can go back if needed.
3. Copy the backup you want from `backups` into the data folder and rename the
   copy to `mailboxes.db`.
4. Start the app.

Settings are stored in the same file, so restoring a backup also restores the
settings from that day.

## Development

You need a JDK, version 17 or newer. Maven is included through the wrapper
(`./mvnw`, or `mvnw.cmd` on Windows), so there's nothing else to install.

| Task | Command |
| ---- | ------- |
| Run the app | `./mvnw javafx:run` (or the Run button in your IDE on `App`) |
| Run the tests | `./mvnw test` |
| Build a runnable jar | `./mvnw clean package`, which produces `target/mailbox-manager-<version>-shaded.jar` |

Running the app from source uses your real data folder, so anything you add or
delete while testing changes your real records.

### Tests

Always run tests with `./mvnw test`. Maven points them at a throwaway data
folder under `target/`, and the tests refuse to run anywhere else, which is why
they fail from an IDE's test runner instead of touching your real database.

Some tests open real windows and press buttons and keys in code, so windows
briefly appear on screen while the tests run. They don't move the mouse. In CI
they run under a virtual display.

### Project layout

```
src/main/java/org/lfps/mailboxes/
  App.java          start-up: data folder, database schema, daily backup, main window
  data/             database access: Database, MailboxRepository, SettingsRepository, Setting
  model/            Mailbox
  view/             one class per screen, plus AppWindow (menu bar) and SettingsView
  util/             input validation and runtime info
```

To add a setting, declare it in `data/Setting.java`, add a field for it in
`view/SettingsView.java`, and read it with `SettingsRepository` where it's
used. To add a menu item, edit `buildMenuBar` in `view/AppWindow.java`.

### Releasing

1. Update the version in `pom.xml` and commit it (`Release x.y.z`).
2. Push to `main` and wait for the tests to pass in GitHub Actions.
3. Tag the commit and push the tag:

   ```sh
   git tag vx.y.z
   git push origin vx.y.z
   ```

GitHub Actions then builds a jar on Windows, Linux, and both kinds of Mac, and
attaches them to a GitHub release for the tag. To rebuild the jars for an
existing tag, run the **Build** workflow manually from the Actions tab.
