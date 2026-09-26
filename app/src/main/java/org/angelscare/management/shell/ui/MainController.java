package org.angelscare.management.shell.ui;

import java.nio.file.Path;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import org.angelscare.management.db.DeviceIdentity;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

/** The main window. For now it only reports that the database is up. */
@Component
@Scope("prototype") // one controller per loaded view, as FXMLLoader expects
public class MainController {

    private final DeviceIdentity deviceIdentity;
    private final Path dataDir;

    @FXML
    private Label status;

    MainController(DeviceIdentity deviceIdentity, @Value("${angelscare.data-dir}") Path dataDir) {
        this.deviceIdentity = deviceIdentity;
        this.dataDir = dataDir;
    }

    @FXML
    void initialize() {
        status.setText("Database OK\n\n"
                + "Data folder: " + dataDir + "\n"
                + "This computer's ID: " + deviceIdentity.deviceId());
    }
}
