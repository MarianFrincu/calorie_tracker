package com.calorietracker.desktop;

import com.calorietracker.desktop.api.ApiClient;
import com.calorietracker.desktop.api.CognitoAuthService;
import com.calorietracker.desktop.ui.AiView;
import com.calorietracker.desktop.ui.DayView;
import com.calorietracker.desktop.ui.FoodExplorerView;
import com.calorietracker.desktop.ui.ObjectiveView;
import com.calorietracker.desktop.ui.ProfileView;
import com.calorietracker.desktop.ui.RecipesView;
import com.calorietracker.desktop.ui.ReportsView;
import com.calorietracker.desktop.ui.WeightView;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import javafx.application.Application;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.Separator;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TextField;
import javafx.scene.control.TextInputDialog;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

/**
 * Calorie Tracker desktop shell.
 *
 * Sidebar: Day · Recipes · AI · Profile · Objective · Weight · Reports.
 * Day/Recipes/Profile/Objective/Weight/Reports are recreated on every visit
 * so their data is fresh; AI is CACHED so the chat history persists across
 * tab switches.
 */
public class CalorieTrackerApp extends Application {

    private final AppConfig config = new AppConfig();
    private ApiClient api;
    private Stage stage;
    private BorderPane root;
    private VBox sidebar;
    private final List<Button> navButtons = new ArrayList<>();

    /** Cached so chat history doesn't disappear when the user leaves the tab. */
    private AiView aiView;

    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void start(Stage stage) {
        this.stage = stage;
        // Make the main stage globally reachable so child dialogs can call
        // initOwner(...) and we can restore maximize/fullscreen after they close.
        AppContext.setMainStage(stage);

        api = new ApiClient(config.apiBaseUrl());
        if ("cognito".equalsIgnoreCase(config.authMode()) && !login()) {
            return;
        }
        root = new BorderPane();
        sidebar = buildSidebar();
        root.setLeft(sidebar);
        showDay();

        Scene scene = new Scene(root, 1280, 820);
        scene.getStylesheets().add(getClass().getResource("/app.css").toExternalForm());
        stage.setScene(scene);
        stage.setTitle("Calorie Tracker");
        stage.show();
    }

    // ---------------- Sidebar ----------------

    private VBox buildSidebar() {
        Label brand = new Label("Calorie Tracker");
        brand.getStyleClass().add("brand");
        // Let JavaFX shrink the brand if it must but never display "...";
        // ellipsis is the cause of the truncation reported by users.
        brand.setMinWidth(Region.USE_PREF_SIZE);

        Button toggleBtn = new Button("❮"); // ❮ heavy left-pointing angle
        toggleBtn.getStyleClass().add("sidebar-toggle");
        toggleBtn.setOnAction(e -> toggleSidebarCollapsed(toggleBtn));

        Region brandSpacer = new Region();
        HBox.setHgrow(brandSpacer, Priority.ALWAYS);
        HBox topBar = new HBox(brand, brandSpacer, toggleBtn);
        topBar.setAlignment(Pos.CENTER_LEFT);
        topBar.setPadding(new Insets(0, 10, 0, 0));
        topBar.getStyleClass().add("sidebar-top");

        navButtons.clear();
        Button dayBtn       = navButton("Day",       this::showDay);
        Button recipesBtn   = navButton("Recipes",   this::showRecipes);
        Button aiBtn        = navButton("AI",        this::showAi);
        Button compareBtn   = navButton("Explore",   this::showCompare);
        Button profileBtn   = navButton("Profile",   this::showProfile);
        Button objectiveBtn = navButton("Objective", this::showObjective);
        Button weightBtn    = navButton("Weight",    this::showWeight);
        Button reportsBtn   = navButton("Reports",   this::showReports);

        Separator sep = new Separator();
        Region grow = new Region();
        VBox.setVgrow(grow, Priority.ALWAYS);

        // Logout makes sense only when there was a login (Cognito mode). In dev/local
        // mode every call already maps to 'dev-user', so no session to drop.
        VBox box;
        if ("cognito".equalsIgnoreCase(config.authMode())) {
            Button logoutBtn = new Button("Sign out");
            logoutBtn.setMaxWidth(Double.MAX_VALUE);
            logoutBtn.getStyleClass().add("nav-button");
            logoutBtn.setOnAction(e -> signOut());
            box = new VBox(4, topBar, sep,
                    dayBtn, recipesBtn, aiBtn, compareBtn,
                    profileBtn, objectiveBtn, weightBtn, reportsBtn,
                    grow, logoutBtn);
        } else {
            box = new VBox(4, topBar, sep,
                    dayBtn, recipesBtn, aiBtn, compareBtn,
                    profileBtn, objectiveBtn, weightBtn, reportsBtn,
                    grow);
        }
        box.setPrefWidth(200);
        box.setAlignment(Pos.TOP_LEFT);
        box.getStyleClass().add("sidebar");
        return box;
    }

