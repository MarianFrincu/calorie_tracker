package com.calorietracker.desktop.ui;

import com.calorietracker.desktop.api.ApiClient;
import com.calorietracker.desktop.model.Goal;
import com.calorietracker.desktop.model.MacroPreset;
import com.calorietracker.desktop.model.Objective;
import com.calorietracker.desktop.model.Profile;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

/**
 * Read-only summary of the current objective: goal, intensity, macro
 * preset, daily fiber + water targets, and the derived calorie + per-macro
 * gram targets. The form fields are NOT editable here - a single
 * <i>Change objective</i> button opens {@link ObjectiveEditDialog}, and
 * the new values are applied (server-side, with a fresh
 * {@code objective_history} snapshot) only after the user clicks OK in
 * that dialog.
 */
public class ObjectiveView extends BorderPane {

    private final ApiClient api;

    // Top "live" card.
    private final Label targetValue    = new Label("-");
    private final Label targetSubtitle = new Label("Loading...");
    private final Label proteinValue   = new Label("-");
    private final Label carbsValue     = new Label("-");
    private final Label fatValue       = new Label("-");

    // Read-only summary rows.
    private final Label goalLabel      = new Label("-");
    private final Label intensityLabel = new Label("-");
    private final Label presetLabel    = new Label("-");
    private final Label fiberLabel     = new Label("-");
    private final Label waterLabel     = new Label("-");

    /** Maintenance TDEE, computed from the profile - baseline for percent math. */
    private Integer tdee;
    /** Last objective loaded from the server. Edit dialog opens with these as defaults. */
    private Objective currentObjective;

    public ObjectiveView(ApiClient api) {
        this.api = api;

        // -------- Top "current target" card (gradient blue) --------
        Label targetTitle = new Label("DAILY CALORIE TARGET");
        targetTitle.getStyleClass().add("card-title");
        targetValue.getStyleClass().add("big-number");
        targetSubtitle.getStyleClass().add("muted");

        Label macrosTitle = new Label("MACRO TARGETS (grams)");
        macrosTitle.getStyleClass().add("card-title");
        proteinValue.getStyleClass().add("medium-number");
        carbsValue  .getStyleClass().add("medium-number");
        fatValue    .getStyleClass().add("medium-number");

        GridPane macros = new GridPane();
        macros.setHgap(20);
        macros.setVgap(4);
        macros.addRow(0, AiView.macroTag("PROTEIN", "protein"), proteinValue);
        macros.addRow(1, AiView.macroTag("CARBS",   "carbs"),   carbsValue);
        macros.addRow(2, AiView.macroTag("FAT",     "fat"),     fatValue);

        VBox targetCard = new VBox(8, targetTitle, targetValue, targetSubtitle, macrosTitle, macros);
        targetCard.getStyleClass().addAll("card", "target-card");
        targetCard.setMaxWidth(560);

        // -------- Read-only summary card --------
        GridPane summary = new GridPane();
        summary.setHgap(14);
        summary.setVgap(10);
        summary.addRow(0, muted("Goal"),                goalLabel);
        summary.addRow(1, muted("Intensity"),           intensityLabel);
        summary.addRow(2, muted("Macro distribution"),  presetLabel);
        summary.addRow(3, muted("Daily fiber target"),  fiberLabel);
        summary.addRow(4, muted("Daily water target"),  waterLabel);

        Label scopeHint = new Label(
                "Changing the objective applies from today onward. Past days keep the "
                + "objective that was in effect at the time - they aren't rewritten.");
        scopeHint.getStyleClass().add("muted");
        scopeHint.setWrapText(true);

        Button changeBtn = new Button("Change objective...");
        changeBtn.getStyleClass().add("primary-button");
        changeBtn.setOnAction(e -> openEditDialog());

        HBox buttonRow = new HBox(changeBtn);
        buttonRow.setAlignment(Pos.CENTER_LEFT);

        VBox summaryCard = new VBox(12, sectionTitle("Current objective"), summary, scopeHint, buttonRow);
        summaryCard.getStyleClass().add("card");
        summaryCard.setMaxWidth(560);

        VBox content = new VBox(16, targetCard, summaryCard);
        content.setPadding(new Insets(20));
        content.setAlignment(Pos.TOP_CENTER);
        ScrollPane scroll = new ScrollPane(content);
        scroll.setFitToWidth(true);
        scroll.getStyleClass().add("scroll-pane");
        setCenter(scroll);

        loadAll();
    }

    // -------- Data loading + rendering --------

    private void loadAll() {
        Async.run(api::getProfile, (Profile p) -> {
            tdee = p.tdeeMaintain();
            Async.run(api::getObjective, this::applyObjective);
        });
    }

    private void applyObjective(Objective o) {
        currentObjective = o;
        renderSummary(o);
        renderLivePreview(o);
    }

