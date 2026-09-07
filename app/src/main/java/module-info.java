module org.angelscare.management {
    requires javafx.controls;
    requires java.sql;

    opens org.angelscare.management to javafx.graphics;
    exports org.angelscare.management;
}