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
import javafx.application.Application;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
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
        if ("cognito".equalsIgnoreCase(config.authMode())) {
            String saved = com.calorietracker.desktop.api.SessionStore.loadIfValid();
            if (saved != null) {
                api.setToken(saved);
            } else if (!login()) {
                return;
            }
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
        com.calorietracker.desktop.api.SessionStore.clear();
        aiView = null;
        // Hide the main window so only the login dialog is visible.
        if (stage != null) stage.hide();
        if (login()) {
            // Re-assign the field too, otherwise toggleSidebarCollapsed keeps
            // mutating the OLD VBox that is no longer on screen.
            sidebar = buildSidebar();
            root.setLeft(sidebar);
            showDay();
            if (stage != null) stage.show();
        } else {
            // User cancelled the sign-in dialog: quit the app entirely.
            javafx.application.Platform.exit();
        }
    }

    // ---------------- Cognito sign-in / sign-up ----------------

    private VBox authFieldGroup(String labelText, javafx.scene.control.Control field) {
        Label l = new Label(labelText);
        l.getStyleClass().add("auth-field-label");
        VBox box = new VBox(6, l, field);
        box.getStyleClass().add("auth-field-group");
        return box;
    }

    private boolean login() {
        CognitoAuthService auth = new CognitoAuthService();
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("Calorie Tracker");
        dialog.getDialogPane().setHeader(null);
        dialog.setHeaderText(null);
        dialog.setGraphic(null);

        Label brand = new Label("Calorie Tracker");
        brand.getStyleClass().add("auth-brand");
        Label tagline = new Label("Log meals, track macros, hit your goals.");
        tagline.getStyleClass().add("auth-tagline");
        VBox heroBox = new VBox(2, brand, tagline);
        heroBox.getStyleClass().add("auth-hero");
        heroBox.setAlignment(Pos.CENTER_LEFT);

        TextField siEmail = new TextField();
        siEmail.setPromptText("you@example.com");
        siEmail.getStyleClass().add("auth-field");
        PasswordField siPwd = new PasswordField();
        siPwd.setPromptText("password");
        siPwd.getStyleClass().add("auth-field");
        VBox signInBox = new VBox(14,
                authFieldGroup("Email", siEmail),
                authFieldGroup("Password", siPwd));
        signInBox.getStyleClass().add("auth-form");

        TextField suEmail = new TextField();
        suEmail.setPromptText("you@example.com");
        suEmail.getStyleClass().add("auth-field");
        PasswordField suPwd = new PasswordField();
        suPwd.setPromptText("min 8 chars, 1 uppercase, 1 number");
        suPwd.getStyleClass().add("auth-field");
        PasswordField suPwd2 = new PasswordField();
        suPwd2.setPromptText("repeat the password");
        suPwd2.getStyleClass().add("auth-field");
        Label suHint = new Label("We'll email a 6-digit code to confirm your address. "
                + "Enter it on the next screen, then come back to sign in.");
        suHint.setWrapText(true);
        suHint.getStyleClass().add("auth-hint");
        VBox signUpBox = new VBox(14,
                authFieldGroup("Email", suEmail),
                authFieldGroup("Password", suPwd),
                authFieldGroup("Confirm password", suPwd2),
                suHint);
        signUpBox.getStyleClass().add("auth-form");

        Tab signInTab = new Tab("Sign in", signInBox);
        Tab registerTab = new Tab("Register", signUpBox);
        TabPane tabs = new TabPane(signInTab, registerTab);
        tabs.getStyleClass().add("auth-tabs");
        tabs.getTabs().forEach(t -> t.setClosable(false));

        Label status = new Label("");
        status.getStyleClass().add("auth-status");
        status.setWrapText(true);
        status.setManaged(false);
        status.setVisible(false);

        VBox content = new VBox(14, heroBox, tabs, status);
        content.getStyleClass().add("auth-root");
        content.setPrefWidth(440);

        ButtonType submit = new ButtonType("Continue", javafx.scene.control.ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().setContent(content);
        dialog.getDialogPane().getButtonTypes().addAll(submit, ButtonType.CANCEL);
        dialog.getDialogPane().getStyleClass().add("auth-pane");
        dialog.getDialogPane().getStylesheets().add(getClass().getResource("/app.css").toExternalForm());

        Runnable clearStatus = () -> {
            status.setText("");
            status.setManaged(false);
            status.setVisible(false);
            status.getStyleClass().removeAll("auth-status-error", "auth-status-ok");
        };
        java.util.function.BiConsumer<String, Boolean> setStatus = (msg, isError) -> {
            status.getStyleClass().removeAll("auth-status-error", "auth-status-ok");
            status.getStyleClass().add(isError ? "auth-status-error" : "auth-status-ok");
            status.setText(msg);
            status.setManaged(true);
            status.setVisible(true);
        };

        tabs.getSelectionModel().selectedItemProperty().addListener((o, a, b) -> clearStatus.run());
        siEmail.textProperty().addListener((o, a, b) -> clearStatus.run());
        siPwd.textProperty().addListener((o, a, b) -> clearStatus.run());
        suEmail.textProperty().addListener((o, a, b) -> clearStatus.run());
        suPwd.textProperty().addListener((o, a, b) -> clearStatus.run());
        suPwd2.textProperty().addListener((o, a, b) -> clearStatus.run());

        final boolean[] signedIn = {false};

        Button submitBtn = (Button) dialog.getDialogPane().lookupButton(submit);
        submitBtn.addEventFilter(javafx.event.ActionEvent.ACTION, ev -> {
            if (tabs.getSelectionModel().getSelectedIndex() == 1) {
                ev.consume();
                String email = suEmail.getText().trim();
                String pwd = suPwd.getText();
                String pwd2 = suPwd2.getText();
                if (email.isBlank() || pwd.isBlank()) {
                    setStatus.accept("Email and password are required.", true);
                    return;
                }
                if (!pwd.equals(pwd2)) {
                    setStatus.accept("Passwords don't match.", true);
                    return;
                }
                try {
                    auth.signUp(config.cognitoRegion(), config.cognitoClientId(), email, pwd);
                } catch (Exception e) {
                    setStatus.accept("Registration failed. "
                            + com.calorietracker.desktop.ui.Messages.friendly(e), true);
                    return;
                }
                boolean confirmed = promptVerifyCode(dialog, auth, email);
                if (confirmed) {
                    siEmail.setText(email);
                    siPwd.clear();
                    tabs.getSelectionModel().select(signInTab);
                    setStatus.accept("Account confirmed — sign in with your password.", false);
                    siPwd.requestFocus();
                }
            } else {
                ev.consume();
                String email = siEmail.getText().trim();
                String pwd = siPwd.getText();
                if (email.isBlank() || pwd.isBlank()) {
                    setStatus.accept("Email and password are required.", true);
                    return;
                }
                try {
                    String token = auth.login(config.cognitoRegion(), config.cognitoClientId(), email, pwd);
                    if (token == null || token.isBlank()) {
                        throw new RuntimeException("No access token returned");
                    }
                    api.setToken(token);
                    com.calorietracker.desktop.api.SessionStore.save(token);
                    signedIn[0] = true;
                    dialog.setResult(submit);
                    dialog.close();
                } catch (Exception e) {
                    setStatus.accept("Sign-in failed. "
                            + com.calorietracker.desktop.ui.Messages.friendly(e), true);
                }
            }
        });

        javafx.application.Platform.runLater(siEmail::requestFocus);
        AppContext.prepareDialog(dialog);
        dialog.showAndWait();
        return signedIn[0];
    }

    /**
     * Modal verify-code dialog owned by the parent login dialog so it
     * appears on top instead of replacing it. Returns true if Cognito
     * accepted the code, false otherwise.
     */
    private boolean promptVerifyCode(Dialog<?> parent, CognitoAuthService auth, String email) {
        TextInputDialog codeDialog = new TextInputDialog();
        codeDialog.setTitle("Verify your email");
        codeDialog.getDialogPane().setHeader(null);
        codeDialog.setHeaderText(null);
        codeDialog.setGraphic(null);

        Label brand = new Label("Check your inbox");
        brand.getStyleClass().add("auth-brand");
        Label tagline = new Label("We sent a 6-digit code to " + email + ".");
        tagline.getStyleClass().add("auth-tagline");
        VBox hero = new VBox(2, brand, tagline);
        hero.getStyleClass().add("auth-hero");

        TextField codeField = codeDialog.getEditor();
        codeField.setPromptText("123456");
        codeField.getStyleClass().add("auth-field");
        VBox codeGroup = authFieldGroup("Verification code", codeField);

        Label status = new Label("");
        status.getStyleClass().add("auth-status");
        status.setWrapText(true);
        status.setManaged(false);
        status.setVisible(false);

        VBox body = new VBox(14, hero, codeGroup, status);
        body.getStyleClass().add("auth-root");
        body.setPrefWidth(380);

        codeDialog.getDialogPane().setContent(body);
        codeDialog.getDialogPane().getStyleClass().add("auth-pane");
        codeDialog.getDialogPane().getStylesheets().add(getClass().getResource("/app.css").toExternalForm());

        try {
            codeDialog.initOwner(parent.getDialogPane().getScene().getWindow());
            codeDialog.initModality(javafx.stage.Modality.WINDOW_MODAL);
        } catch (Exception ignored) { /* parent not shown yet — leave defaults */ }

        final boolean[] confirmed = {false};
        Button okBtn = (Button) codeDialog.getDialogPane().lookupButton(ButtonType.OK);
        okBtn.addEventFilter(javafx.event.ActionEvent.ACTION, ev -> {
            String code = codeField.getText().trim();
            if (code.isBlank()) {
                ev.consume();
                status.getStyleClass().removeAll("auth-status-error", "auth-status-ok");
                status.getStyleClass().add("auth-status-error");
                status.setText("Enter the code from the email.");
                status.setManaged(true);
                status.setVisible(true);
                return;
            }
            try {
                auth.confirmSignUp(config.cognitoRegion(), config.cognitoClientId(), email, code);
                confirmed[0] = true;
            } catch (Exception e) {
                ev.consume();
                status.getStyleClass().removeAll("auth-status-error", "auth-status-ok");
                status.getStyleClass().add("auth-status-error");
                status.setText("Verification failed. "
                        + com.calorietracker.desktop.ui.Messages.friendly(e));
                status.setManaged(true);
                status.setVisible(true);
            }
        });

        javafx.application.Platform.runLater(codeField::requestFocus);
        codeDialog.showAndWait();
        return confirmed[0];
    }


}
