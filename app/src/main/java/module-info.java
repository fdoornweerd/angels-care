module org.angelscare.management {
    requires javafx.controls;
    requires java.sql;
    requires org.xerial.sqlitejdbc;

    opens org.angelscare.management to javafx.graphics;
    exports org.angelscare.management;
}