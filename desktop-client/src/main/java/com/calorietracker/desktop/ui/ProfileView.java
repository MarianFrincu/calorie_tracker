package com.calorietracker.desktop.ui;

import com.calorietracker.desktop.api.ApiClient;
import com.calorietracker.desktop.model.ActivityLevel;
import com.calorietracker.desktop.model.NutritionCalc;
import com.calorietracker.desktop.model.Sex;
import com.calorietracker.desktop.model.UpdateProfileRequest;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Spinner;
import javafx.scene.control.TextField;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

/**
 * Body stats only. Live-previews BMR and maintenance TDEE as the user changes
 * inputs. The goal/macros/fiber/water settings live in the Objective view.
 */
public class ProfileView extends BorderPane {

    private final ApiClient api;

    private final TextField nameField = new TextField();
    private final ComboBox<Sex> sexBox = new ComboBox<>(FXCollections.observableArrayList(Sex.values()));
    private final Spinner<Integer> ageSpinner = new Spinner<>(5, 110, 25);
    private final Spinner<Double> heightSpinner = new Spinner<>(120.0, 230.0, 175.0, 0.5);
    private final Spinner<Double> weightSpinner = new Spinner<>(30.0, 250.0, 75.0, 0.1);
    private final ComboBox<ActivityLevel> activityBox = new ComboBox<>(FXCollections.observableArrayList(ActivityLevel.values()));

    private final Label bmrValue = new Label("-");
    private final Label tdeeValue = new Label("-");
    private final Label savedHint = new Label(" ");

    private Integer lastSavedBmr;

    public ProfileView(ApiClient api) {
        this.api = api;
        ageSpinner.setEditable(true);
        heightSpinner.setEditable(true);
        weightSpinner.setEditable(true);
        sexBox.getSelectionModel().select(Sex.MALE);
        activityBox.getSelectionModel().select(ActivityLevel.MODERATE);

        // ---- form ----
        GridPane form = new GridPane();
        form.setHgap(14);
        form.setVgap(12);
        form.addRow(0, muted("Display name"), nameField);
        form.addRow(1, muted("Sex"), sexBox);
        form.addRow(2, muted("Age"), ageSpinner);
        form.addRow(3, muted("Height (cm)"), heightSpinner);
        form.addRow(4, muted("Weight (kg)"), weightSpinner);
        form.addRow(5, muted("Activity level"), activityBox);

        Button saveBtn = new Button("Save profile");
        saveBtn.getStyleClass().add("primary-button");
        saveBtn.setOnAction(e -> save());
        savedHint.getStyleClass().add("muted");
        HBox saveRow = new HBox(12, saveBtn, savedHint);
        saveRow.setAlignment(Pos.CENTER);

        VBox formCard = new VBox(14, sectionTitle("Your stats"), form, saveRow);
        formCard.getStyleClass().add("card");
        formCard.setMaxWidth(540);
        formCard.setAlignment(Pos.CENTER);
        form.setAlignment(Pos.CENTER);

        // ---- BMR / TDEE card ----
        Label bmrTitle = new Label("BMR");
        bmrTitle.getStyleClass().add("card-title");
        bmrValue.getStyleClass().add("medium-number");
        Label bmrSub = new Label("kcal/day burned at rest");
        bmrSub.getStyleClass().add("muted");
        VBox bmrBox = new VBox(2, bmrTitle, bmrValue, bmrSub);

        Label tdeeTitle = new Label("TDEE (maintain)");
        tdeeTitle.getStyleClass().add("card-title");
        tdeeValue.getStyleClass().add("big-number");
        Label tdeeSub = new Label("kcal/day to keep weight stable");
        tdeeSub.getStyleClass().add("muted");
        VBox tdeeBox = new VBox(2, tdeeTitle, tdeeValue, tdeeSub);

        VBox statsCard = new VBox(14, tdeeBox, bmrBox);
        statsCard.getStyleClass().addAll("card", "target-card");
        statsCard.setMaxWidth(540);

        // ---- formula explainer ----
        Label formulaTitle = new Label("How these numbers are calculated");
        formulaTitle.getStyleClass().add("section-title");

        Label bmrFormula = new Label(
                "BMR (Mifflin-St Jeor) - kcal/day burned at complete rest:\n"
                        + "    Male:   BMR = 10 x weight(kg) + 6.25 x height(cm) - 5 x age + 5\n"
                        + "    Female: BMR = 10 x weight(kg) + 6.25 x height(cm) - 5 x age - 161");
        bmrFormula.setWrapText(true);
        bmrFormula.getStyleClass().add("formula-block");

        Label tdeeFormula = new Label(
                "TDEE = BMR x activity multiplier   (kcal/day to maintain weight)\n"
                        + "    Sedentary  x 1.20   (little or no exercise)\n"
                        + "    Light      x 1.375  (1-3 light workouts/week)\n"
                        + "    Moderate   x 1.55   (3-5 workouts/week)\n"
                        + "    Active     x 1.725  (6-7 workouts/week)\n"
                        + "    Very high  x 1.90   (twice a day / physical job)");
        tdeeFormula.setWrapText(true);
        tdeeFormula.getStyleClass().add("formula-block");

        Label closing = new Label(
                "Your daily calorie target on the Objective tab is TDEE adjusted by the "
                        + "percent you pick (e.g. -20% for a cut, +15% for a lean bulk).");
        closing.setWrapText(true);
        closing.getStyleClass().add("muted");

        VBox formulaCard = new VBox(10, formulaTitle, bmrFormula, tdeeFormula, closing);
        formulaCard.getStyleClass().add("card");
        formulaCard.setMaxWidth(540);

        VBox content = new VBox(16, statsCard, formCard, formulaCard);
        content.setPadding(new Insets(20));
        content.setAlignment(Pos.TOP_CENTER);

        ScrollPane scroll = new ScrollPane(content);
        scroll.setFitToWidth(true);
        scroll.getStyleClass().add("scroll-pane");
        setCenter(scroll);

        // ---- live preview wiring ----
        sexBox.valueProperty().addListener((o, a, b) -> updatePreview());
        ageSpinner.valueProperty().addListener((o, a, b) -> updatePreview());
        heightSpinner.valueProperty().addListener((o, a, b) -> updatePreview());
        weightSpinner.valueProperty().addListener((o, a, b) -> updatePreview());
        activityBox.valueProperty().addListener((o, a, b) -> updatePreview());

        load();
    }

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