    private void renderSummary(Objective o) {
        goalLabel.setText(o.goal() == null ? "-" : pretty(o.goal()));
        intensityLabel.setText(o.goal() == Goal.MAINTAIN
                ? "at TDEE"
                : (o.goalPercent() == null ? "-" : o.goalPercent() + "%"));
        presetLabel.setText(o.macroPreset() == null ? "-" : o.macroPreset().pretty());
        fiberLabel.setText(o.dailyFiberTargetG() == null ? "-" : o.dailyFiberTargetG() + " g");
        waterLabel.setText(o.dailyWaterTargetMl() == null ? "-" : o.dailyWaterTargetMl() + " ml");
    }

    private void renderLivePreview(Objective o) {
        Integer t = o.dailyCalorieTarget();
        if (t == null || tdee == null) {
            targetValue.setText("-");
            targetSubtitle.setText("Set every field on the Profile tab first.");
            proteinValue.setText("-");
            carbsValue.setText("-");
            fatValue.setText("-");
            return;
        }
        targetValue.setText(t + " kcal/day");
        String dir = switch (o.goal() == null ? Goal.MAINTAIN : o.goal()) {
            case LOSE     -> (o.goalPercent() == null ? "?" : o.goalPercent()) + "% under TDEE";
            case GAIN     -> (o.goalPercent() == null ? "?" : o.goalPercent()) + "% over TDEE";
            case MAINTAIN -> "at TDEE";
        };
        targetSubtitle.setText("Currently " + dir + " (TDEE " + tdee + " kcal)");
        proteinValue.setText(safe(o.dailyProteinTargetG()) + " g");
        carbsValue  .setText(safe(o.dailyCarbsTargetG())   + " g");
        fatValue    .setText(safe(o.dailyFatTargetG())     + " g");
    }

    private static String safe(Integer v) { return v == null ? "-" : v.toString(); }

    private static String pretty(Goal g) {
        return switch (g) {
            case LOSE -> "Lose weight";
            case GAIN -> "Gain weight";
            case MAINTAIN -> "Maintain";
        };
    }

    private void openEditDialog() {
        if (tdee == null || currentObjective == null) {
            Async.showWarning("Profile + objective are still loading. Try again in a moment.");
            return;
        }
        new ObjectiveEditDialog(api, currentObjective, tdee).showAndWait()
                .ifPresent(updated -> applyObjective(updated));
    }

    // -------- Small layout helpers --------

    private Label muted(String text) {
        Label l = new Label(text);
        l.getStyleClass().add("muted");
        return l;
    }
    private Label sectionTitle(String text) {
        Label l = new Label(text);
        l.getStyleClass().add("section-title");
        return l;
    }

    /**
     * Modal editor for the objective. Mirrors the form that used to live
     * inline in ObjectiveView, plus a live preview of the resulting
     * calorie + macro targets. The server is only called when the user
     * clicks OK; Cancel discards.
     */
    private static final class ObjectiveEditDialog extends javafx.scene.control.Dialog<Objective> {

        private static final Integer[] PERCENT_CHOICES = { 10, 15, 20, 25, 30 };

