# Changelog

What changed in each release of Mailbox Manager, newest first. Downloads are on
the [releases page](https://github.com/wgoodzey/mailbox-manager/releases).

## Unreleased

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
