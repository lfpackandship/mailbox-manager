module org.lfps.mailboxes {
    requires transitive javafx.controls;
	requires java.desktop;
    requires java.net.http;
    requires java.sql;
    requires jdk.httpserver;
    requires org.xerial.sqlitejdbc;
    exports org.lfps.mailboxes;
    exports org.lfps.mailboxes.model;
}
