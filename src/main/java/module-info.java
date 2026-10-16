/**
 * Mailbox Manager, a desktop app for keeping track of rented mailboxes: who
 * rents each box, when rentals end, payments, prices, and backups.
 */
module org.lfps.mailboxes {
    requires transitive javafx.controls;
	requires java.desktop;
    requires java.net.http;
    requires java.sql;
    requires jdk.httpserver;
    requires org.xerial.sqlitejdbc;
    requires org.dhatim.fastexcel;
    requires org.dhatim.fastexcel.reader;
    exports org.lfps.mailboxes;
    exports org.lfps.mailboxes.model;
}