    /**
     * Toggle the sidebar between full (200 px, all labels visible) and a
     * narrow rail (36 px, only the toggle button is shown). When collapsing
     * we also hide the brand label and disable its managed sizing so the
     * 16 px bold "Calorie Tracker" text doesn't stick out and force the rail
     * back to ~120 px.
     */
    private void toggleSidebarCollapsed(Button toggleBtn) {
        boolean collapse = sidebar.getPrefWidth() > 60;
        for (javafx.scene.Node child : sidebar.getChildren()) {
            if (child instanceof HBox topBar) {
                // Brand text + the spacer get hidden when collapsing so the chevron sits
                // dead-centre in the narrow rail, with the topBar's own padding zeroed out.
                for (javafx.scene.Node inner : topBar.getChildren()) {
                    if (inner instanceof Label brand && brand.getStyleClass().contains("brand")) {
                        brand.setVisible(!collapse);
                        brand.setManaged(!collapse);
                    } else if (inner instanceof Region r && !(inner instanceof Button)) {
                        r.setVisible(!collapse);
                        r.setManaged(!collapse);
                    }
                }
                topBar.setAlignment(collapse ? Pos.CENTER : Pos.CENTER_LEFT);
                topBar.setPadding(collapse ? new Insets(0, 0, 10, 0) : new Insets(0, 10, 0, 0));
                continue;
            }
            child.setVisible(!collapse);
            child.setManaged(!collapse);
        }
        sidebar.setPrefWidth(collapse ? 44 : 200);
        sidebar.setMinWidth(collapse ? 44 : 200);
        sidebar.setMaxWidth(collapse ? 44 : 200);
        // Heavy angle brackets stand out against the dark sidebar; the chevron also
        // flips direction so the meaning is unambiguous.
        toggleBtn.setText(collapse ? "❯" : "❮");
    }

    private Button navButton(String text, Runnable action) {
        Button b = new Button(text);
        b.setMaxWidth(Double.MAX_VALUE);
        b.getStyleClass().add("nav-button");
        b.setOnAction(e -> action.run());
        navButtons.add(b);
        return b;
    }

    // ---------------- View swap helpers ----------------

    private void showDay()       { swap(0, new DayView(api)); }
    private void showRecipes()   { swap(1, new RecipesView(api)); }
    private void showAi() {
        if (aiView == null) aiView = new AiView(api);
        swap(2, aiView);
    }
    private void showCompare()   { swap(3, new FoodExplorerView(api)); }
    private void showProfile()   { swap(4, new ProfileView(api)); }
    private void showObjective() { swap(5, new ObjectiveView(api)); }
    private void showWeight()    { swap(6, new WeightView(api)); }
    private void showReports()   { swap(7, new ReportsView(api)); }

    private void swap(int activeIndex, Node view) {
        for (int i = 0; i < navButtons.size(); i++) {
            Button b = navButtons.get(i);
            b.getStyleClass().remove("active");
            if (i == activeIndex) b.getStyleClass().add("active");
        }
        root.setCenter(view);
    }

    /**
     * Drop the cached access token, drop the cached AiView (chat history would
     * leak across users), and re-show the sign-in dialog. If the user cancels
     * out of it, close the app.
     */
    private void signOut() {
        api.setToken(null);
        aiView = null;
        if (login()) {
            root.setLeft(buildSidebar()); // active-state recolour, etc.
            showDay();
        } else {
            stage.close();
        }
    }

    // ---------------- Cognito sign-in / sign-up ----------------

