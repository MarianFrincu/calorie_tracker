package com.calorietracker.desktop.ui;

import com.calorietracker.desktop.api.ApiClient;
import com.calorietracker.desktop.model.DailyNutritionPoint;
import com.calorietracker.desktop.model.DailyWaterPoint;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import java.util.function.Function;
import java.util.function.ToDoubleFunction;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.chart.CategoryAxis;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

/**
 * Daily AreaChart for any of Calories / Protein / Carbs / Fat / Fiber / Water,
 * with the day-specific target rendered as a single dashed horizontal line.
 * <p>
 * "Weekly" = the calendar week (Monday-Sunday) that contains the anchor date.
 * "Monthly" = the calendar month that contains the anchor date. Prev/Next step
 * one calendar week / one calendar month at a time. Days after today are
 * trimmed so a partial current period still shows the right shape.
 */
public class ReportsView extends BorderPane {

    private enum Period {
        WEEKLY("Weekly (this calendar week)"),
        MONTHLY("Monthly (this calendar month)");
        final String label;
        Period(String label) { this.label = label; }
        @Override public String toString() { return label; }
    }

    private enum Metric {
        CALORIES("Calories", "kcal"),
        PROTEIN("Protein", "g"),
        CARBS("Carbs", "g"),
        FAT("Fat", "g"),
        FIBER("Fiber", "g"),
        WATER("Water", "ml");
        final String label; final String unit;
        Metric(String label, String unit) { this.label = label; this.unit = unit; }
        @Override public String toString() { return label; }
    }

    private static final DateTimeFormatter ISO = DateTimeFormatter.ISO_LOCAL_DATE;
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("d");
    private static final DateTimeFormatter DAY_MONTH = DateTimeFormatter.ofPattern("d MMM");
    /** Friendly format used in hover tooltips: "Mon, 12 Jun". */
    private static final DateTimeFormatter PRETTY = DateTimeFormatter.ofPattern("EEE, d MMM");

    private final ApiClient api;
    private final ComboBox<Period> periodBox = new ComboBox<>(FXCollections.observableArrayList(Period.values()));
    private final ComboBox<Metric> metricBox = new ComboBox<>(FXCollections.observableArrayList(Metric.values()));
    /** Any date inside the period the user wants to look at. Period is computed FROM this. */
    private final DatePicker anchorPicker = new DatePicker(LocalDate.now());

    private final SmoothAreaChart<String, Number> chart;
    private final XYChart.Series<String, Number> actualSeries = new XYChart.Series<>();
    private final XYChart.Series<String, Number> targetSeries = new XYChart.Series<>();

    private final Label rangeLabel = new Label("");
    private final Label meanLabel = new Label("-");
    private final Label minLabel = new Label("-");
    private final Label maxLabel = new Label("-");
    private final Label targetLabel = new Label("-");

    private List<DailyNutritionPoint> nutritionCache;
    private List<DailyWaterPoint> waterCache;

    public ReportsView(ApiClient api) {
        this.api = api;
        periodBox.setValue(Period.WEEKLY);
        metricBox.setValue(Metric.CALORIES);

        // ---- toolbar with date navigation ----
        Button prev = new Button("< Prev");
        Button next = new Button("Next >");
        Button today = new Button("Today");
        today.getStyleClass().add("primary-button");
        prev.setOnAction(e -> shift(-1));
        next.setOnAction(e -> shift(+1));
        today.setOnAction(e -> { anchorPicker.setValue(LocalDate.now()); reload(); });
        anchorPicker.valueProperty().addListener((o, a, b) -> reload());
        anchorPicker.setEditable(false);

        rangeLabel.getStyleClass().add("muted");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox toolbar = new HBox(8,
                new Label("Any date in period:"), prev, anchorPicker, today, next,
                new Label("Period:"), periodBox,
                spacer,
                new Label("Metric:"), metricBox);
        toolbar.setAlignment(Pos.CENTER_LEFT);
        toolbar.setPadding(new Insets(10, 16, 10, 16));
        toolbar.getStyleClass().add("toolbar");

        // ---- chart ----
        CategoryAxis xAxis = new CategoryAxis();
        xAxis.setLabel("");
        NumberAxis yAxis = new NumberAxis();
        yAxis.setLabel("Value");
        chart = new SmoothAreaChart<>(xAxis, yAxis);
        chart.setAnimated(false);
        chart.setLegendVisible(true);
        // Show a marker per day so the user can see exactly which dates
        // contributed to the line.
        chart.setCreateSymbols(true);
        actualSeries.setName("Actual");
        targetSeries.setName("Target");
        chart.getData().add(actualSeries);
        chart.getData().add(targetSeries);

        VBox stats = statsCard();
        HBox center = new HBox(12, chart, stats);
        HBox.setHgrow(chart, Priority.ALWAYS);
        center.setPadding(new Insets(16));

        VBox topBox = new VBox(toolbar, rangeRow());
        setTop(topBox);
        setCenter(center);

        periodBox.valueProperty().addListener((o, a, b) -> reload());
        metricBox.valueProperty().addListener((o, a, b) -> render());

        reload();
    }

