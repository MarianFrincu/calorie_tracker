package com.calorietracker.desktop;

import com.calorietracker.desktop.api.ApiClient;
import com.calorietracker.desktop.api.AuthSession;
import com.calorietracker.desktop.api.CognitoAuthService;
import com.calorietracker.desktop.api.CognitoException;
import com.calorietracker.desktop.model.Profile;
import com.calorietracker.desktop.ui.AiView;
import com.calorietracker.desktop.ui.DayView;
import com.calorietracker.desktop.ui.FoodExplorerView;
import com.calorietracker.desktop.ui.ObjectiveView;
import com.calorietracker.desktop.ui.PasswordInput;
import com.calorietracker.desktop.ui.PasswordRules;
import com.calorietracker.desktop.ui.ProfileView;
import com.calorietracker.desktop.ui.RecipesView;
import com.calorietracker.desktop.ui.ReportsView;
import com.calorietracker.desktop.ui.WeightView;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.function.Consumer;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Alert;
import javafx.scene.control.Dialog;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.control.Separator;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TextField;
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
    /** Cognito session; null in auth.mode=none (dev backend without security). */
    private AuthSession session;
    /** True while a sign-in dialog is open, so an expiry notice can't stack a second one. */
    private boolean loginShowing;
    private Stage stage;
    private BorderPane root;
    private VBox sidebar;
    private final List<Button> navButtons = new ArrayList<>();

    /** Cached so chat history doesn't disappear when the user leaves the tab. */
    private AiView aiView;

    /** True until the user has saved a complete profile. While true, only the
     *  Profile tab is enabled — every other view depends on BMR/TDEE existing. */
    private boolean onboardingLock = false;

    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void start(Stage stage) {
        this.stage = stage;
        // Make the main stage globally reachable so child dialogs can call
        // initOwner(...) and we can restore maximize/fullscreen after they close.
        AppContext.setMainStage(stage);

        try {
            if ("cognito".equalsIgnoreCase(config.authMode())) {
                session = new AuthSession(new CognitoAuthService(
                        config.cognitoUserPoolId(), config.cognitoClientId()));
                api = new ApiClient(config.apiBaseUrl(), session);
            } else {
                api = new ApiClient(config.apiBaseUrl(), null);
            }
        } catch (RuntimeException e) {
            // Misconfiguration (plain-http URL in cognito mode, bad pool id, missing
            // client id...): say exactly what is wrong instead of failing later.
            Alert alert = new Alert(Alert.AlertType.ERROR, e.getMessage());
            alert.setHeaderText("Calorie Tracker can't start");
            alert.showAndWait();
            Platform.exit();
            return;
        }

        if (session != null) {
            api.setOnSessionExpired(() -> Platform.runLater(this::sessionExpired));
            // Resume the previous launch's session (refreshing it if the access
            // token has lapsed), then probe it: it may have been revoked or
            // issued by a different user pool. On any failure, sign in afresh.
            if (!(session.restore() && sessionValid())) {
                session.clearLocal();
                if (!login()) {
                    Platform.exit();
                    return;
                }
            }
        }

        // Decide what the user sees first based on profile completeness. New
        // signups have empty sex/age/etc. — landing them anywhere but Profile
        // would 404 charts and produce the "nothing renders" effect.
        onboardingLock = !isProfileComplete();

        root = new BorderPane();
        sidebar = buildSidebar();
        root.setLeft(sidebar);
        if (onboardingLock) showProfile(); else showDay();

        Scene scene = new Scene(root, 1280, 820);
        scene.getStylesheets().add(getClass().getResource("/app.css").toExternalForm());
        stage.setScene(scene);
        stage.setTitle("Calorie Tracker");
        stage.show();
    }

    /** True iff a GET /api/profile succeeds with the current token. */
    private boolean sessionValid() {
        try {
            api.getProfile();
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /** True iff the user has saved every body-stat needed for BMR/TDEE. */
    private boolean isProfileComplete() {
        try {
            Profile p = api.getProfile();
            return p != null
                    && p.sex() != null
                    && p.age() != null
                    && p.heightCm() != null
                    && p.weightKg() != null
                    && p.activityLevel() != null;
        } catch (Exception e) {
            // If we can't tell, be safe and lock to onboarding.
            return false;
        }
    }

    /**
     * Called by ProfileView right after a successful save so the sidebar can
     * unlock the other tabs without requiring a full app restart.
     */
    private void onProfileSaved() {
        if (onboardingLock && isProfileComplete()) {
            onboardingLock = false;
            applyOnboardingLock();
        }
    }

    /** Disable every nav button except Profile while we're in onboarding-lock. */
    private void applyOnboardingLock() {
        for (Button b : navButtons) {
            boolean isProfile = "Profile".equals(b.getText());
            b.setDisable(onboardingLock && !isProfile);
            if (onboardingLock && !isProfile) {
                if (!b.getStyleClass().contains("nav-locked")) b.getStyleClass().add("nav-locked");
            } else {
                b.getStyleClass().remove("nav-locked");
            }
        }
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
        // Disable non-Profile buttons if we're locked into onboarding so the
        // user can only complete the profile before navigating elsewhere.
        applyOnboardingLock();
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
    private void showProfile()   {
        swap(4, new ProfileView(api, this::onProfileSaved, this::deleteAccountData, this::onAccountDeleted,
                session != null));
    }
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
     * Revoke the refresh token at Cognito, drop the cached AiView (chat history
     * would leak across users), and re-show the sign-in dialog. If the user
     * cancels out of it, close the app.
     */
    private void signOut() {
        // Revocation is a network call; don't block the UI on it.
        AuthSession s = session;
        Thread revoke = new Thread(s::signOut, "sign-out");
        revoke.setDaemon(true);
        revoke.start();
        showLoginAgain();
    }

    /** The refresh token was rejected (revoked, expired, user deleted): ask for a password again. */
    private void sessionExpired() {
        if (loginShowing || session == null) return;
        Alert alert = new Alert(Alert.AlertType.INFORMATION, "Your session has expired. Please sign in again.");
        alert.setHeaderText(null);
        AppContext.prepareDialog(alert);
        alert.showAndWait();
        showLoginAgain();
    }

    /** Shared tail of sign-out / expiry / account deletion. */
    private void showLoginAgain() {
        aiView = null;
        // Hide the main window so only the login dialog is visible.
        if (stage != null) stage.hide();
        if (login()) {
            // Re-evaluate onboarding state for the freshly signed-in user — a
            // different account may need to complete the profile, or may
            // already be set up. Build sidebar AFTER setting the lock so the
            // gating runs on the fresh button list.
            onboardingLock = !isProfileComplete();
            // Re-assign the field too, otherwise toggleSidebarCollapsed keeps
            // mutating the OLD VBox that is no longer on screen.
            sidebar = buildSidebar();
            root.setLeft(sidebar);
            if (onboardingLock) showProfile(); else showDay();
            if (stage != null) stage.show();
        } else {
            // User cancelled the sign-in dialog: quit the app entirely.
            javafx.application.Platform.exit();
        }
    }

    // ---------------- Cognito sign-in / sign-up ----------------

    private VBox authFieldGroup(String labelText, Node field) {
        Label l = new Label(labelText);
        l.getStyleClass().add("auth-field-label");
        VBox box = new VBox(6, l, field);
        box.getStyleClass().add("auth-field-group");
        return box;
    }

    private static final String RULES_NOT_MET = "The password doesn't meet all the requirements listed under it yet.";

    /**
     * Runs a network call off the JavaFX thread (Cognito can take seconds) and
     * delivers the outcome back on it. The UI stays responsive and the caller
     * disables its buttons meanwhile.
     */
    private static <T> void background(Callable<T> work, Consumer<T> onOk, Consumer<Throwable> onError) {
        Task<T> task = new Task<>() {
            @Override protected T call() throws Exception { return work.call(); }
        };
        task.setOnSucceeded(e -> onOk.accept(task.getValue()));
        task.setOnFailed(e -> onError.accept(task.getException()));
        Thread t = new Thread(task, "auth-call");
        t.setDaemon(true);
        t.start();
    }

    private static Label statusLabel() {
        Label status = new Label("");
        status.getStyleClass().add("auth-status");
        status.setWrapText(true);
        status.setManaged(false);
        status.setVisible(false);
        return status;
    }

    private static void showStatus(Label status, String msg, boolean isError) {
        status.getStyleClass().removeAll("auth-status-error", "auth-status-ok");
        status.getStyleClass().add(isError ? "auth-status-error" : "auth-status-ok");
        status.setText(msg);
        status.setManaged(true);
        status.setVisible(true);
    }

    private static void hideStatus(Label status) {
        status.setText("");
        status.setManaged(false);
        status.setVisible(false);
        status.getStyleClass().removeAll("auth-status-error", "auth-status-ok");
    }

    private boolean login() {
        loginShowing = true;
        try {
            return showLoginDialog();
        } finally {
            loginShowing = false;
        }
    }

    private boolean showLoginDialog() {
        CognitoAuthService auth = session.cognito();
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
        PasswordInput siPwd = new PasswordInput("password");
        Hyperlink forgot = new Hyperlink("Forgot password?");
        VBox signInBox = new VBox(14,
                authFieldGroup("Email", siEmail),
                authFieldGroup("Password", siPwd),
                forgot);
        signInBox.getStyleClass().add("auth-form");

        TextField suEmail = new TextField();
        suEmail.setPromptText("you@example.com");
        suEmail.getStyleClass().add("auth-field");
        PasswordInput suPwd = new PasswordInput("choose a password");
        PasswordInput suPwd2 = new PasswordInput("repeat the password");
        Label suHint = new Label("We'll email a 6-digit code to confirm your address. "
                + "Enter it on the next screen, then come back to sign in.");
        suHint.setWrapText(true);
        suHint.getStyleClass().add("auth-hint");
        VBox signUpBox = new VBox(14,
                authFieldGroup("Email", suEmail),
                authFieldGroup("Password", suPwd),
                authFieldGroup("Confirm password", suPwd2),
                new PasswordRules(suPwd.textProperty(), suPwd2.textProperty()),
                suHint);
        signUpBox.getStyleClass().add("auth-form");

        Tab signInTab = new Tab("Sign in", signInBox);
        Tab registerTab = new Tab("Register", signUpBox);
        TabPane tabs = new TabPane(signInTab, registerTab);
        tabs.getStyleClass().add("auth-tabs");
        tabs.getTabs().forEach(t -> t.setClosable(false));

        Label status = statusLabel();

        VBox content = new VBox(14, heroBox, tabs, status);
        content.getStyleClass().add("auth-root");
        content.setPrefWidth(440);

        ButtonType submit = new ButtonType("Continue", javafx.scene.control.ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().setContent(content);
        dialog.getDialogPane().getButtonTypes().addAll(submit, ButtonType.CANCEL);
        dialog.getDialogPane().getStyleClass().add("auth-pane");
        dialog.getDialogPane().getStylesheets().add(getClass().getResource("/app.css").toExternalForm());

        Runnable clearStatus = () -> hideStatus(status);
        tabs.getSelectionModel().selectedItemProperty().addListener((o, a, b) -> clearStatus.run());
        siEmail.textProperty().addListener((o, a, b) -> clearStatus.run());
        siPwd.textProperty().addListener((o, a, b) -> clearStatus.run());
        suEmail.textProperty().addListener((o, a, b) -> clearStatus.run());
        suPwd.textProperty().addListener((o, a, b) -> clearStatus.run());
        suPwd2.textProperty().addListener((o, a, b) -> clearStatus.run());

        final boolean[] signedIn = {false};
        Button submitBtn = (Button) dialog.getDialogPane().lookupButton(submit);

        forgot.setOnAction(e -> {
            String email = promptResetPassword(dialog, auth, siEmail.getText().trim());
            if (email != null) {
                siEmail.setText(email);
                siPwd.clear();
                showStatus(status, "Password changed — sign in with the new one.", false);
                siPwd.requestFocus();
            }
        });

        submitBtn.addEventFilter(javafx.event.ActionEvent.ACTION, ev -> {
            // Every path handles closing itself once the (async) call returns.
            ev.consume();
            if (tabs.getSelectionModel().getSelectedIndex() == 1) {
                String email = suEmail.getText().trim();
                String pwd = suPwd.getText();
                if (email.isBlank() || pwd.isBlank()) {
                    showStatus(status, "Email and password are required.", true);
                    return;
                }
                if (!PasswordRules.meetsAll(pwd)) {
                    showStatus(status, RULES_NOT_MET, true);
                    return;
                }
                if (!pwd.equals(suPwd2.getText())) {
                    showStatus(status, "The two passwords don't match.", true);
                    return;
                }
                submitBtn.setDisable(true);
                background(() -> { auth.signUp(email, pwd); return null; }, ignored -> {
                    submitBtn.setDisable(false);
                    suPwd.clear();
                    suPwd2.clear();
                    if (promptVerifyCode(dialog, auth, email)) {
                        siEmail.setText(email);
                        siPwd.clear();
                        tabs.getSelectionModel().select(signInTab);
                        showStatus(status, "Account confirmed — sign in with your password.", false);
                        siPwd.requestFocus();
                    }
                }, err -> {
                    submitBtn.setDisable(false);
                    showStatus(status, com.calorietracker.desktop.ui.Messages.friendly(err), true);
                });
            } else {
                String email = siEmail.getText().trim();
                String pwd = siPwd.getText();
                if (email.isBlank() || pwd.isBlank()) {
                    showStatus(status, "Email and password are required.", true);
                    return;
                }
                submitBtn.setDisable(true);
                showStatus(status, "Signing in…", false);
                background(() -> auth.login(email, pwd), tokens -> {
                    siPwd.clear(); // don't keep the password in a live control
                    session.signedIn(tokens);
                    signedIn[0] = true;
                    dialog.setResult(submit);
                    dialog.close();
                }, err -> {
                    submitBtn.setDisable(false);
                    if (err instanceof CognitoException ce && "UserNotConfirmedException".equals(ce.type())) {
                        // Signed up but never entered the code: send a fresh one and ask for it.
                        showStatus(status, "Your email isn't confirmed yet. We've sent you a new code.", false);
                        background(() -> { auth.resendCode(email); return null; }, ignored -> {
                            if (promptVerifyCode(dialog, auth, email)) {
                                showStatus(status, "Account confirmed — sign in with your password.", false);
                                siPwd.requestFocus();
                            }
                        }, resendErr -> showStatus(status,
                                com.calorietracker.desktop.ui.Messages.friendly(resendErr), true));
                        return;
                    }
                    showStatus(status, com.calorietracker.desktop.ui.Messages.friendly(err), true);
                });
            }
        });

        Platform.runLater(siEmail::requestFocus);
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
        Dialog<ButtonType> codeDialog = new Dialog<>();
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

        TextField codeField = new TextField();
        codeField.setPromptText("123456");
        codeField.getStyleClass().add("auth-field");
        VBox codeGroup = authFieldGroup("Verification code", codeField);
        Hyperlink resend = new Hyperlink("Resend code");

        Label status = statusLabel();

        VBox body = new VBox(14, hero, codeGroup, resend, status);
        body.getStyleClass().add("auth-root");
        body.setPrefWidth(380);

        codeDialog.getDialogPane().setContent(body);
        codeDialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        codeDialog.getDialogPane().getStyleClass().add("auth-pane");
        codeDialog.getDialogPane().getStylesheets().add(getClass().getResource("/app.css").toExternalForm());
        ownBy(codeDialog, parent);

        resend.setOnAction(e -> {
            resend.setDisable(true);
            background(() -> { auth.resendCode(email); return null; }, ignored -> {
                resend.setDisable(false);
                showStatus(status, "A new code is on its way.", false);
            }, err -> {
                resend.setDisable(false);
                showStatus(status, com.calorietracker.desktop.ui.Messages.friendly(err), true);
            });
        });

        final boolean[] confirmed = {false};
        Button okBtn = (Button) codeDialog.getDialogPane().lookupButton(ButtonType.OK);
        okBtn.addEventFilter(javafx.event.ActionEvent.ACTION, ev -> {
            ev.consume();
            String code = codeField.getText().trim();
            if (code.isBlank()) {
                showStatus(status, "Enter the code from the email.", true);
                return;
            }
            okBtn.setDisable(true);
            background(() -> { auth.confirmSignUp(email, code); return null; }, ignored -> {
                confirmed[0] = true;
                codeDialog.setResult(ButtonType.OK);
                codeDialog.close();
            }, err -> {
                okBtn.setDisable(false);
                showStatus(status, com.calorietracker.desktop.ui.Messages.friendly(err), true);
            });
        });

        Platform.runLater(codeField::requestFocus);
        codeDialog.showAndWait();
        return confirmed[0];
    }

    /**
     * Two-step forgot-password flow: request a code by email, then set a new
     * password with it.
     *
     * @return the email whose password was changed, or null if cancelled
     */
    private String promptResetPassword(Dialog<?> parent, CognitoAuthService auth, String prefillEmail) {
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("Reset password");
        dialog.getDialogPane().setHeader(null);
        dialog.setHeaderText(null);
        dialog.setGraphic(null);

        Label brand = new Label("Reset your password");
        brand.getStyleClass().add("auth-brand");
        Label tagline = new Label("We'll email you a code to set a new password.");
        tagline.getStyleClass().add("auth-tagline");
        tagline.setWrapText(true);
        VBox hero = new VBox(2, brand, tagline);
        hero.getStyleClass().add("auth-hero");

        TextField email = new TextField(prefillEmail);
        email.setPromptText("you@example.com");
        email.getStyleClass().add("auth-field");
        TextField code = new TextField();
        code.setPromptText("123456");
        code.getStyleClass().add("auth-field");
        PasswordInput pwd = new PasswordInput("choose a new password");
        PasswordInput pwd2 = new PasswordInput("repeat the password");

        VBox requestStep = new VBox(14, authFieldGroup("Email", email));
        VBox confirmStep = new VBox(14,
                authFieldGroup("Reset code", code),
                authFieldGroup("New password", pwd),
                authFieldGroup("Confirm new password", pwd2),
                new PasswordRules(pwd.textProperty(), pwd2.textProperty()));
        confirmStep.setVisible(false);
        confirmStep.setManaged(false);
        Label status = statusLabel();

        VBox body = new VBox(14, hero, requestStep, confirmStep, status);
        body.getStyleClass().add("auth-root");
        body.setPrefWidth(400);

        ButtonType next = new ButtonType("Send code", javafx.scene.control.ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().setContent(body);
        dialog.getDialogPane().getButtonTypes().addAll(next, ButtonType.CANCEL);
        dialog.getDialogPane().getStyleClass().add("auth-pane");
        dialog.getDialogPane().getStylesheets().add(getClass().getResource("/app.css").toExternalForm());
        ownBy(dialog, parent);

        final String[] changedFor = {null};
        final boolean[] codeSent = {false};
        Button nextBtn = (Button) dialog.getDialogPane().lookupButton(next);
        nextBtn.addEventFilter(javafx.event.ActionEvent.ACTION, ev -> {
            ev.consume();
            String address = email.getText().trim();
            if (!codeSent[0]) {
                if (address.isBlank()) {
                    showStatus(status, "Enter the email you registered with.", true);
                    return;
                }
                nextBtn.setDisable(true);
                background(() -> { auth.forgotPassword(address); return null; }, ignored -> {
                    codeSent[0] = true;
                    email.setDisable(true);
                    confirmStep.setVisible(true);
                    confirmStep.setManaged(true);
                    tagline.setText("Enter the code sent to " + address + " and choose a new password.");
                    nextBtn.setText("Set new password");
                    nextBtn.setDisable(false);
                    showStatus(status, "If that address has an account, a reset code is on its way.", false);
                    dialog.getDialogPane().getScene().getWindow().sizeToScene();
                    code.requestFocus();
                }, err -> {
                    nextBtn.setDisable(false);
                    showStatus(status, com.calorietracker.desktop.ui.Messages.friendly(err), true);
                });
                return;
            }
            if (code.getText().isBlank() || pwd.getText().isEmpty()) {
                showStatus(status, "Enter the code and a new password.", true);
                return;
            }
            if (!PasswordRules.meetsAll(pwd.getText())) {
                showStatus(status, RULES_NOT_MET, true);
                return;
            }
            if (!pwd.getText().equals(pwd2.getText())) {
                showStatus(status, "The two passwords don't match.", true);
                return;
            }
            String newPwd = pwd.getText();
            String theCode = code.getText().trim();
            nextBtn.setDisable(true);
            background(() -> { auth.confirmForgotPassword(address, theCode, newPwd); return null; }, ignored -> {
                pwd.clear();
                pwd2.clear();
                changedFor[0] = address;
                dialog.setResult(next);
                dialog.close();
            }, err -> {
                nextBtn.setDisable(false);
                showStatus(status, com.calorietracker.desktop.ui.Messages.friendly(err), true);
            });
        });

        Platform.runLater(email::requestFocus);
        dialog.showAndWait();
        return changedFor[0];
    }

    private static void ownBy(Dialog<?> child, Dialog<?> parent) {
        try {
            child.initOwner(parent.getDialogPane().getScene().getWindow());
            child.initModality(javafx.stage.Modality.WINDOW_MODAL);
        } catch (Exception ignored) { /* parent not shown yet — leave defaults */ }
    }

    // ---------------- Account deletion ----------------

    /**
     * Runs on a background thread (ProfileView calls it via Async). Server data
     * first: if the Cognito identity went first and the data delete then
     * failed, the user could never sign in again to retry.
     */
    private Void deleteAccountData() throws Exception {
        api.deleteProfile();
        if (session != null) {
            String token = session.accessToken();
            if (token != null) session.cognito().deleteUser(token);
            session.clearLocal();
        }
        return null;
    }

    /** Back on the FX thread after a successful deletion. */
    private void onAccountDeleted() {
        if (session != null) {
            showLoginAgain();
            return;
        }
        // Dev mode: the backend recreates an empty dev-user; restart onboarding.
        aiView = null;
        onboardingLock = true;
        sidebar = buildSidebar();
        root.setLeft(sidebar);
        showProfile();
    }
}
