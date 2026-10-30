# Notes for AI coding agents

Mailbox Manager is a JavaFX desktop app used by Lake Forest Pack and Ship.
The README covers building, testing, the project layout, and releasing; read
it before making changes. This file covers how to work in the repository.

## Issues and branches

Work is planned in GitHub issues, and each issue gets its own branch.

- Every piece of work starts from an issue. The user opens it, or asks you
  to open it with `gh issue create`. If asked for something that has no
  issue, ask whether to open one, and don't start until it exists. `gh issue
  view <number>` shows what an issue asks for.
- Work on the branch linked to the issue. `gh issue develop --list
  <number>` shows it; run `git fetch` first, then check it out. If the issue
  has no branch, create one from `main` with `gh issue develop <number>
  --base main --checkout`, which links it the same way as Development →
  Create a branch on the issue's page.
- Never commit directly to `main`, except for the release commit (see
  "When finishing a release"). Before changing anything, check the current
  branch.
- Say which issue each commit is for at the end of its message, such as
  "Part of #4".
- When the issue's work is done, push its branch and open a pull request
  (see below) without asking first, so the user can review it on GitHub.
  The user reviews and merges it and closes the issue, so don't merge it or
  close the issue yourself.
- Otherwise, push only when the user asks.
- Once a pull request is merged, switch to `main`, pull it, and delete the
  issue's local branch.

## Commits and checking in

Work the way a developer on the team would: in small steps, asking along
the way.

- Make a commit for each task or each separate change, so the history is
  easy to read and any one change can be undone on its own. Three tasks
  means at least three commits. Each commit has passing tests and a
  message saying what changed and why.
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

## Opening a pull request

Push the branch, then open the pull request with `gh pr create` and give
the user its link. If the GitHub CLI isn't installed, give the user a title
and description to paste in instead, with the link for opening it:
`https://github.com/lfpackandship/mailbox-manager/compare/main...<branch>`.

The description is for whoever reviews and merges the branch, so unlike the
release notes it can name code. Base it on `git log main..<branch>` and the
diff, not on memory, and keep it short:

```markdown
## Summary

One or two sentences: what the branch does and why, and which issue it's
for, such as "Part of #4".

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

There's no release branch. The user decides when a release is ready, once
the issues they want in it are merged into `main`, and says which version
it is. Do these in order. Steps 3 and 5 publish the release, so ask before
each one and wait for a yes. Do the rest without asking.

1. Switch to `main` and pull it. Check that `git status` is clean.
2. Make the release commit on `main`, the one commit that doesn't go on an
   issue branch, as in the README's release steps: update the README for
   anything in the release that it describes, set the version in
   `pom.xml`, and commit it as `Release x.y.z`.
3. Ask, then push `main`.
4. Wait for the tests to pass in GitHub Actions. If you can't see them, ask
   the user to say when they have.
5. Ask, then tag the release commit `vx.y.z` and push the tag, which builds
   the installers and makes the GitHub release.
6. Once the build finishes, check that its annotations don't include the
   "GOOGLE_CLIENT_ID isn't set" warning. If they do, the installers can't
   connect to Google Drive, so tell the user. If you can't see the build,
   ask the user to look.
7. Write the release notes as described below and replace the generated
   ones with them: `gh release edit vx.y.z --notes-file <file>`. Then give
   the user the release's link. If the GitHub CLI isn't installed, give the
   user the notes to paste in instead (edit the release, replace the
   generated notes).

The release notes are for the people who use the app, not developers.
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

Base the notes on what's changed since the last release, not on memory:
the pull requests merged since its tag (`gh pr list --state merged`) and
`git log v<last>..vx.y.z`.
