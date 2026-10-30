# Mailbox Manager user guide

How to use Mailbox Manager, screen by screen, and where it keeps your data
and backups. To install it, see the [README](../README.md#installing).

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
  lists the empty boxes to pick from. **Save** and **Cancel** stay at the top
  of the window as the form scrolls; a field with a mistake is outlined in red.
  After saving, the form empties, ready for the next box. Leaving a form with
  changes that haven't been saved asks first.
- **Manage Boxes** – the open boxes in one table showing each box number,
  holder's name, business title, and phone, with a search field, which has
  the cursor in it when the screen opens. Search
  matches any part of a name, business name, box number, box name, phone
  number, email, forwarding address (including its note), the notes or dated notes, or the
  number of a [forwarding label](#forwarding-labels) printed for the box, and
  phone numbers match with or without formatting. **Ctrl+F** (⌘F on a Mac)
  comes here from any screen, ready to search. To see everything recorded
  for a box, including its rental history, double-click it, or select it and
  click **View** or press Enter; the details open in their own window with
  **Edit** and **Renew…** buttons. Select a row to edit, renew, close, or
  delete it. The menu next to the search field shows closed boxes instead,
  all boxes, boxes [holding mail](#holding-mail), or
  [recently deleted](#deleting-a-box) ones. **Print…** prints the list as shown, and **Save as
  Spreadsheet…** saves it as a file Excel or Google Sheets opens, with
  everything recorded for each box. **Import…** adds many boxes at once from a
  spreadsheet (see [Importing boxes](#importing-boxes-from-a-spreadsheet)).
- **Renewals** – open boxes that are past due, and those due within the next
  30 days (adjustable in Settings), most urgent first. On the main menu, small
  bubbles on the Renewals button count them: red for past due, yellow for due
  soon. Point at the button to see what the numbers mean. Select a box and click
  **Renew…** to renew it, or double-click it, or select it and click **View**
  or press Enter, to see its full details. **Print Reminders…** prints a
  [renewal reminder](#renewal-reminders-and-the-price-sheet) for each box to
  put in it, and **Print List…** and **Save as Spreadsheet…** print or save
  the lists.
- **Calendar** – a month view with each open box shown on the day its rental
  ends. Today is outlined in blue, and **Today** comes back to this month. A
  day with more than two boxes shows the
  first and how many more, such as "+3 more". Hover over an entry for the holder's name. Click a highlighted day to
  list all the boxes ending that day, with each holder's name, business, and phone, and buttons to view or
  renew them.
- **Payments** – every rental and renewal recorded between two dates, with the
  total paid. It starts on this month; **Last Month** and **This Year** are a
  click away, or choose any dates and click **Show**. **Print…** and **Save as
  Spreadsheet…** print or save the list, for example for whoever does the
  books. Select an entry and click **Edit Entry…** to fix one recorded wrongly,
  or **Delete Entry** to remove it. Key deposits aren't counted as paid, since
  they're given back; the total held for open boxes is shown underneath, along
  with any kept from boxes closed in the dates shown.
- **Box Inventory** – every box you have, and whether it's rented or empty.
  See [Box inventory](#box-inventory).

A line under the buttons sums up the shop: how many boxes are open, how many
are empty (once the [box inventory](#box-inventory) is set up), and the money
taken this month, as on Payments, such as `212 open boxes · 14 empty ·
$3,480.00 taken in October`.

### The menu bar

The menus at the top of the window are there on every screen:

- **File** – **Print Price Sheet…** (⌘P on a Mac, Ctrl+P on Windows and
  Linux), **Print Renewal Reminders…** for every box past due or due soon,
  **Forwarding** (⌘6 or Ctrl+6, see [Forwarding labels](#forwarding-labels)),
  **Import Boxes…** (see [Importing boxes](#importing-boxes-from-a-spreadsheet)),
  **Back Up Now…**, **Restore a Backup…**, **Settings**, and **Exit**.
- **Go** – any screen, without going back to the main menu first: **Main
  Menu** (⌘0 or Ctrl+0), **Add New Box** (⌘N or Ctrl+N), **Manage Boxes**,
  **Find a Box** (⌘F or Ctrl+F, which opens Manage Boxes ready to search),
  **Renewals**, **Calendar**, **Payments**, and **Box Inventory** (⌘1 to ⌘5,
  or Ctrl+1 to Ctrl+5), and the **Prices…** window.
- **Help** – **Keyboard Shortcuts**, which lists every shortcut in the app,
  **Show Data Folder**, and **About Mailbox Manager**, which says which
  version is installed.

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

To fix a renewal recorded wrongly, select it on Payments and click **Edit
Entry…**: the day it was paid, the dates it covers, the amount, how it was
paid, and the note can all be changed. If the box's rental ends on that
entry's end date, changing the end date changes when the box's rental ends
too. If a renewal was recorded by mistake, click **Delete Entry** instead.
That leaves the box's end date as it is; edit the box to change it. Editing a
box's end date directly doesn't add anything to its history.

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
prices, and the reminders' messages are set in Settings under
[Price Sheet and Reminders](#settings). What each size measures is entered on
the Prices window.

### Forwarding-only boxes

Some customers don't rent a box here; the shop just forwards their mail,
which still arrives addressed to a box number, often the box they used to
rent. Tick **Forwarding only** on Add New Box or Edit Box for them, and add
the address to forward to under **Forwarding Addresses**. Until an address is
added, the box's details say none is recorded yet.

A forwarding-only box doesn't hold its box number, so the box with that
number can be rented to someone else, and the Box Inventory counts it as
empty. Ticking **Forwarding only** for a holder who's giving up their box but
wants their mail forwarded frees the box straight away. Lists show the number
as, for example, "12 (forwarding)", and on Manage Boxes **Forwarding only** in
the menu next to the search field shows just these.

Forwarding-only boxes still have an end date, appear on Renewals and the
Calendar, and are renewed the same way, but no box price is filled in when
renewing, and the key fields are turned off. **Print Reminders…** ticks them
like any other box, to mail or hand over. Their reminder is headed **Mail
Forwarding Renewal Reminder**, has no size circled, and has its own message,
set in Settings as **Forwarding reminder message**.

### Keys and deposits

Add New Box and Edit Box have a place for how many keys the holder was given
and the refundable deposit paid for them. Typing the number of keys fills in
the deposit, at the **Key deposit per key** set in Settings ($10.00 unless
changed); you can still change it. A box's details show its keys, and closing
a box reminds you to collect them and give back the deposit.

Closing a box with a deposit also asks whether the deposit was **Given back**
or **Kept**, for example because the keys weren't returned. It starts on **Not
recorded yet**, so you can close the box without answering and set it later:
Edit Box has a **Key Deposit Was** choice for closed boxes. Payments shows the
deposits kept from boxes closed in the dates shown.

### Closing a box

When a holder gives up their box, select it on Manage Boxes and click
**Close Box**. It disappears from Manage Boxes, Renewals, and the Calendar,
and its number can be given to someone else, but everything recorded for it
is kept: choose **Closed boxes** from the menu next to the search field to
find it. To bring it back, select it there and click **Reopen**. That only
works if its number hasn't been given to someone else since; if it has, edit
the closed box's number first.

### Deleting a box

**Delete** is for boxes entered by mistake; if the holder has given up the
box, close it instead. A deleted box disappears from every list and its
number can be given to someone else, but for 30 days it's kept under
**Recently deleted** in the menu next to the search field on Manage Boxes.
Select it there and click **Restore** to bring it back as it was, as long as
its number hasn't been given to someone else in the meantime.

After 30 days it's erased for good, the next time the app starts. To erase
one straight away, select it under Recently deleted and click **Delete
Forever**. Either way, its payments stay on Payments, marked with the box
number, such as "12 (deleted)", so the totals for past months never change.

### Importing boxes from a spreadsheet

To add many boxes at once, such as when first setting up the app, open
**File → Import Boxes…**, or click **Import…** on Manage Boxes.

1. Click **Save Template**. It puts **Mailbox Manager import template.xlsx**
   in your Downloads folder (with a number added if one's already there).
2. Open it in Excel, Google Sheets, or Numbers. Fill in one box per row,
   under the grey example row. Only **Box Number**, in orange, is required;
   the **Key** tab says what goes in each column. Save it as an Excel
   workbook (.xlsx) or a CSV file. In Google Sheets or Numbers, download or
   export it as Excel.
3. Click **Choose File…** and pick it.

Every row is checked the way Add New Box checks the form: the box number
mustn't be rented to someone else or appear on two rows, must be in the
[box inventory](#box-inventory) if that's set up, and phone numbers, emails,
dates, keys, deposits, and forwarding addresses must make sense. Rows with
problems are listed. Select one and click **Fix…** to correct it in the app,
or **Skip** to leave it out; **Skip All** leaves them all out, after asking.
Once none are left, **Import** adds the boxes, all at once. The example row,
if left in, and blank rows are skipped.

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
such as `Large: 2 of 5 empty, 1 out of service · Small: 30 of 35 empty`.

A box that can't be rented for now, for example because of a broken lock,
can be marked out of service: select it and click **Out of Service…**, and
give a reason if you like. It isn't counted as empty, and **Choose…** on Add
New Box doesn't offer it. Typing its number on Add New Box or Edit Box asks
whether to use it anyway. Anyone already renting it keeps it, and its status
shows, for example, "Rented, out of service: broken lock". **Back in
Service** undoes it, and **Out of service** in the menu above the list shows
just these boxes.

### Prices

Click **Prices…** on the Box Inventory screen, or next to **Prices for each
size** in Settings, to set what a box costs. It's laid out like the printed
price sheet: a column for each size in the inventory, plus a **Default**
column for boxes with no size, or whose size has no price for that length,
and a row for each rental length. Leave a price blank if there isn't one, and
click **Save**. The **Measures** row at the top is for what each size
measures, such as `3¾" x 5" x 14"`, which is shown on the printed price sheet.

When you click a rental length button on Add New Box or when renewing, the
price for that box's size and length is filled in as the amount paid. You can
still change it; an amount you type in isn't replaced if you then pick another
length. On Add New Box the price is filled in once both the box number and the
length are chosen. Prices apply only when you use the rental length buttons,
not when you pick an end date yourself.

### Raising prices from a later date

To change prices from a day in the future, and let box holders know:

1. Open the Prices window and click **Price Change…**.
2. Choose the day the new prices start under **New prices start on**.
3. The new prices start out as today's. Change the ones that are going up, and
   click **Save**.
4. Click **Print Notices…** to print a notice for each rented box. Each one
   has the box number, the holder's name, when their rental ends, and the day
   the new prices start, then today's prices and the new ones, with the box's
   size circled in both. As with reminders, untick any you don't want, click a
   box to preview its notice, and print or save them as a PDF. The message on
   the notices can be changed on the Price Change window.

Until that day, adding or renewing a box still fills in today's prices, so box
holders can renew early to keep them. On that day, the new prices replace
today's on their own; there's nothing else to do. The Prices window says when
a price change is coming. To change it, open **Price Change…** again, or click
**Cancel Price Change** to keep today's prices. Forwarding-only boxes don't
get notices, since box prices don't apply to them.

### Forwarding addresses

If a box holder wants mail forwarded, add one or more forwarding addresses on
Add New Box or Edit Box: fill in the street, city, two-letter state, and ZIP
code (5 digits or ZIP+4), plus an optional apartment or suite and a note such
as "summer" or "office", then click **Add Address**. A box can have several
addresses; the notes help staff pick the right one. An address typed but not
added is saved with the box anyway. The **Forwarding Addresses** and
**Alternate Business Names** sections fold up to save room; click the heading
to open one. Each heading says how many there are.

### Dated notes

Besides the **Notes** box, which is rewritten as things change, Edit Box has
a **Dated Notes** section for keeping a history of what happens with a box,
such as "Called about late payment". Type the note and click **Add Note**;
it's dated today unless you choose an earlier day first, for something that
happened before. To change a note, select it and click **Edit**, which puts
it back in the fields to change and add again; **Remove** deletes it.
Nothing is saved until you click **Save**. The box's details list the dated
notes newest first. Add New Box doesn't have them, since a new box has no
history yet.

### Holding mail

When a box holder asks the shop to hold their mail, such as while they're
on vacation, open **Mail Hold** on Add New Box or Edit Box. Choose the first
day under **From** and the last under **Until**, add a note if it helps,
such as "forward it all on the 20th", and click **Save**. While the hold is
on, the box's details say so at the top, and Manage Boxes and Renewals show
the box number as, for example, "12 (holding)". **Holding mail** in the menu
next to the search on Manage Boxes lists just those boxes. A hold set for
later days shows on the box's details as "Will hold mail …" until it starts.

The hold ends by itself after its last day; there's nothing to remove. To
end one sooner, click **Clear** in the Mail Hold section and save.

### Forwarding labels

To print a mailing label for forwarding a holder's mail, open the box's
details and click **Print Label…**, or find the address under **File →
Forwarding** and click **Print Label…** there. The window that opens shows a
preview of the label: the shop's name and address from Settings in the top
corner, and the holder's name, business, and forwarding address in large
type. Choose the address if the box has more than one, and what to print on:

- **Label** – a 4 by 6.5 inch label, in the label printer.
- **Copy paper** – the same label in the corner of an ordinary sheet, with a
  dashed line around it to cut out.

Click **Print…** and choose the printer in the Print window that opens.

Each label printed gets a number, printed small in its bottom corner, such as
**261007-03** for the third label printed on October 7, 2026. The app keeps a
record of every label: its number, the box, when it was printed, and the name
and address on it. No two labels get the same number, even after restoring an
older backup.

**File → Forwarding** has two lists:

- **Who We Forward For** – every forwarding address, closed boxes included,
  newest first, with a search. Addresses added before version 1.10 have no
  date and are listed last.
- **Labels Printed** – every label printed, newest first, searchable by label
  number, name, box, or address. **Reprint…** prints one again with its
  original number and address.

Either list can be printed or saved as a spreadsheet.

### Settings

Open **File → Settings** (⌘, on a Mac, Ctrl+, on Windows and Linux). Settings
opens in its own window, so whatever you were doing stays as it was. It's
split into tabs: **General**, **Price Sheet and Reminders**, **Backups**, and
**About**. Changes on every tab take effect when you click **Save**, below the
tabs. If something can't be saved, Settings shows the tab it's on.

On **General**:

- **Text size** – Normal, Large, or Extra large, for the whole app. Larger
  text applies right away. If the window is then too small, make it bigger;
  it opens at the size and place it was left in.
- **Rental lengths (months)** – the quick-set buttons on Add New Box and
  Renew, such as `1, 3, 6, 12`. Up to six lengths, each from 1 to 120
  months.
- **Prices for each size** – opens the [price table](#prices).
- **Key deposit per key** – fills in the key deposit on Add New Box and Edit
  Box. Leave it blank if you don't take a deposit.
- **Week starts on** – Sunday or Monday, for the Calendar.
- **Show boxes due within (days)** – how far ahead the Renewals screen looks.
  Default 30.

On **Price Sheet and Reminders**: the shop's name, its address and phone, the
text above the prices (start a line with `-` to make it a bullet point), the
text below them, and the messages on renewal reminders, with a separate one
for forwarding-only boxes. They start out as on the shop's printed price sheet.
**Print Price Sheet…** prints it.

On **Backups**:

- **Daily backups to keep** – how many daily backups to keep before the oldest
  are deleted. Default 30.
- **Also copy backups to** – see [A second copy of your backups](#a-second-copy-of-your-backups).
- **Google Drive** – see [Backing up to Google Drive](#backing-up-to-google-drive).

- **Back up or restore** – **Back Up Now…** and **Restore…**.

The Backups tab also has an **Open** button that shows the data folder in
Explorer or Finder.

The **About** tab shows which version of Mailbox Manager is installed, and the
versions of Java and JavaFX it runs on.

## Your data and backups

Everything is stored in a single database file, `mailboxes.db`, in this folder:

| System  | Folder                                            |
| ------- | ------------------------------------------------- |
| macOS   | `~/Library/Application Support/MailboxManager`    |
| Windows | `%APPDATA%\MailboxManager`                        |
| Linux   | `~/.mailbox-manager`                              |

Each day, the first time the app starts, it saves a copy of the database to
the `backups` folder inside that folder, named by date
(`mailboxes-2026-09-26.db`). Each time the app closes, it brings that day's
backup up to date, so the backup has the day's work. If the app is left open
past midnight, it saves the new day's backup by itself. If a backup can't be
made, for example because the disk is full, the app still opens, shows a
warning, and tries again the next time it starts; nothing is shown when the
app closes.

### A second copy of your backups

The daily backups are on the same computer as the data, so they won't help if
the computer itself fails. To keep copies somewhere else:

- **Automatically:** on the Backups tab of Settings, next to **Also copy
  backups to**, click **Choose…** and pick a folder on a USB drive, a network
  drive, or a synced folder such as OneDrive or Dropbox, then click **Save**.
  Each daily backup is copied there too, including when it's brought up to
  date as the app closes, and old copies are deleted the same way. If the
  folder isn't available when the app starts (for example, the
  USB drive is unplugged), the app shows a warning and tries again next time.
- **Whenever you like:** click **Back Up Now…**. It saves a backup named
  with the date and time, such as `mailboxes-backup-2026-09-26-143005.db`,
  in the backups folder, where **Restore a Backup…** lists it. It then asks
  whether to save a copy somewhere else too: **Downloads**, ready to drag
  onto a USB drive or send to someone, the folder set as **Also copy backups
  to** if there is one, or any folder with **Choose Folder…**. Click **No
  Thanks** to keep just the one in the backups folder. Backups saved this
  way are never deleted automatically, wherever they are.

### Backing up to Google Drive

The app can also put a copy of each daily backup in your Google Drive, so your
data is safe even if the computer is lost or broken. To turn it on:

1. Open **File → Settings**, go to **Backups**, and next to **Google
   Drive**, click **Connect Google Drive**.
2. Your web browser opens Google's sign-in page. Sign in to the Google account
   you want the backups in, and click **Allow**.
3. Go back to the app. It shows "Backing up to Google Drive as" and your
   email address, and uploads today's backup straight away.

After that, each day's backup is uploaded when the app starts (or at the
new day, if it's left open), to a folder in your Google Drive called
**Mailbox Manager Backups**. It isn't uploaded again each time the app
closes, so a day's work reaches Google Drive in the next day's backup. The app
deletes old backups there the same way it does on the computer, keeping as
many as **Daily backups to keep** says. The app can only see the files it
puts in your Google Drive, not anything else there.

Settings shows when the last backup was uploaded. If an upload fails, for
example because the internet is down, the app shows a warning and tries again
the next time it starts; the backup is still saved on the computer. If Google
ever signs the app out, the warning has a **Reconnect** button that takes you
through signing in again.

To stop, click **Disconnect** in Settings. Backups already in your Google
Drive are kept. To use a backup from Google Drive, see
[Restoring a backup](#restoring-a-backup).

### Restoring a backup

1. Open **File → Settings**, go to **Backups**, and click **Restore…**.
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

## Running the jar instead

Each release also includes a jar for each system (`…-<platform>.jar`), which
needs Java 17 or newer installed; Java 25 from
[Oracle](https://www.oracle.com/java/technologies/downloads/) or
[Adoptium](https://adoptium.net/) is recommended. The Java on java.com is
version 8, which is too old. Run it with `java -jar <file>.jar`, or
double-click it. If double-clicking shows "A Java Exception has occurred",
check that `java -version` reports 17 or newer.
