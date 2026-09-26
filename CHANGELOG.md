# Changelog

What changed in each release of Mailbox Manager, newest first. Downloads are on
the [releases page](https://github.com/wgoodzey/mailbox-manager/releases).

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
