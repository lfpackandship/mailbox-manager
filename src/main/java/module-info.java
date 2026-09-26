module org.lfps.mailboxes {
    requires javafx.controls;
	requires java.desktop;
    requires java.sql;
    requires org.xerial.sqlitejdbc;
    exports org.lfps.mailboxes;
    exports org.lfps.mailboxes.model;
}
