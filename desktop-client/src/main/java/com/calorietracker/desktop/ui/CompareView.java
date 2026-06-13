package com.calorietracker.desktop.ui;

import com.calorietracker.desktop.api.ApiClient;
import com.calorietracker.desktop.model.Ingredient;
import com.calorietracker.desktop.model.Recipe;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.scene.chart.BarChart;
import javafx.scene.chart.CategoryAxis;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

/**
 * Compare 2-4 foods side by side. Pick from the library (ingredients on the
 * "/100 g" basis, recipes on the "per serving" basis) and the right pane shows
 * a side-by-side metrics table + a bar chart per macro/calories so the
 * differences pop.
 */
public class CompareView extends BorderPane {

    private static final int MAX_SLOTS = 4;
    private static final int PAGE_SIZE = 20;

    private enum SourceKind {
        INGREDIENT("Food"),
        RECIPE("Recipe");
        final String label;
        SourceKind(String label) { this.label = label; }
        @Override public String toString() { return label; }
    }

    /** Unified view of either an Ingredient (per 100 g) or a Recipe (per serving). */
    private record CompareItem(SourceKind kind, long id, String label,
                               int kcal, double protein, double carbs, double fat, double fiber) {
        static CompareItem of(Ingredient i) {
            return new CompareItem(SourceKind.INGREDIENT, i.id(), i.name() + " (per 100 g)",
                    i.kcalPer100g(), i.proteinPer100g(), i.carbsPer100g(), i.fatPer100g(), i.fiberPer100g());
        }
        static CompareItem of(Recipe r) {
            int servings = Math.max(1, r.servings());
            return new CompareItem(SourceKind.RECIPE, r.id(), r.name() + " (per serving)",
                    r.totalKcal() / servings,
                    r.totalProtein() / servings,
                    r.totalCarbs() / servings,
                    r.totalFat() / servings,
                    r.totalFiber() / servings);
        }
    }

    private final ObservableList<CompareItem> picked = FXCollections.observableArrayList();
    private final TableView<Object[]> metricsTable = new TableView<>();
    private final BarChart<String, Number> chart;

