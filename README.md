# Mailbox Manager

A desktop app for keeping track of rented mailboxes: who holds each box, how
to reach them, which business names receive mail there, and when each rental
ends. It runs on macOS, Windows, and Linux, and keeps all data on your own
computer.

See [CHANGELOG.md](CHANGELOG.md) for what's new in each release.

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
below) and carries over automatically.

### Running the jar instead

Each release also includes a jar for each system (`…-<platform>.jar`), which
needs Java 17 or newer installed; Java 25 from
[Oracle](https://www.oracle.com/java/technologies/downloads/) or
[Adoptium](https://adoptium.net/) is recommended. The Java on java.com is
version 8, which is too old. Run it with `java -jar <file>.jar`, or
double-click it. If double-clicking shows "A Java Exception has occurred",
check that `java -version` reports 17 or newer.

## Using the app

The main menu has six screens:

- **Add New Box** – record a new box holder. Only the box number is required,
  and each box number can only belong to one holder at a time. You can also
  add the holder's first and last name, a business title, a nickname for the
  box, a phone number, an email address, any alternate business names (DBAs)
  that receive mail there, the date the rental ends, the amount paid and how,
  any forwarding addresses, how many keys you gave out and the key deposit,
  and notes. The rental length buttons (1, 3, 6, and 12 months unless changed
  in Settings) set the end date that far from today. If the
  [box inventory](#box-inventory) is set up, **Choose…** next to the box number
  lists the empty boxes to pick from.
- **Manage Boxes** – the open boxes in one table showing each box number,
  holder's name, business title, and phone, with a search field. Search
  matches any part of a name, business name, box number, box name, phone
  number, email, forwarding address (including its note), or the notes, and
  phone numbers match with or without formatting. To see everything recorded
  for a box, including its rental history, double-click it, or select it and
  click **View** or press Enter; the details open in their own window with
  **Edit** and **Renew…** buttons. Select a row to edit, renew, close, or
  delete it. The menu next to the search field shows closed boxes instead, or
  all boxes. **Print…** prints the list as shown, and **Save as
  Spreadsheet…** saves it as a file Excel or Google Sheets opens, with
  everything recorded for each box.
- **Renewals** – open boxes that are past due, and those due within the next
  30 days (adjustable in Settings), most urgent first. Select a box and click
  **Renew…** to renew it, or double-click it, or select it and click **View**
  or press Enter, to see its full details. **Print Reminders…** prints a
  [renewal reminder](#renewal-reminders-and-the-price-sheet) for each box to
  put in it, and **Print List…** and **Save as Spreadsheet…** print or save
  the lists.
- **Calendar** – a month view with each open box shown on the day its rental
  ends. Today is outlined in blue. A day with more than two boxes shows the
  first and how many more, such as "+3 more". Hover over an entry for the holder's name. Click a highlighted day to
  list all the boxes ending that day, with each holder's name, business, and phone, and buttons to view or
  renew them.
- **Payments** – every rental and renewal recorded between two dates, with the
  total paid. It starts on this month; **Last Month** and **This Year** are a
  click away, or choose any dates and click **Show**. **Print…** and **Save as
  Spreadsheet…** print or save the list, for example for whoever does the
  books. Key deposits aren't counted as paid, since they're given back; the
  total held for open boxes is shown underneath.
- **Box Inventory** – every box you have, and whether it's rented or empty.
  See [Box inventory](#box-inventory).

### The menu bar

The menus at the top of the window are there on every screen:

- **File** – **Print Price Sheet…** (⌘P on a Mac, Ctrl+P on Windows and
  Linux), **Print Renewal Reminders…** for every box past due or due soon,
  **Back Up Now…**, **Restore a Backup…**, **Settings**, and **Exit**.
- **Go** – any screen, without going back to the main menu first: **Main
  Menu** (⌘0 or Ctrl+0), **Add New Box** (⌘N or Ctrl+N), **Manage Boxes**,
  **Renewals**, **Calendar**, **Payments**, and **Box Inventory** (⌘1 to ⌘5,
  or Ctrl+1 to Ctrl+5), and the **Prices…** window.
- **Help** – **Show Data Folder**, and **About Mailbox Manager**, which says
  which version is installed.

### Renewing a box

Select a box on Renewals or Manage Boxes, or open its details, and click
**Renew…**. The renewal starts from the day the rental ends, so no days are
lost or given away; change **Renew from** if you'd rather start from today,
for example for a box that lapsed long ago. Click a rental length button, or
choose the new end date, then enter the amount paid, how it was paid, and a
note such as a check number (all optional), and click **Renew**. The box's end
date moves, and the renewal is added to its rental history, which shows in its
details and on Payments.

Renting a box on Add New Box with an end date also starts its rental history,
with the amount paid if entered.

If a renewal was recorded by mistake, select it on Payments and click
**Delete Entry**. That leaves the box's end date as it is; edit the box to
change it. Editing a box's end date directly doesn't add anything to its
history.

### Renewal reminders and the price sheet

To remind box holders to renew, click **Print Reminders…** on Renewals. Each
box that's past due or due soon gets a page to put in its box: the price
sheet, headed with the box number, the holder's name, and the day the rental
ends, with that date and the box's size circled in the table of prices. Untick
any boxes you don't want reminders for, click a box to preview its page, and
click **Print**, or **Save as PDF…** to save them as a PDF file instead, which
needs no printer. To print one for a single box, open its details and click
**Print Reminder…**.

Printing anything in the app opens your computer's own Print window, the same
one other programs use, to choose the printer and the number of copies. On a
Mac it also shows a preview, and its PDF button saves a PDF instead. A printer
has to be set up first, even to save a PDF; if there isn't one, the computer
says how to add one.

The plain price sheet, for handing to new customers, is printed or saved as a
PDF with **Print Price Sheet…** on the Prices window or in Settings. Its prices always match
the [price table](#prices), with a column for each size and a row for each
rental length that has a price.

The shop's name, address, and phone number, the text above and below the
prices, and the reminder's message are set in Settings under
[Price Sheet and Reminders](#settings). What each size measures is entered on
the Prices window.

### Keys and deposits

Add New Box and Edit Box have a place for how many keys the holder was given
and the refundable deposit paid for them. Typing the number of keys fills in
the deposit, at the **Key deposit per key** set in Settings ($10.00 unless
changed); you can still change it. A box's details show its keys, and closing
a box reminds you to collect them and give back the deposit.

### Closing a box

When a holder gives up their box, select it on Manage Boxes and click
**Close Box**. It disappears from Manage Boxes, Renewals, and the Calendar,
and its number can be given to someone else, but everything recorded for it
is kept: choose **Closed boxes** from the menu next to the search field to
find it. To bring it back, select it there and click **Reopen**. That only
works if its number hasn't been given to someone else since; if it has, edit
the closed box's number first.

**Delete** erases a box and its history for good. Use it for boxes entered by
mistake.

### Box inventory

The Box Inventory screen lists every physical box, whether it's rented and to
whom, and its size if you record one. To set it up, type the box numbers under
**Add boxes**, as single numbers and ranges separated by commas (such as
`1-200, 301-310, 12A`), with an optional size such as Small or Large for all of
them, and click **Add Boxes**. Add each size separately to record sizes, or
select boxes and click **Set Size…** later. If some boxes are already rented
but not in the inventory, the screen offers to add them.

Once the inventory has any boxes in it:

- Add New Box and Edit Box only accept box numbers in the inventory, which
  catches typos.
- **Choose…** on Add New Box lists the empty boxes, with their sizes.

Removing a box from the inventory doesn't affect anyone renting it.

When boxes have sizes, the screen also shows how many of each size are empty,
such as `Large: 2 of 5 empty · Small: 30 of 35 empty`.

### Prices

Click **Prices…** on the Box Inventory screen, or next to **Prices for each
size** in Settings, to set what a box costs. There's a row for each size in
the inventory and a column for each rental length, plus a **Default** row for
boxes with no size, or whose size has no price for that length. Leave a price
blank if there isn't one, and click **Save**. The **Measures** column is for what each size measures, such
as `3¾" x 5" x 14"`, which is shown on the printed price sheet.

When you click a rental length button on Add New Box or when renewing, the
price for that box's size and length is filled in as the amount paid. You can
still change it; an amount you type in isn't replaced if you then pick another
length. On Add New Box the price is filled in once both the box number and the
length are chosen. Prices apply only when you use the rental length buttons,
not when you pick an end date yourself.

### Forwarding addresses

If a box holder wants mail forwarded, add one or more forwarding addresses on
Add New Box or Edit Box: fill in the street, city, two-letter state, and ZIP
code (5 digits or ZIP+4), plus an optional apartment or suite and a note such
as "summer" or "office", then click **Add Address**. A box can have several
addresses; the notes help staff pick the right one.

### Settings

Open **File → Settings** (⌘, on a Mac, Ctrl+, on Windows and Linux). Settings
opens in its own window, so whatever you were doing stays as it was. Changes
take effect when you click **Save**.

- **Text size** – Normal, Large, or Extra large, for the whole app. Larger
  text applies right away; restart the app to also enlarge its window to match.
- **Rental lengths (months)** – the quick-set buttons on Add New Box, Edit
  Box, and Renew, such as `1, 3, 6, 12`. Up to six lengths, each from 1 to 120
  months.
- **Prices for each size** – opens the [price table](#prices).
- **Key deposit per key** – fills in the key deposit on Add New Box and Edit
  Box. Leave it blank if you don't take a deposit.
- **Price Sheet and Reminders** – the shop's name, its address and phone, the
  text above the prices (start a line with `-` to make it a bullet point), the
  text below them, and the message on renewal reminders. They start out as on
  the shop's printed price sheet. **Print Price Sheet…** prints it.
- **Week starts on** – Sunday or Monday, for the Calendar.
- **Show boxes due within (days)** – how far ahead the Renewals screen looks.
  Default 30.
- **Daily backups to keep** – how many daily backups to keep before the oldest
  are deleted. Default 30.
- **Also copy backups to** – see [A second copy of your backups](#a-second-copy-of-your-backups).
- **Google Drive** – see [Backing up to Google Drive](#backing-up-to-google-drive).

The Backups section also has **Back Up Now…**, **Restore…**, and an **Open**
button that shows the data folder in Explorer or Finder.

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

### A second copy of your backups

The daily backups are on the same computer as the data, so they won't help if
the computer itself fails. To keep copies somewhere else:

- **Automatically:** in Settings, next to **Also copy backups to**, click
  **Choose…** and pick a folder on a USB drive, a network drive, or a synced
  folder such as OneDrive or Dropbox, then click **Save**. Each daily backup is
  copied there too, and old copies are deleted the same way. If the folder
  isn't available when the app starts (for example, the USB drive is
  unplugged), the app shows a warning and tries again next time.
- **Whenever you like:** click **Back Up Now…** and pick a folder. It saves a
  backup named with the date and time, such as
  `mailboxes-backup-2026-09-26-143005.db`. These are never deleted
  automatically.

### Backing up to Google Drive

The app can also put a copy of each daily backup in your Google Drive, so your
data is safe even if the computer is lost or broken. To turn it on:

1. Open **File → Settings** and, next to **Google Drive**, click
   **Connect Google Drive**.
2. Your web browser opens Google's sign-in page. Sign in to the Google account
   you want the backups in, and click **Allow**.
3. Go back to the app. It shows "Backing up to Google Drive as" and your
   email address, and uploads today's backup straight away.

After that, each day's backup is uploaded when the app starts, to a folder in
your Google Drive called **Mailbox Manager Backups**. The app deletes old
backups there the same way it does on the computer, keeping as many as
**Daily backups to keep** says. The app can only see the files it puts in your
Google Drive, not anything else there.

Settings shows when the last backup was uploaded. If an upload fails, for
example because the internet is down, the app shows a warning and tries again
the next time it starts; the backup is still saved on the computer. If Google
ever signs the app out, the warning has a **Reconnect** button that takes you
through signing in again.

To stop, click **Disconnect** in Settings. Backups already in your Google
Drive are kept. To use a backup from Google Drive, see
[Restoring a backup](#restoring-a-backup).

### Restoring a backup

1. Open **File → Settings** and click **Restore…**.
2. Pick a backup from the list, or click **Choose File…** to use one saved
   somewhere else, such as a USB drive. If Google Drive is connected,
   **Google Drive…** lists the backups there; pick one and click **Download
   and Restore**. Downloaded backups are kept in the `from-google-drive`
   folder inside the `backups` folder.
3. Confirm. The app saves your current data as a backup first (listed as
   "Data saved before a restore"), so you can undo the restore by restoring
   that.

Settings are stored in the same file as the data, so restoring a backup also
brings back the settings from that day. The Google Drive sign-in is the
exception: it's kept separately, in `google-drive.properties` in the data
folder, so it isn't included in backups and restoring doesn't change it. Backups from older versions of the app
are upgraded automatically when restored.

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
                    BoxInventoryRepository, PriceRepository, SettingsRepository, Setting
  drive/            Google Drive backups: DriveBackup (connect, upload, download),
                    GoogleDrive (talks to Google), DriveAccount (the saved sign-in)
  model/            Mailbox, ForwardingAddress, RentalPeriod, and InventoryBox
  view/             one class per screen, plus AppWindow (menu bar, text size), SettingsView,
                    GoogleDriveRow, RestoreView, PriceSheet (the printed price sheet and
                    reminders), Printing, and TableOutput (printing and saving lists)
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

### Branches

Work for each version goes on its own branch, named for that version, such
as `1.7-features` or `1.6.1-features`, and is merged into `main` when it's
ready to release. Nothing is committed to `main` directly. Once a version is
released and its branch is fully merged, the branch is deleted.

Instructions for AI coding agents, including these rules, are in
[AGENTS.md](AGENTS.md). Claude Code reads them through `CLAUDE.md`.

### Releasing

1. Update the version in `pom.xml` and commit it (`Release x.y.z`). Versions
   must be three numbers, x.y.z, because the macOS installer accepts nothing
   else.
2. Add the release to [CHANGELOG.md](CHANGELOG.md).
3. Push to `main` and wait for the tests to pass in GitHub Actions.
4. Tag the commit and push the tag. The tag must match the version in
   `pom.xml`, or the build stops before releasing anything.

   ```sh
   git tag -a vx.y.z -m "Mailbox Manager x.y.z"
   git push origin vx.y.z
   ```

GitHub Actions then builds the installers and jars on Windows, Linux, and both
kinds of Mac, installs and starts each app to check that it launches, and
attaches them all to a GitHub release for the tag. The release notes are
generated from commit titles; edit the release on GitHub to add a friendlier
summary from the changelog.

From the Actions tab you can also run the **Build** workflow by hand. With the
tag left empty it builds the installers without releasing them, so you can
download them from the run and try them out first. With an existing tag
entered, it rebuilds that tag's release files.

The installers aren't code-signed, which is why users see a one-time warning
on Windows and macOS. Signing needs an Apple Developer account for macOS and a
code-signing certificate for Windows.
