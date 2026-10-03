# Notes for AI coding agents

Mailbox Manager is a JavaFX desktop app used by Lake Forest Pack and Ship.
The README covers building, testing, the project layout, and releasing; read
it before making changes. This file covers how to work in the repository.

## Branches

- Never commit directly to `main`. Before changing anything, check the
  current branch. If it's `main`, create a branch for the work first.
- Name branches after the version they're for: `1.7-features` for a minor
  release, `1.6.1-features` for a patch release. If a branch for the next
  version already exists, keep working on it rather than starting another.
- Merge into `main` only when the user asks, and push only when they ask.
- After a release is tagged, its branch can be deleted once `git log
  main..<branch>` is empty, locally and on GitHub.

## Working on the code

- Run the tests with `./mvnw test`, never from an IDE's test runner (see the
  README for why). Some tests open windows, so they need a display.
- The code targets Java 11: no records, text blocks, or switch expressions.
- Match the surrounding code's style and comment density. User-facing text
  is plain and friendly, written for someone who isn't technical.
- Add each user-visible change to the "Unreleased" section of CHANGELOG.md.
- Google Drive backups need the app's Google client ID. Release builds get
  it from the `GOOGLE_CLIENT_ID` and `GOOGLE_CLIENT_SECRET` repository
  secrets; local builds from the gitignored
  `src/main/resources/org/lfps/mailboxes/drive/google-oauth.properties`.
  Never commit that file or print its values.
- The app's privacy policy is in the separate public `mailbox-manager-site`
  repo. If the app starts handling data or Google access differently, update
  the policy and its date there too.

## When finishing a release

Follow the README's release steps. Then give the user release notes to paste
into the GitHub release, in this format (match the earlier releases on the
releases page, and write for the people who use the app, not developers):

```markdown
## What's new

**Feature name.** What it does and how to use it, step by step if needed.

## Fixes

- What was wrong, described as the user saw it.

## Installing or upgrading

Download the installer for your computer below and install it over the old
version. Your boxes, settings, and backups carry over automatically.

| Computer | File |
| --- | --- |
| Windows | mailbox-manager-x.y.z-windows-x64.msi |
| Mac with Apple silicon (M1 and later) | mailbox-manager-x.y.z-mac-arm64.dmg |
| Mac with Intel | mailbox-manager-x.y.z-mac-x64.dmg |
| Linux | mailbox-manager-x.y.z-linux-x64.AppImage |
```

Leave out sections that don't apply, such as Fixes in a release with none.
After the build finishes, check that its annotations don't include the
"GOOGLE_CLIENT_ID isn't set" warning.