        ObjectiveEditDialog(ApiClient api, Objective initial, Integer tdee) {
            setTitle("Change objective");
            setHeaderText("Pick a goal, intensity and macro distribution. The new values "
                    + "apply from today onwards; past days keep their historical target.");
            com.calorietracker.desktop.AppContext.prepareDialog(this);

            javafx.scene.control.ComboBox<Goal> goalBox =
                    new javafx.scene.control.ComboBox<>(javafx.collections.FXCollections.observableArrayList(Goal.values()));
            goalBox.setConverter(new javafx.util.StringConverter<>() {
                @Override public String toString(Goal g) { return g == null ? "" : pretty(g); }
                @Override public Goal fromString(String s) { return null; }
            });
            javafx.scene.control.ComboBox<Integer> percentBox =
                    new javafx.scene.control.ComboBox<>(javafx.collections.FXCollections.observableArrayList(PERCENT_CHOICES));
            javafx.scene.control.ComboBox<MacroPreset> presetBox =
                    new javafx.scene.control.ComboBox<>(javafx.collections.FXCollections.observableArrayList(MacroPreset.values()));
            presetBox.setConverter(new javafx.util.StringConverter<>() {
                @Override public String toString(MacroPreset p) { return p == null ? "" : p.pretty(); }
                @Override public MacroPreset fromString(String s) { return null; }
            });
            javafx.scene.control.Spinner<Integer> fiberSpinner = new javafx.scene.control.Spinner<>(0, 200, 30);
            javafx.scene.control.Spinner<Integer> waterSpinner = new javafx.scene.control.Spinner<>(500, 6000, 2000, 100);
            fiberSpinner.setEditable(true);
            waterSpinner.setEditable(true);

            // Seed with the user's current objective so opening + immediately closing is a no-op.
            goalBox.setValue(initial.goal() == null ? Goal.MAINTAIN : initial.goal());
            percentBox.setValue(snapPercent(initial.goalPercent() == null ? 20 : initial.goalPercent()));
            presetBox.setValue(initial.macroPreset() == null ? MacroPreset.BALANCED : initial.macroPreset());
            if (initial.dailyFiberTargetG() != null) fiberSpinner.getValueFactory().setValue(initial.dailyFiberTargetG());
            if (initial.dailyWaterTargetMl() != null) waterSpinner.getValueFactory().setValue(initial.dailyWaterTargetMl());

            GridPane form = new GridPane();
            form.setHgap(14);
            form.setVgap(12);
            form.addRow(0, mutedLabel("Goal"),               goalBox);
            form.addRow(1, mutedLabel("Intensity"),          percentBox);
            form.addRow(2, mutedLabel("Macro distribution"), presetBox);
            form.addRow(3, mutedLabel("Daily fiber (g)"),    fiberSpinner);
            form.addRow(4, mutedLabel("Daily water (ml)"),   waterSpinner);

            Label hint = new Label("Intensity is the % of TDEE you cut (lose) or add (gain). "
                    + "Ignored when goal = Maintain.");
            hint.getStyleClass().add("muted");
            hint.setWrapText(true);

            // Live preview line.
            Label preview = new Label();
            preview.getStyleClass().add("medium-number");
            preview.setStyle("-fx-text-fill: #2c5282;");
            Label previewTitle = new Label("Preview:");
            previewTitle.getStyleClass().add("muted");
            HBox previewRow = new HBox(8, previewTitle, preview);
            previewRow.setAlignment(Pos.CENTER_LEFT);
            previewRow.setPadding(new Insets(8, 12, 8, 12));
            previewRow.setStyle("-fx-background-color: #f7fafc; -fx-background-radius: 6;");

            Runnable refresh = () -> {
                int pct = goalBox.getValue() == Goal.MAINTAIN ? 0 : percentBox.getValue();
                double adj = switch (goalBox.getValue()) {
                    case LOSE -> -pct / 100.0;
                    case GAIN -> +pct / 100.0;
                    case MAINTAIN -> 0.0;
                };
                int kcal = (int) Math.round(tdee * (1.0 + adj));
                MacroPreset p = presetBox.getValue();
                int protein = (int) Math.round(kcal * (p.proteinPercent / 100.0) / 4.0);
                int carbs   = (int) Math.round(kcal * (p.carbsPercent   / 100.0) / 4.0);
                int fat     = (int) Math.round(kcal * (p.fatPercent     / 100.0) / 9.0);
                preview.setText(kcal + " kcal/day · P " + protein + " g · C " + carbs + " g · F " + fat + " g");
            };
            goalBox.valueProperty().addListener((o, a, b) -> refresh.run());
            percentBox.valueProperty().addListener((o, a, b) -> refresh.run());
            presetBox.valueProperty().addListener((o, a, b) -> refresh.run());
            fiberSpinner.valueProperty().addListener((o, a, b) -> refresh.run());
            waterSpinner.valueProperty().addListener((o, a, b) -> refresh.run());
            refresh.run();

            VBox content = new VBox(12, form, hint, previewRow);
            content.setPadding(new Insets(14));
            content.setMinWidth(520);
            getDialogPane().setContent(content);
            getDialogPane().getButtonTypes().addAll(
                    javafx.scene.control.ButtonType.OK, javafx.scene.control.ButtonType.CANCEL);

            // Do the POST inside an event filter so we can keep the dialog
            // open + show a friendly error if the server rejects.
            Objective[] saved = new Objective[1];
            javafx.scene.control.Button okBtn = (javafx.scene.control.Button) getDialogPane()
                    .lookupButton(javafx.scene.control.ButtonType.OK);
            okBtn.addEventFilter(javafx.event.ActionEvent.ACTION, ev -> {
                try {
                    int pct = goalBox.getValue() == Goal.MAINTAIN ? 0 : percentBox.getValue();
                    com.calorietracker.desktop.model.UpdateObjectiveRequest req =
                            new com.calorietracker.desktop.model.UpdateObjectiveRequest(
                                    goalBox.getValue(), pct, presetBox.getValue(),
                                    fiberSpinner.getValue(), waterSpinner.getValue());
                    saved[0] = api.updateObjective(req);
                } catch (Exception ex) {
                    Async.showError(ex);
                    ev.consume();
                }
            });

            setResultConverter(bt -> bt == javafx.scene.control.ButtonType.OK ? saved[0] : null);
        }

        private static int snapPercent(int p) {
            int closest = PERCENT_CHOICES[0];
            int best = Math.abs(p - closest);
            for (int c : PERCENT_CHOICES) {
                int d = Math.abs(p - c);
                if (d < best) { best = d; closest = c; }
            }
            return closest;
        }

        private static Label mutedLabel(String text) {
            Label l = new Label(text);
            l.getStyleClass().add("muted");
            l.setMinWidth(150);
            return l;
        }
    }
}