    private boolean login() {
        CognitoAuthService auth = new CognitoAuthService();
        while (true) {
            Dialog<ButtonType> dialog = new Dialog<>();
            dialog.setTitle("Calorie Tracker");
            dialog.setHeaderText("Sign in or create an account");

            // -- Sign-in tab --
            TextField siEmail = new TextField(); siEmail.setPromptText("email");
            PasswordField siPwd = new PasswordField(); siPwd.setPromptText("password");
            GridPane signInForm = new GridPane();
            signInForm.setHgap(10);
            signInForm.setVgap(10);
            signInForm.setPadding(new Insets(14));
            signInForm.addRow(0, new Label("Email"), siEmail);
            signInForm.addRow(1, new Label("Password"), siPwd);

            // -- Sign-up tab --
            TextField suEmail = new TextField(); suEmail.setPromptText("email");
            PasswordField suPwd = new PasswordField(); suPwd.setPromptText("password (min 8 chars)");
            PasswordField suPwd2 = new PasswordField(); suPwd2.setPromptText("repeat password");
            Label suHint = new Label("After registering, Cognito emails a verification code. "
                    + "Enter the code when prompted, then come back here to sign in.");
            suHint.setWrapText(true);
            suHint.getStyleClass().add("muted");
            GridPane signUpForm = new GridPane();
            signUpForm.setHgap(10);
            signUpForm.setVgap(10);
            signUpForm.setPadding(new Insets(14));
            signUpForm.addRow(0, new Label("Email"), suEmail);
            signUpForm.addRow(1, new Label("Password"), suPwd);
            signUpForm.addRow(2, new Label("Confirm"), suPwd2);
            VBox signUpBox = new VBox(10, signUpForm, suHint);
            signUpBox.setPadding(new Insets(0, 14, 14, 14));

            TabPane tabs = new TabPane(
                    new Tab("Sign in", signInForm),
                    new Tab("Register", signUpBox));
            tabs.getTabs().forEach(t -> t.setClosable(false));
            tabs.setPrefWidth(420);

            ButtonType submit = new ButtonType("Continue", javafx.scene.control.ButtonBar.ButtonData.OK_DONE);
            dialog.getDialogPane().setContent(tabs);
            dialog.getDialogPane().getButtonTypes().addAll(submit, ButtonType.CANCEL);
            dialog.getDialogPane().getStylesheets().add(getClass().getResource("/app.css").toExternalForm());
            AppContext.prepareDialog(dialog);

            Optional<ButtonType> result = dialog.showAndWait();
            if (result.isEmpty() || result.get() == ButtonType.CANCEL) return false;

            if (tabs.getSelectionModel().getSelectedIndex() == 1) {
                handleRegister(auth, suEmail.getText().trim(), suPwd.getText(), suPwd2.getText());
                continue; // loop back to the sign-in tab so they can log in
            }

            try {
                String token = auth.login(config.cognitoRegion(), config.cognitoClientId(),
                        siEmail.getText().trim(), siPwd.getText());
                if (token == null || token.isBlank()) {
                    throw new RuntimeException("No access token returned");
                }
                api.setToken(token);
                return true;
            } catch (Exception e) {
                showAlert(Alert.AlertType.ERROR,
                        "Sign-in failed. " + com.calorietracker.desktop.ui.Messages.friendly(e));
            }
        }
    }

    private void handleRegister(CognitoAuthService auth, String email, String pwd, String pwd2) {
        if (email.isBlank() || pwd.isBlank()) {
            showAlert(Alert.AlertType.WARNING, "Email and password are required.");
            return;
        }
        if (!pwd.equals(pwd2)) {
            showAlert(Alert.AlertType.WARNING, "Passwords don't match.");
            return;
        }
        try {
            auth.signUp(config.cognitoRegion(), config.cognitoClientId(), email, pwd);
        } catch (Exception e) {
            showAlert(Alert.AlertType.ERROR,
                    "Registration failed. " + com.calorietracker.desktop.ui.Messages.friendly(e));
            return;
        }
        TextInputDialog codeDialog = new TextInputDialog();
        codeDialog.setTitle("Verify email");
        codeDialog.setHeaderText("We sent a 6-digit code to " + email + ".");
        codeDialog.setContentText("Code:");
        AppContext.prepareDialog(codeDialog);
        Optional<String> code = codeDialog.showAndWait();
        if (code.isEmpty() || code.get().isBlank()) {
            showAlert(Alert.AlertType.INFORMATION,
                    "Account created. You can confirm it later by registering again with the same email "
                            + "and entering the code from the email.");
            return;
        }
        try {
            auth.confirmSignUp(config.cognitoRegion(), config.cognitoClientId(), email, code.get().trim());
            showAlert(Alert.AlertType.INFORMATION,
                    "Account verified. You can now sign in.");
        } catch (Exception e) {
            showAlert(Alert.AlertType.ERROR,
                    "Verification failed. " + com.calorietracker.desktop.ui.Messages.friendly(e));
        }
    }

    /** Owner-aware modal alert with the noisy default header stripped. */
    private static void showAlert(Alert.AlertType type, String message) {
        Alert alert = new Alert(type, message);
        alert.setHeaderText(null);
        AppContext.prepareDialog(alert);
        alert.showAndWait();
    }
}
