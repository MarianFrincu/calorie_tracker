package com.calorietracker.desktop.ui;

import com.calorietracker.desktop.model.DaySummary;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

/**
 * Right-side card: today's calorie target + 4 macro rows shown as
 * "consumed / target" with a thin progress bar each.
 */
public class SummaryCard extends VBox {

    private final Label bigNumber = new Label("- / -");
    private final Label remaining = new Label("");
    private final ProgressBar progress = new ProgressBar(0);

    private final MacroRow proteinRow = new MacroRow("Protein", "protein");
    private final MacroRow carbsRow   = new MacroRow("Carbs",   "carbs");
    private final MacroRow fatRow     = new MacroRow("Fat",     "fat");
    private final MacroRow fiberRow   = new MacroRow("Fiber",   "fiber");

    public SummaryCard() {
        setSpacing(10);
        getStyleClass().add("card");

        Label title = new Label("TODAY");
        title.getStyleClass().add("card-title");

        bigNumber.getStyleClass().add("big-number");
        progress.setMaxWidth(Double.MAX_VALUE);
        progress.setPrefHeight(14);
        remaining.getStyleClass().add("muted");

        Label macrosTitle = new Label("MACROS");
        macrosTitle.getStyleClass().add("card-title");
        VBox macros = new VBox(14, proteinRow, carbsRow, fatRow, fiberRow);

        getChildren().addAll(title, bigNumber, progress, remaining, macrosTitle, macros);
    }

    public void update(DaySummary s) {
        int consumed = s.consumedKcal() == null ? 0 : s.consumedKcal();
        if (s.dailyCalorieTarget() != null) {
            int target = s.dailyCalorieTarget();
            bigNumber.setText(consumed + " / " + target + " kcal");
            progress.setProgress(target > 0 ? Math.min(1.0, (double) consumed / target) : 0);
            int rem = s.remainingKcal() == null ? 0 : s.remainingKcal();
            remaining.setText(rem + " kcal remaining");
        } else {
            bigNumber.setText(consumed + " kcal");
            progress.setProgress(0);
            remaining.setText("Set your profile + objective to compute a target");
        }
        proteinRow.update(s.totalProtein(), s.proteinTargetG());
        carbsRow  .update(s.totalCarbs(),   s.carbsTargetG());
        fatRow    .update(s.totalFat(),     s.fatTargetG());
        fiberRow  .update(s.totalFiber(),   s.fiberTargetG());
    }

    /** One row per macro: name on the left, "X / Y g" on the right, mini bar below. */
    private static final class MacroRow extends VBox {
        private final Label valueLabel = new Label("-");
        private final ProgressBar bar = new ProgressBar(0);

        MacroRow(String name, String cssVariant) {
            setSpacing(5);
            Label nameLabel = new Label(name);
            nameLabel.getStyleClass().add("macro-name");
            valueLabel.getStyleClass().add("macro-value");
            Region spacer = new Region();
            HBox.setHgrow(spacer, Priority.ALWAYS);
            HBox top = new HBox(8, nameLabel, spacer, valueLabel);
            top.setAlignment(Pos.CENTER_LEFT);

            bar.setMaxWidth(Double.MAX_VALUE);
            bar.setPrefHeight(14);
            bar.getStyleClass().addAll("macro-bar", cssVariant); // colors via .progress-bar.<variant>

            getChildren().addAll(top, bar);
        }

        void update(double consumed, Integer target) {
            if (target == null || target <= 0) {
                valueLabel.setText(fmt(consumed) + " g  (no target)");
                valueLabel.getStyleClass().setAll("macro-value-muted");
                bar.setProgress(0);
                return;
            }
            valueLabel.setText(fmt(consumed) + " / " + target + " g");
            valueLabel.getStyleClass().setAll("macro-value");
            bar.setProgress(Math.min(1.0, consumed / (double) target));
        }

        private static String fmt(double v) {
            return v == Math.floor(v) ? String.valueOf((int) v) : String.format("%.1f", v);
        }
    }
}
