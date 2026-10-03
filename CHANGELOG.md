# Changelog

What changed in each release of Mailbox Manager, newest first. Downloads are on
the [releases page](https://github.com/lfpackandship/mailbox-manager/releases).

## 1.6.1 – 2026-10-03

### Removed

- The app no longer reads a Google client ID from `google-oauth.properties` in
  the data folder. The installers have it built in, so the file isn't needed;
  one left there is ignored.

## 1.6.0 – 2026-10-02

### Added

- Backups to Google Drive: in Settings, click **Connect Google Drive** and sign
  in to Google, and each daily backup is also uploaded to a
  **Mailbox Manager Backups** folder in your Google Drive. Old backups there
  are deleted the same way as on the computer. Settings shows which account
  it's using and when it last uploaded, and if Google signs the app out, a
  warning offers to reconnect.

## 1.5.2 – 2026-09-28

### Changed

- The phone number is optional on Add New Box and Edit Box. A number that's
  entered still has to be a full 10-digit number.

## 1.5.1 – 2026-09-28

### Changed

- First and last name are optional on Add New Box and Edit Box. A box with no
  name is shown by its business title, if it has one.

## 1.5.0 – 2026-09-26

### Added

- Renewals and payments: a **Renew…** button on Renewals, Manage Boxes, and a
  box's details renews it from its current end date for a rental length or
  to a chosen date, and records the amount paid, how it was paid, and a note.
  Add New Box can record the first payment too.
- Rental history: each box's details list every rental and renewal with what
  was paid.
- A **Payments** screen listing the rentals and renewals recorded between two
  dates (this month, last month, this year, or any dates) with the total paid.
- Closing boxes: **Close Box** on Manage Boxes keeps a box's record and
  history when its holder leaves, frees its number for someone else, and takes
  it off Renewals and the Calendar. Closed boxes can be shown on Manage Boxes
  and reopened.
- A **Box Inventory** screen listing every physical box as rented or empty,
  with sizes. Boxes can be added in ranges such as `1-200`. Once it's set up,
  Add New Box and Edit Box only accept box numbers in it, and **Choose…** on
  Add New Box picks from the empty boxes.
- Prices: a table of what each box size costs for each rental length, with a
  default for boxes with no size, opened from **Prices…** on Box Inventory or
  in Settings. Choosing a rental length on Add New Box or when renewing fills
  in the price for that box's size.
- The Box Inventory screen shows how many boxes of each size are empty.
- On the Calendar, click a day to list the boxes ending that day with their
  holder, business, and phone, and view or renew them from there.
- The Calendar outlines today in blue.
- A notes field on each box, shown in its details and matched by search.

### Changed

- **Delete** on Manage Boxes now warns that it erases the box's history, and
  suggests closing the box instead.
- The main menu has six buttons, with Renewals moved before Calendar.

### Fixed

- Boxes were sorted as text, so box 10 came before box 2. They're now sorted
  by number, with letters after the number they follow (12, 12A, 13),
  everywhere boxes are listed.
- Clicking a column header on Manage Boxes didn't sort the list.
- A Calendar day with three or more boxes spilled into the day below. It now
  lists up to two, or the first and "+N more".

## 1.4.0 – 2026-09-26

### Changed

- Manage Boxes shows only the box number, name, business title, and phone, so
  the table is easier to scan. Double-click a box, or select it and click
  **View** or press Enter, to see its full details in a separate window, with
  an **Edit** button. The same works on the Renewals screen.
- The lists on Manage Boxes and Renewals fit the window instead of making the
  screen scroll, and grow when the window is larger.

## 1.3.0 – 2026-09-26

### Added

- Settings for text size (Normal, Large, Extra large), the rental lengths
  offered as quick-set buttons, and whether the Calendar's weeks start on
  Sunday or Monday.
- Backup tools in Settings: **Back Up Now…** saves a backup to a folder you
  choose, **Restore…** restores a backup (saving your current data first), a
  second folder can receive a copy of every daily backup, and **Open** shows
  the data folder.

- Forwarding addresses: each box can have any number of addresses to forward
  mail to, each with an optional note, entered on Add New Box and Edit Box.
  Manage Boxes search also matches them.

### Changed

- Screens scroll when they don't fit the window, and the window starts larger
  when the text size is larger.

### Fixed

- Saving Edit Box failed for a box with no email or business title stored,
  as can happen with data from older versions or restored backups.

## 1.2.0 – 2026-09-26

### Added

- Installers with Java built in, so you no longer need to install Java:
  a Windows `.msi`, a macOS `.dmg` (Apple silicon and Intel), and a Linux
  `.AppImage`. The Windows installer needs no administrator password and adds
  Start menu and desktop shortcuts.
- An app icon. On a Mac, the menu bar now says "Mailbox Manager" instead of
  "java".

### Changed

- The installers are now the recommended download. The jars are still
  published and need Java 17 or newer.

## 1.1.1 – 2026-09-26

### Fixed

- The jars didn't start on Java 17 or 21, failing with "JavaFX runtime
  components are missing" (on Windows, "A Java Exception has occurred"). This
  affected 1.0.1 and 1.1.0.

## 1.1.0 – 2026-09-26

### Added

- A Settings window, opened from **File → Settings** (⌘, on a Mac, Ctrl+, on
  Windows and Linux), for how many days ahead Renewals looks and how many
  daily backups to keep. Both were previously fixed at 30.
- A File menu on every screen, with Settings and Exit.

### Fixed

- The app wouldn't open if the daily backup failed, for example on a full
  disk. It now opens, shows a warning, and retries the backup next time.
- A backup interrupted partway through could leave a broken file that counted
  as that day's backup.

## 1.0.1 – 2026-09-26

### Added

- Ready-to-run jars for Windows, Linux, and Mac (Apple silicon and Intel) on
  each release.

## 1.0.0 – 2026-09-26

First release.

- **Add New Box:** record a box holder's name, business title, box number and
  nickname, phone, email, alternate business names, and rental end date, with
  1/3/6/12 month quick-set buttons.
- **Manage Boxes:** a searchable table of all boxes, with edit and delete.
- **Calendar:** a month view showing when each box rental ends.
- **Renewals:** boxes that are past due or due within 30 days.
- Phone numbers are formatted as you type.
- Data is stored in a per-user folder and backed up daily, keeping the last 30
  backups.
