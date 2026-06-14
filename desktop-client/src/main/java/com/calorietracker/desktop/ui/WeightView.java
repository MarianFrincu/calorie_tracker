package com.calorietracker.desktop.ui;

import com.calorietracker.desktop.api.ApiClient;
import com.calorietracker.desktop.model.WeightEntry;
import java.time.LocalDate;
import java.util.List;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.chart.CategoryAxis;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.control.Tooltip;
import javafx.scene.control.Button;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.Spinner;
import javafx.scene.control.SpinnerValueFactory;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

/**
 * Weight tracker: a LineChart of weight over time + a table to log new
 * entries (one per day - re-saving the same date updates) and remove old ones.
 */
public class WeightView extends BorderPane {

    private final ApiClient api;
    private final ObservableList<WeightEntry> entries = FXCollections.observableArrayList();
    private final SmoothAreaChart<String, Number> chart;
    private final XYChart.Series<String, Number> series = new XYChart.Series<>();
    private final DatePicker datePicker = new DatePicker(LocalDate.now());
    private final Spinner<Double> weightSpinner = new Spinner<>(30.0, 250.0, 75.0, 0.1);

    public WeightView(ApiClient api) {
        this.api = api;
        weightSpinner.setEditable(true);

        // ---- log bar ----
        Button logBtn = new Button("Save weight");
        logBtn.getStyleClass().add("primary-button");
        logBtn.setOnAction(e -> save());
        HBox logBar = new HBox(8,
                new Label("Date:"), datePicker,
                new Label("Weight (kg):"), weightSpinner,
                logBtn);
        logBar.setAlignment(Pos.CENTER_LEFT);
        logBar.getStyleClass().add("toolbar");

        // ---- chart ----
        CategoryAxis xAxis = new CategoryAxis();
        xAxis.setLabel("Date");
        NumberAxis yAxis = new NumberAxis();
        yAxis.setLabel("kg");
        yAxis.setForceZeroInRange(false);
        yAxis.setTickLabelFormatter(new javafx.util.StringConverter<>() {
            @Override public String toString(Number n) {
                return n == null ? "" : String.format("%.1f", n.doubleValue());
            }
            @Override public Number fromString(String s) { return Double.valueOf(s); }
        });
        // SmoothAreaChart for the same monotone-cubic interpolation the Reports
        // chart uses. Weight is a single series so we don't want the area fill -
        // a CSS class on this chart suppresses .chart-series-area-fill.
        chart = new SmoothAreaChart<>(xAxis, yAxis);
        chart.getStyleClass().add("weight-chart");
        chart.setTitle("Weight over time");
        chart.setLegendVisible(false);
        chart.setAnimated(false);
        chart.setCreateSymbols(true);
        chart.getData().add(series);

        // ---- table ----
        TableView<WeightEntry> table = new TableView<>(entries);
        TableColumn<WeightEntry, String> dateCol = new TableColumn<>("Date");
        dateCol.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().date()));
        dateCol.setPrefWidth(140);
        TableColumn<WeightEntry, String> kgCol = new TableColumn<>("Weight (kg)");
        kgCol.setCellValueFactory(c -> new SimpleStringProperty(String.valueOf(c.getValue().weightKg())));
        kgCol.setPrefWidth(120);
        TableColumn<WeightEntry, Void> delCol = new TableColumn<>("");
        delCol.setCellFactory(col -> new javafx.scene.control.TableCell<>() {
            private final Button btn = new Button("x");
            {
                btn.getStyleClass().add("danger-button");
                btn.setOnAction(ev -> {
                    WeightEntry w = getTableView().getItems().get(getIndex());
                    Async.run(() -> { api.deleteWeight(w.id()); return null; }, ignored -> refresh());
                });
            }
            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                setGraphic(empty ? null : btn);
            }
        });
        table.getColumns().add(dateCol);
        table.getColumns().add(kgCol);
        table.getColumns().add(delCol);
        Label empty = new Label("No weight logged yet. Use the bar above to add today's measurement.");
        empty.getStyleClass().add("muted");
        empty.setWrapText(true);
        table.setPlaceholder(empty);
        table.setPrefWidth(360);

        VBox tableBox = new VBox(table);
        tableBox.setPadding(new Insets(10));
        VBox.setVgrow(table, Priority.ALWAYS);

        HBox center = new HBox(12, chart, tableBox);
        HBox.setHgrow(chart, Priority.ALWAYS);
        center.setPadding(new Insets(16));

        setTop(logBar);
        setCenter(center);
        refresh();
    }

    private void save() {
        LocalDate date = datePicker.getValue();
        double kg = weightSpinner.getValue();
        if (date == null) return;
        Async.run(() -> api.upsertWeight(date, kg), ignored -> {
            refresh();
            // When the new entry is for TODAY, also patch the profile's
            // weightKg so BMR/TDEE on the Profile tab stay in lockstep.
            // Older log entries don't touch the profile.
            if (date.equals(LocalDate.now())) {
                Async.run(() -> api.updateProfile(
                        new com.calorietracker.desktop.model.UpdateProfileRequest(
                                null, null, null, null, kg, null)),
                        ignored2 -> {});
            }
        });
    }

    private void refresh() {
        Async.run(api::listWeight, list -> {
            entries.setAll(list);
            rebuildChart(list);
            if (!list.isEmpty()) {
                // pre-fill the spinner with the latest weight as a convenience
                WeightEntry latest = list.get(list.size() - 1);
                if (latest.weightKg() != null) {
                    SpinnerValueFactory.DoubleSpinnerValueFactory f =
                            (SpinnerValueFactory.DoubleSpinnerValueFactory) weightSpinner.getValueFactory();
                    f.setValue(latest.weightKg());
                }
            }
        });
    }

    private void rebuildChart(List<WeightEntry> list) {
        series.getData().clear();
        for (WeightEntry e : list) {
            if (e.weightKg() == null) continue;
            XYChart.Data<String, Number> point = new XYChart.Data<>(e.date(), e.weightKg());
            series.getData().add(point);
            installTooltip(point, e.date() + "\n" + String.format("%.1f", e.weightKg()) + " kg");
        }
    }

    /** Per-day hover tooltip + scale-on-hover affordance (mirrors ReportsView). */
    private static void installTooltip(XYChart.Data<String, Number> data, String text) {
        Tooltip tt = new Tooltip(text);
        tt.setShowDelay(javafx.util.Duration.millis(150));
        tt.getStyleClass().add("chart-point-tooltip");
        Runnable install = () -> {
            javafx.scene.Node n = data.getNode();
            if (n != null) {
                Tooltip.install(n, tt);
                n.setOnMouseEntered(ev -> n.setStyle("-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.4), 6, 0.2, 0, 0); -fx-scale-x: 1.4; -fx-scale-y: 1.4;"));
                n.setOnMouseExited(ev -> n.setStyle(""));
            }
        };
        if (data.getNode() != null) install.run();
        data.nodeProperty().addListener((obs, oldN, newN) -> { if (newN != null) install.run(); });
    }
}
