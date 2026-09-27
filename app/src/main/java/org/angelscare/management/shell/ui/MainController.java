package org.angelscare.management.shell.ui;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.layout.StackPane;
import org.angelscare.management.Diagnostics;
import org.angelscare.management.accounts.model.Ledger;
import org.angelscare.management.accounts.ui.DetailController;
import org.angelscare.management.accounts.ui.DetailViewModel;
import org.angelscare.management.db.DeviceIdentity;
import org.angelscare.management.shell.ui.Navigator.Page;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

/** The main window: shows whatever page the {@link Navigator} has open. */
@Component
@Scope("prototype") // one controller per loaded view, as FXMLLoader expects
public class MainController {

    /** A page built once and shown again on every visit. */
    private record Loaded(Parent root, PageController controller) {
    }

    private final Navigator navigator;
    private final ApplicationContext context;
    private final DetailViewModel incomeDetail;
    private final DetailViewModel expenseDetail;
    private final DeviceIdentity deviceIdentity;
    private final Path dataDir;
    private final Map<String, Loaded> pages = new HashMap<>();

    @FXML
    private Button back;
    @FXML
    private Label breadcrumb;
    @FXML
    private StackPane content;
    @FXML
    private Label footer;

    MainController(Navigator navigator, ApplicationContext context,
            @Qualifier("incomeDetailViewModel") DetailViewModel incomeDetail,
            @Qualifier("expenseDetailViewModel") DetailViewModel expenseDetail,
            DeviceIdentity deviceIdentity, @Value("${angelscare.data-dir}") Path dataDir) {
        this.navigator = navigator;
        this.context = context;
        this.incomeDetail = incomeDetail;
        this.expenseDetail = expenseDetail;
        this.deviceIdentity = deviceIdentity;
        this.dataDir = dataDir;
    }

    @FXML
    void initialize() {
        footer.setText("Data folder: " + dataDir + "    ·    This computer's ID: "
                + deviceIdentity.deviceId());
        navigator.currentProperty().addListener((obs, old, page) -> show(page));
        navigator.termProperty().addListener((obs, old, term) -> showBreadcrumb());
        navigator.start();
        show(navigator.current());
    }

    @FXML
    void back() {
        navigator.back();
    }

    private void show(Page page) {
        back.setDisable(page instanceof Page.SchoolYears);
        showBreadcrumb();
        Loaded loaded = pages.computeIfAbsent(key(page), this::load);
        content.getChildren().setAll(loaded.root());
        if (loaded.controller() != null) {
            try {
                loaded.controller().show();
            } catch (Exception e) {
                content.getChildren().setAll(error("The page could not be shown.", e));
            }
        }
    }

    private void showBreadcrumb() {
        breadcrumb.setText(String.join("  ›  ", navigator.breadcrumb()));
    }

    private static String key(Page page) {
        return switch (page) {
            case Page.SchoolYears p -> "years";
            case Page.TermSummary p -> "summary";
            case Page.Detail p -> p.ledger() == Ledger.INCOME ? "income" : "expense";
            case Page.Students p -> "students";
        };
    }

    /** Builds a page the first time it is opened; a broken page shows its error, not a crash. */
    private Loaded load(String key) {
        try {
            return switch (key) {
                case "years" -> fxml("/fxml/calendar/school-years.fxml", null);
                case "summary" -> fxml("/fxml/accounts/term-summary.fxml", null);
                case "income" -> fxml("/fxml/accounts/detail.fxml",
                        new DetailController(incomeDetail, navigator));
                case "expense" -> fxml("/fxml/accounts/detail.fxml",
                        new DetailController(expenseDetail, navigator));
                default -> fxml("/fxml/student/students.fxml", null);
            };
        } catch (Exception e) {
            return new Loaded(error("The page could not be built.", e), null);
        }
    }

    private Loaded fxml(String resource, PageController controller) throws Exception {
        FXMLLoader loader = new FXMLLoader(MainController.class.getResource(resource));
        if (controller == null) {
            loader.setControllerFactory(context::getBean);
        } else {
            loader.setController(controller);
        }
        Parent root = loader.load();
        return new Loaded(root, loader.getController());
    }

    private static Parent error(String what, Exception e) {
        Diagnostics.log(what, e);
        TextArea details = new TextArea(what + "\n\n" + Diagnostics.describe(e)
                + "\n\nLog file:\n" + Diagnostics.logFilePath());
        details.setEditable(false);
        details.setWrapText(true);
        return details;
    }
}
