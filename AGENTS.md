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

## Commits and checking in

Work the way a developer on the team would: in small steps, asking along
the way.

- Make a commit for each task or each separate change, so the history is
  easy to read and any one change can be undone on its own. Three tasks
  means at least three commits. Each commit has passing tests, its
  CHANGELOG.md entry, and a message saying what changed and why.
- Before starting a list of tasks, say how you'll split it into commits and
  ask about anything unclear. Then stop after each task: say what changed,
  as the user would see it, and wait for a go-ahead before the next. Don't
  work through the whole list in one go.
- Ask rather than guess when a decision is the user's: what a field or
  setting is for, how something should look or behave, or anything that
  changes how the shop works day to day. Don't fill a gap with your own
  idea and carry on.
- Do what was asked. Suggest anything more, and wait to be asked before
  doing it.

## Working on the code

- Run the tests with `./mvnw test`, never from an IDE's test runner (see the
  README for why). Some tests open windows, so they need a display.
- The code targets Java 11: no records, text blocks, or switch expressions.
- Match the surrounding code's style and comment density. User-facing text
  is plain and friendly, written for someone who isn't technical.
- Add each user-visible change to the "Unreleased" section of CHANGELOG.md.

## Documentation

Everything is documented, so someone new can read any file cold.

- In `src/main/java`, every class, field, constructor, and method has a
  Javadoc comment, private ones included, with `@param`, `@return`, and
  `@throws` where they apply. The build checks this: the compiler runs with
  `-Xdoclint` and `-Werror` (see `pom.xml`), so `./mvnw test` fails on a
  missing comment or tag. Don't turn the check off or work around it.
- Say what the code does and why, not what its name already says: what a
  value means, its units (cents, points, days), what `null` or an empty
  value means, what's changed or saved, and what can go wrong. Name the
  screen or setting a user would know it by.
- A new class starts with a comment on what it's for and where it fits.
  Inside methods, comment the reasons that aren't obvious from the code.
- In tests, every class has a comment saying what it covers, and every
  helper method has one. Test method names already read as sentences, so
  `@Test` methods don't need one.
- When code changes, change its comments with it. A wrong comment is worse
  than none.
- Google Drive backups need the app's Google client ID. Release builds get
  it from the `GOOGLE_CLIENT_ID` and `GOOGLE_CLIENT_SECRET` repository
  secrets; local builds from the gitignored
  `src/main/resources/org/lfps/mailboxes/drive/google-oauth.properties`.
  Never commit that file or print its values.
- The app's privacy policy is in the separate public `mailbox-manager-site`
  repo. If the app starts handling data or Google access differently, update
  the policy and its date there too.

## When asked for a pull request

Push the branch if the user agrees, then give them a title and description
to paste in. The GitHub CLI may not be installed, so also give the link for
opening it: `https://github.com/lfpackandship/mailbox-manager/compare/main...<branch>`.

The description is for whoever reviews and merges the branch, so unlike the
release notes it can name code. Base it on `git log main..<branch>` and the
diff, not on memory, and keep it short:

```markdown
## Summary

One or two sentences: what the branch does and why.

## Changes

- One bullet per meaningful change, user-visible ones first. Name the
  screens or classes involved.

## Testing

- `./mvnw test`: how many tests ran and whether they passed.
- Anything checked by hand, such as signing in to Google Drive.

## Before merging

- Anything the reviewer must do or know: new repository secrets, Google
  Cloud or privacy policy changes, data upgrades. Leave this section out if
  there's nothing.
```

The title is a short imperative phrase like a commit title, such as "Add
Google Drive backups". Don't merge the pull request yourself unless asked.

## When finishing a release

Follow the README's release steps. Then, without being asked, give the user
release notes to paste into the GitHub release (edit the release, replace the
generated notes). They're for the people who use the app, not developers.
Write them like the earlier releases on the releases page:

- **What's new** first. Each feature starts with a short bold sentence
  naming it, followed by what it does and where to find it, in the app's own
  words (button and screen names in bold, as they appear). Use numbered
  steps when there's something to set up, and bullets for details.
- **Fixes** next, if there are any: what went wrong, as the user saw it,
  in the past tense, and that it's fixed.
- **Installing or upgrading** last, always, with the download table. Say if
  the data is upgraded the first time the new version starts.
- End with a "See the README for more on …" line for anything new that the
  README explains further.
- No commit hashes, code names, file paths inside the app, or developer
  terms. Leave out sections that don't apply.

For example, the notes for 1.5.2:

```markdown
## What's new

**The phone number is now optional.** On Add New Box and Edit Box, only the box number is required. If you do enter a phone number, it still has to be a complete 10-digit number, so typos are caught.

## Installing or upgrading

Download the installer for your computer below and install it over the old version. Your boxes, settings, and backups carry over automatically.

| Computer | File |
| --- | --- |
| Windows | mailbox-manager-1.5.2-windows-x64.msi |
| Mac with Apple silicon (M1 and later) | mailbox-manager-1.5.2-mac-arm64.dmg |
| Mac with Intel | mailbox-manager-1.5.2-mac-x64.dmg |
| Linux | mailbox-manager-1.5.2-linux-x64.AppImage |
```

Base the notes on the release's CHANGELOG.md entry. After the build
finishes, check that its annotations don't include the "GOOGLE_CLIENT_ID
isn't set" warning; if they do, the installers can't connect to Google Drive.