    private HBox rangeRow() {
        HBox h = new HBox(rangeLabel);
        h.setAlignment(Pos.CENTER_LEFT);
        h.setPadding(new Insets(6, 16, 6, 16));
        return h;
    }

    private VBox statsCard() {
        Label title = new Label("STATS");
        title.getStyleClass().add("card-title");
        VBox box = new VBox(8, title,
                row("Mean",   meanLabel),
                row("Min",    minLabel),
                row("Max",    maxLabel),
                row("Target", targetLabel));
        box.getStyleClass().add("card");
        box.setMinWidth(220);
        box.setMaxWidth(260);
        return box;
    }

    private HBox row(String key, Label value) {
        Label k = new Label(key);
        k.getStyleClass().add("muted");
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        value.getStyleClass().add("medium-number");
        HBox h = new HBox(8, k, spacer, value);
        h.setAlignment(Pos.CENTER_LEFT);
        return h;
    }

    // ---------------- navigation ----------------

    /**
     * Step exactly one calendar week / month from the current anchor. We move
     * the anchor by the period unit; the period bounds re-derive from it.
     * If the next period would be entirely in the future we still allow it -
     * just no data will render.
     */
    private void shift(int sign) {
        LocalDate anchor = anchorPicker.getValue();
        if (anchor == null) anchor = LocalDate.now();
        LocalDate newAnchor = (periodBox.getValue() == Period.WEEKLY)
                ? anchor.plusWeeks(sign)
                : anchor.plusMonths(sign);
        if (newAnchor.isAfter(LocalDate.now())) newAnchor = LocalDate.now();
        anchorPicker.setValue(newAnchor);
    }

    /** Calendar bounds (Monday-Sunday week or 1st-last day of month) for the given anchor. */
    private LocalDate[] periodBounds(LocalDate anchor) {
        LocalDate start;
        LocalDate end;
        if (periodBox.getValue() == Period.WEEKLY) {
            start = anchor.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
            end   = start.plusDays(6);
        } else {
            start = anchor.withDayOfMonth(1);
            end   = anchor.with(TemporalAdjusters.lastDayOfMonth());
        }
        // Don't query future days - the chart should just stop at "today" for the in-progress period.
        if (end.isAfter(LocalDate.now())) end = LocalDate.now();
        if (start.isAfter(end)) start = end; // (defensive; can only happen on a future anchor)
        return new LocalDate[]{start, end};
    }

    // ---------------- data ----------------

    private void reload() {
        LocalDate anchor = anchorPicker.getValue() == null ? LocalDate.now() : anchorPicker.getValue();
        LocalDate[] r = periodBounds(anchor);
        LocalDate from = r[0], to = r[1];
        long days = java.time.temporal.ChronoUnit.DAYS.between(from, to) + 1;
        rangeLabel.setText(from + "  to  " + to + "  (" + days + " day" + (days == 1 ? "" : "s") + ")");
        Async.run(() -> api.nutritionReport(from, to), n -> {
            nutritionCache = n;
            Async.run(() -> api.waterReport(from, to), w -> {
                waterCache = w;
                render();
            });
        });
    }

    private void render() {
        actualSeries.getData().clear();
        targetSeries.getData().clear();
        Metric m = metricBox.getValue();

        if (m == Metric.WATER) {
            if (waterCache == null) return;
            fill(waterCache, DailyWaterPoint::date, p -> p.ml(),
                    p -> p.mlTarget() == null ? null : p.mlTarget().doubleValue());
        } else {
            if (nutritionCache == null) return;
            ToDoubleFunction<DailyNutritionPoint> getActual = switch (m) {
                case CALORIES -> DailyNutritionPoint::kcal;
                case PROTEIN  -> DailyNutritionPoint::protein;
                case CARBS    -> DailyNutritionPoint::carbs;
                case FAT      -> DailyNutritionPoint::fat;
                case FIBER    -> DailyNutritionPoint::fiber;
                default       -> p -> 0;
            };
            Function<DailyNutritionPoint, Double> getTarget = switch (m) {
                case CALORIES -> p -> p.kcalTarget()    == null ? null : p.kcalTarget().doubleValue();
                case PROTEIN  -> p -> p.proteinTarget() == null ? null : p.proteinTarget().doubleValue();
                case CARBS    -> p -> p.carbsTarget()   == null ? null : p.carbsTarget().doubleValue();
                case FAT      -> p -> p.fatTarget()     == null ? null : p.fatTarget().doubleValue();
                case FIBER    -> p -> p.fiberTarget()   == null ? null : p.fiberTarget().doubleValue();
                default       -> p -> null;
            };
            fill(nutritionCache, DailyNutritionPoint::date, getActual, getTarget);
        }

        chart.getYAxis().setLabel(m.label + " (" + m.unit + ")");
        styleTargetSeries();
    }