    private void updatePreview() {
        Integer bmr = NutritionCalc.bmr(sexBox.getValue(), ageSpinner.getValue(),
                heightSpinner.getValue(), weightSpinner.getValue());
        Integer tdee = NutritionCalc.tdeeMaintain(sexBox.getValue(), ageSpinner.getValue(),
                heightSpinner.getValue(), weightSpinner.getValue(), activityBox.getValue());
        bmrValue.setText(bmr == null ? "-" : bmr + " kcal");
        tdeeValue.setText(tdee == null ? "-" : tdee + " kcal");

        if (lastSavedBmr != null && bmr != null && !bmr.equals(lastSavedBmr)) {
            savedHint.setText("Unsaved changes  (saved BMR: " + lastSavedBmr + " kcal)");
        } else {
            savedHint.setText(" ");
        }
    }

    private void load() {
        // Two reads, then resolve: prefer the latest weight-log entry over
        // profile.weightKg so Profile + Weight stay in lockstep with each other.
        Async.run(api::getProfile, p -> {
            if (p.displayName() != null) nameField.setText(p.displayName());
            if (p.sex() != null) sexBox.setValue(p.sex());
            if (p.age() != null) ageSpinner.getValueFactory().setValue(p.age());
            if (p.heightCm() != null) heightSpinner.getValueFactory().setValue(p.heightCm());
            if (p.weightKg() != null) weightSpinner.getValueFactory().setValue(p.weightKg());
            if (p.activityLevel() != null) activityBox.setValue(p.activityLevel());
            lastSavedBmr = p.bmr();
            updatePreview();
            // Now overlay the latest weight log if one exists - this is the
            // single source of truth for "current body weight".
            Async.run(api::listWeight, weights -> {
                if (weights != null && !weights.isEmpty()) {
                    var latest = weights.get(weights.size() - 1);
                    if (latest.weightKg() != null) {
                        weightSpinner.getValueFactory().setValue(latest.weightKg());
                        updatePreview();
                    }
                }
            });
        });
    }

    private void save() {
        UpdateProfileRequest req = new UpdateProfileRequest(
                nameField.getText().isBlank() ? null : nameField.getText(),
                sexBox.getValue(), ageSpinner.getValue(),
                heightSpinner.getValue(), weightSpinner.getValue(),
                activityBox.getValue());
        Async.run(() -> api.updateProfile(req), p -> {
            lastSavedBmr = p.bmr();
            updatePreview();
            // Also log today's weight so the Weight tab and the Profile tab
            // never diverge. upsertWeight is idempotent per (user, date),
            // so re-saving with the same value just refreshes the row.
            Double weight = weightSpinner.getValue();
            if (weight != null) {
                Async.run(() -> api.upsertWeight(java.time.LocalDate.now(), weight),
                        ignored -> {});
            }
            Alert alert = new Alert(Alert.AlertType.INFORMATION,
                    "Saved. BMR " + p.bmr() + " kcal, maintenance TDEE " + p.tdeeMaintain() + " kcal.");
            com.calorietracker.desktop.AppContext.prepareDialog(alert);
            alert.showAndWait();
        });
    }
}
