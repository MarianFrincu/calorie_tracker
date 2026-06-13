package com.calorietracker.desktop.ui;

import com.calorietracker.desktop.api.ApiClient;
import com.calorietracker.desktop.model.DaySummary;
import com.calorietracker.desktop.model.DaySummary.WaterSummary;
import java.time.LocalDate;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.TextField;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

/**
 * Right-side card: water totals, quick + custom add, and per-entry delete chips
 * so the user can both put water in and take it back out.
 */
public class WaterCard extends VBox {

    private final ApiClient api;
    private final Runnable onChange;
    private final Label bigNumber = new Label("- / - ml");
    private final ProgressBar progress = new ProgressBar(0);
    private final FlowPane entriesPane = new FlowPane(6, 6);
    private final TextField customField = new TextField();
    private LocalDate currentDate;

    public WaterCard(ApiClient api, Runnable onChange) {
        this.api = api;
        this.onChange = onChange;
        setSpacing(10);
        getStyleClass().add("card");

        Label title = new Label("WATER");
        title.getStyleClass().add("card-title");

        bigNumber.getStyleClass().add("medium-number");
        progress.setMaxWidth(Double.MAX_VALUE);
        progress.setPrefHeight(10);
        progress.getStyleClass().add("water");

        // ---- quick-add buttons ----
        Button b250 = new Button("+250 ml");
        Button b500 = new Button("+500 ml");
        b250.setOnAction(e -> addWater(250));
        b500.setOnAction(e -> addWater(500));
        HBox quickRow = new HBox(8, b250, b500);

        // ---- custom amount ----
        customField.setPromptText("ml");
        customField.setPrefWidth(80);
        Button customAdd = new Button("Add");
        customAdd.setOnAction(e -> addCustom());
        customField.setOnAction(e -> addCustom());
        HBox customRow = new HBox(8, new Label("Custom:"), customField, customAdd);

        // ---- today's entries (each a delete-on-click chip) ----
        Label entriesTitle = new Label("Today's intake (click to remove)");
        entriesTitle.getStyleClass().add("card-title");
        entriesPane.setHgap(6);
        entriesPane.setVgap(6);
        HBox.setHgrow(entriesPane, Priority.ALWAYS);

        getChildren().addAll(title, bigNumber, progress, quickRow, customRow, entriesTitle, entriesPane);
    }

    public void update(WaterSummary w, LocalDate date) {
        this.currentDate = date;
        bigNumber.setText(w.totalMl() + " / " + w.targetMl() + " ml");
        double frac = w.targetMl() > 0 ? (double) w.totalMl() / w.targetMl() : 0;
        progress.setProgress(Math.min(1.0, frac));

        entriesPane.getChildren().clear();
        if (w.entries() == null || w.entries().isEmpty()) {
            Label empty = new Label("(nothing yet)");
            empty.getStyleClass().add("muted");
            entriesPane.getChildren().add(empty);
        } else {
            for (DaySummary.WaterEntry entry : w.entries()) {
                entriesPane.getChildren().add(chip(entry));
            }
        }
    }

    private Button chip(DaySummary.WaterEntry entry) {
        Button btn = new Button(entry.ml() + " ml  x");
        btn.getStyleClass().add("water-chip");
        btn.setOnAction(e -> Async.run(() -> {
            api.deleteWater(entry.id());
            return null;
        }, ignored -> onChange.run()));
        return btn;
    }

    private void addWater(int ml) {
        if (currentDate == null) return;
        Async.run(() -> {
            api.addWater(currentDate, ml);
            return null;
        }, ignored -> onChange.run());
    }

    private void addCustom() {
        if (currentDate == null) return;
        String s = customField.getText().trim();
        if (s.isEmpty()) return;
        try {
            int ml = Integer.parseInt(s);
            if (ml <= 0) {
                Async.showError(new RuntimeException("Enter a positive number of millilitres."));
                return;
            }
            Async.run(() -> {
                api.addWater(currentDate, ml);
                return null;
            }, ignored -> {
                customField.clear();
                onChange.run();
            });
        } catch (NumberFormatException ex) {
            Async.showError(new RuntimeException("\"" + s + "\" is not a number."));
        }
    }

}