    private <T> void fill(List<T> points,
                          Function<T, String> isoDateOf,
                          ToDoubleFunction<T> actualOf,
                          Function<T, Double> targetOf) {
        if (points == null || points.isEmpty()) {
            meanLabel.setText("-");
            minLabel.setText("-");
            maxLabel.setText("-");
            targetLabel.setText("-");
            return;
        }

        double sum = 0, min = Double.MAX_VALUE, max = -Double.MAX_VALUE;
        Double lastTarget = null;
        int prevMonth = -1;

        String unit = metricBox.getValue() == null ? "" : metricBox.getValue().unit;

        // First pass: actual + per-day raw targets.
        // The target for each day is whatever historical snapshot the backend
        // returned for that date, so a goal change on day N only changes the
        // line from day N onward - earlier days keep the old goal.
        java.util.List<Double> targets    = new java.util.ArrayList<>(points.size());
        java.util.List<String> labels     = new java.util.ArrayList<>(points.size());
        java.util.List<LocalDate> dates   = new java.util.ArrayList<>(points.size());

        for (T p : points) {
            LocalDate date = LocalDate.parse(isoDateOf.apply(p), ISO);
            String label = (date.getMonthValue() != prevMonth) ? date.format(DAY_MONTH) : date.format(DAY);
            prevMonth = date.getMonthValue();
            labels.add(label);
            dates.add(date);

            double v = actualOf.applyAsDouble(p);
            XYChart.Data<String, Number> actualPoint = new XYChart.Data<>(label, v);
            actualSeries.getData().add(actualPoint);
            installTooltip(actualPoint, date.format(PRETTY) + "\nActual: " + fmt(v) + " " + unit);

            Double t = targetOf.apply(p);
            targets.add(t);
            if (t != null) lastTarget = t;

            sum += v;
            if (v < min) min = v;
            if (v > max) max = v;
        }

        // Second pass: one target point per day. The smooth (Bezier) renderer
        // draws a curve through all of them, so a goal change between day i-1
        // and day i shows up as a smooth ramp rather than a vertical step -
        // which is what the user wants for the "objective line" to read as a
        // curve rather than a polyline.
        for (int i = 0; i < points.size(); i++) {
            Double t = targets.get(i);
            if (t == null) continue;
            XYChart.Data<String, Number> targetPoint = new XYChart.Data<>(labels.get(i), t);
            targetSeries.getData().add(targetPoint);
            installTooltip(targetPoint, dates.get(i).format(PRETTY) + "\nTarget: " + fmt(t) + " " + unit);
        }

        int n = points.size();
        meanLabel.setText(fmt(sum / n));
        minLabel.setText(fmt(min));
        maxLabel.setText(fmt(max));
        targetLabel.setText(lastTarget == null ? "-" : fmt(lastTarget));
    }

    private void styleTargetSeries() {
        if (targetSeries.getNode() != null
                && !targetSeries.getNode().getStyleClass().contains("target-series")) {
            targetSeries.getNode().getStyleClass().add("target-series");
        }
    }

    private static String fmt(double v) {
        return v == Math.floor(v) ? String.valueOf((int) v) : String.format("%.1f", v);
    }

    /**
     * Attach a hover tooltip to a chart data point. JavaFX creates the
     * underlying node lazily after the chart lays out, so we listen on the
     * {@code nodeProperty} and install only once the node exists.
     */
    private static void installTooltip(XYChart.Data<String, Number> data, String text) {
        javafx.scene.control.Tooltip tt = new javafx.scene.control.Tooltip(text);
        tt.setShowDelay(javafx.util.Duration.millis(150));
        tt.getStyleClass().add("chart-point-tooltip");
        Runnable install = () -> {
            javafx.scene.Node n = data.getNode();
            if (n != null) {
                javafx.scene.control.Tooltip.install(n, tt);
                // A subtle hover affordance so the user sees the point is interactive.
                n.setOnMouseEntered(e -> n.setStyle("-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.4), 6, 0.2, 0, 0); -fx-scale-x: 1.4; -fx-scale-y: 1.4;"));
                n.setOnMouseExited(e -> n.setStyle(""));
            }
        };
        if (data.getNode() != null) install.run();
        data.nodeProperty().addListener((obs, oldN, newN) -> { if (newN != null) install.run(); });
    }
}