    public CompareView(ApiClient api) {
        // ---- LEFT pane: source picker ----
        ComboBox<SourceKind> kindBox = new ComboBox<>(FXCollections.observableArrayList(SourceKind.values()));
        kindBox.setValue(SourceKind.INGREDIENT);
        TextField search = new TextField();
        search.setPromptText("search (press Enter)");
        TableView<CompareItem> hits = new TableView<>();
        TableColumn<CompareItem, String> nameCol = new TableColumn<>("Name");
        nameCol.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().label()));
        nameCol.setPrefWidth(280);
        TableColumn<CompareItem, String> kcalCol = new TableColumn<>("kcal");
        kcalCol.setCellValueFactory(c -> new SimpleStringProperty(String.valueOf(c.getValue().kcal())));
        kcalCol.setPrefWidth(60);
        hits.getColumns().add(nameCol);
        hits.getColumns().add(kcalCol);
        VBox.setVgrow(hits, Priority.ALWAYS);

        Button addBtn = new Button("Add to comparison");
        addBtn.getStyleClass().add("primary-button");
        addBtn.disableProperty().bind(hits.getSelectionModel().selectedItemProperty().isNull());
        addBtn.setOnAction(e -> {
            CompareItem sel = hits.getSelectionModel().getSelectedItem();
            if (sel == null) return;
            if (picked.size() >= MAX_SLOTS) {
                Async.showError(new RuntimeException("You can compare at most " + MAX_SLOTS + " foods."));
                return;
            }
            for (CompareItem c : picked) {
                if (c.kind() == sel.kind() && c.id() == sel.id()) return; // skip dupes silently
            }
            picked.add(sel);
            rebuild();
        });

        Runnable doSearch = () -> {
            String q = search.getText();
            if (kindBox.getValue() == SourceKind.INGREDIENT) {
                Async.run(() -> api.searchAllIngredients(q, 0, PAGE_SIZE), list -> {
                    List<CompareItem> mapped = new ArrayList<>();
                    for (Ingredient i : list) mapped.add(CompareItem.of(i));
                    hits.getItems().setAll(mapped);
                });
            } else {
                Async.run(() -> api.searchAllRecipes(q, 0, PAGE_SIZE), list -> {
                    List<CompareItem> mapped = new ArrayList<>();
                    for (Recipe r : list) mapped.add(CompareItem.of(r));
                    hits.getItems().setAll(mapped);
                });
            }
        };
        search.setOnAction(e -> doSearch.run());
        kindBox.valueProperty().addListener((o, a, b) -> { search.clear(); doSearch.run(); });

        Label leftTitle = new Label("Pick foods to compare");
        leftTitle.getStyleClass().add("section-title");
        VBox leftPane = new VBox(8,
                leftTitle,
                new HBox(8, new Label("Source:"), kindBox),
                search,
                hits,
                addBtn);
        leftPane.setPadding(new Insets(16));
        leftPane.setPrefWidth(420);
        leftPane.getStyleClass().add("card");
        leftPane.setMaxHeight(Double.MAX_VALUE);

        // ---- RIGHT pane: comparison ----
        Label rightTitle = new Label("Comparison");
        rightTitle.getStyleClass().add("section-title");

        Label hint = new Label(
                "Ingredients are compared per 100 g, recipes per serving. "
                        + "Tap the X next to a chip to remove it.");
        hint.setWrapText(true);
        hint.getStyleClass().add("muted");

        HBox chipsBar = new HBox(6);
        chipsBar.setPadding(new Insets(4, 0, 4, 0));
        picked.addListener((javafx.collections.ListChangeListener<CompareItem>) ch -> renderChips(chipsBar));

        metricsTable.setPlaceholder(new Label("Add at least 2 foods to see the comparison."));
        // Unconstrained policy: each column keeps its prefWidth, table shows a
        // horizontal scrollbar if the picked-food columns exceed the pane width.
        // CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN caused the metric column to
        // shrink below readability when many foods were picked.
        metricsTable.setColumnResizePolicy(TableView.UNCONSTRAINED_RESIZE_POLICY);
        metricsTable.setPrefHeight(240);

        CategoryAxis xAxis = new CategoryAxis();
        NumberAxis yAxis = new NumberAxis();
        yAxis.setLabel("Value");
        chart = new BarChart<>(xAxis, yAxis);
        chart.setLegendVisible(true);
        chart.setAnimated(false);
        chart.setTitle("Per macro / kcal");
        VBox.setVgrow(chart, Priority.ALWAYS);

        VBox rightPane = new VBox(10, rightTitle, chipsBar, hint, metricsTable, chart);
        rightPane.setPadding(new Insets(16));
        rightPane.getStyleClass().add("card");

        HBox center = new HBox(14, leftPane, rightPane);
        HBox.setHgrow(rightPane, Priority.ALWAYS);
        center.setPadding(new Insets(16));
        ScrollPane scroll = new ScrollPane(center);
        scroll.setFitToWidth(true);
        scroll.setFitToHeight(true);
        scroll.getStyleClass().add("scroll-pane");
        setCenter(scroll);

        // initial empty render + first search
        rebuild();
        doSearch.run();
    }

    private void renderChips(HBox chipsBar) {
        chipsBar.getChildren().clear();
        for (CompareItem c : picked) {
            Label chip = new Label(c.label() + "  X");
            chip.getStyleClass().add("water-chip"); // reuse the chip-style we already have
            chip.setOnMouseClicked(ev -> { picked.remove(c); rebuild(); });
            chipsBar.getChildren().add(chip);
        }
    }

    /** Re-derives the metrics table + bar chart from the current picks. */
    private void rebuild() {
        metricsTable.getColumns().clear();
        metricsTable.getItems().clear();
        chart.getData().clear();
        if (picked.isEmpty()) return;

        // Metric column - pinned width sized to the widest metric label ("Calories (kcal)").
        // min == pref == max so a flexible layout can't squash it.
        TableColumn<Object[], String> metricCol = new TableColumn<>("Metric");
        metricCol.setCellValueFactory(c -> new SimpleStringProperty((String) c.getValue()[0]));
        metricCol.setMinWidth(160);
        metricCol.setPrefWidth(160);
        metricCol.setMaxWidth(160);
        metricsTable.getColumns().add(metricCol);

        for (int i = 0; i < picked.size(); i++) {
            CompareItem c = picked.get(i);
            int idx = i + 1;
            TableColumn<Object[], String> col = new TableColumn<>(c.label());
            col.setCellValueFactory(cd -> new SimpleStringProperty(String.valueOf(cd.getValue()[idx])));
            // Sized to a typical "Banana (per 100 g)" label without truncation.
            col.setMinWidth(170);
            col.setPrefWidth(200);
            metricsTable.getColumns().add(col);
        }

        // Plain numeric comparison - we deliberately don't bold a "winner" because
        // which value is best depends on the user's goal (cutting vs bulking, etc.).
        addRow("Calories (kcal)", c -> (double) c.kcal(),    v -> Math.round(v) + "");
        addRow("Protein (g)",     CompareItem::protein,      v -> String.format("%.1f", v));
        addRow("Carbs (g)",       CompareItem::carbs,        v -> String.format("%.1f", v));
        addRow("Fat (g)",         CompareItem::fat,          v -> String.format("%.1f", v));
        addRow("Fiber (g)",       CompareItem::fiber,        v -> String.format("%.1f", v));

        // Bar chart: one series per food, x = metric
        String[] metrics = { "kcal", "Protein", "Carbs", "Fat", "Fiber" };
        Function<CompareItem, double[]> vec = c -> new double[]{
                c.kcal(), c.protein(), c.carbs(), c.fat(), c.fiber()
        };
        for (CompareItem c : picked) {
            XYChart.Series<String, Number> s = new XYChart.Series<>();
            s.setName(c.label());
            double[] v = vec.apply(c);
            for (int i = 0; i < metrics.length; i++) {
                s.getData().add(new XYChart.Data<>(metrics[i], v[i]));
            }
            chart.getData().add(s);
        }
    }

    private void addRow(String metric, Function<CompareItem, Double> valueOf, Function<Double, String> fmt) {
        int n = picked.size();
        Object[] row = new Object[1 + n];
        row[0] = metric;
        for (int i = 0; i < n; i++) {
            row[1 + i] = fmt.apply(valueOf.apply(picked.get(i)));
        }
        metricsTable.getItems().add(row);
    }
}
