package com.calorietracker.desktop.ui;

import com.calorietracker.desktop.api.ApiClient;
import com.calorietracker.desktop.model.CreateRecipeRequest;
import com.calorietracker.desktop.model.Ingredient;
import com.calorietracker.desktop.model.Recipe;
import java.util.ArrayList;
import java.util.List;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.Separator;
import javafx.scene.control.Spinner;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

/**
 * Build a new recipe. The ingredient search hits the full library (own +
 * public, paginated 20 at a time) so you can mix your custom ingredients with
 * common foods seeded into the public DB. If what you need is missing, click
 * "+ New ingredient" to create it on the fly.
 */
public class RecipeBuilderDialog extends Dialog<Recipe> {

    private static final int PAGE_SIZE = 20;

    private static final class LineRow {
        final Ingredient ingredient;
        final double amountGrams;
        LineRow(Ingredient i, double g) { this.ingredient = i; this.amountGrams = g; }
        int kcal() { return (int) Math.round(ingredient.kcalPer100g() * (amountGrams / 100.0)); }
    }

    private final ApiClient api;
    private final TextField nameField = new TextField();
    private final TextField cookedGramsField = new TextField();
    private final TableView<Ingredient> searchTable = new TableView<>();
    private final TextField searchField = new TextField();
    private final TextField amountField = new TextField("100");
    private final ObservableList<LineRow> lines = FXCollections.observableArrayList();
    private final TableView<LineRow> linesTable = new TableView<>(lines);
    private final Label totalsLabel = new Label("Totals: 0 kcal");
    private final Label per100gLabel = new Label("Per 100 g (cooked): —");
    private final Pager<Ingredient> ingredientPager;
    private boolean cookedGramsTouched = false;

    private final Recipe editing;

    public RecipeBuilderDialog(ApiClient api) {
        this(api, null);
    }

    /**
     * Edit-existing constructor. Pass a Recipe to load its ingredients +
     * cooked weight into the form; OK then PUTs. Pass {@code null} for the
     * "new recipe" flow.
     */
    public RecipeBuilderDialog(ApiClient api, Recipe editing) {
        this.api = api;
        this.editing = editing;
        boolean isEdit = editing != null;
        setTitle(isEdit ? "Edit recipe" : "New recipe");
        setHeaderText(isEdit
                ? "Edit ingredients and cooked weight. Public seed recipes are read-only — edit your own copy."
                : "Build a recipe by adding ingredients with their amount in grams.");
        com.calorietracker.desktop.AppContext.prepareDialog(this);

        nameField.setPromptText("e.g. Mediterranean salad");

        // ---------------- ingredient search (full library + pagination) ----------------
        searchField.setPromptText("search the library (own + public)");
        TableColumn<Ingredient, String> sNameCol = new TableColumn<>("Ingredient");
        sNameCol.setCellValueFactory(c -> new SimpleStringProperty(
                c.getValue().name() + (c.getValue().isPublic() ? "" : "  (mine)")));
        sNameCol.setPrefWidth(220);
        TableColumn<Ingredient, String> sKcalCol = new TableColumn<>("kcal/100g");
        sKcalCol.setCellValueFactory(c -> new SimpleStringProperty(String.valueOf(c.getValue().kcalPer100g())));
        TableColumn<Ingredient, String> sFibCol = new TableColumn<>("Fiber/100g");
        sFibCol.setCellValueFactory(c -> new SimpleStringProperty(AiView.fmt(c.getValue().fiberPer100g()) + " g"));
        searchTable.getColumns().add(sNameCol);
        searchTable.getColumns().add(sKcalCol);
        searchTable.getColumns().add(sFibCol);
        searchTable.setPrefHeight(360);
        searchTable.setMinHeight(280);
        javafx.scene.layout.VBox.setVgrow(searchTable, javafx.scene.layout.Priority.ALWAYS);
        Label sEmpty = new Label("No foods match. Click \"+ New food\" to create one.");
        sEmpty.getStyleClass().add("muted");
        searchTable.setPlaceholder(sEmpty);

        ingredientPager = new Pager<>(
                (q, page) -> api.searchAllIngredients(q, page, PAGE_SIZE),
                searchTable);
        searchField.setOnAction(e -> ingredientPager.resetAndLoad(searchField.getText()));

        Button newIngBtn = new Button("+ New food");
        newIngBtn.setOnAction(e -> new IngredientEditorDialog(api).showAndWait()
                .ifPresent(ing -> ingredientPager.resetAndLoad(searchField.getText())));
        Button addRowBtn = new Button("Add to recipe");
        addRowBtn.getStyleClass().add("primary-button");
        addRowBtn.setOnAction(e -> addLineFromSelection());
        amountField.setPrefWidth(80);
        javafx.scene.layout.HBox.setHgrow(searchField, javafx.scene.layout.Priority.ALWAYS);
        HBox addBar = new HBox(8, searchField, new Label("Amount (g):"), amountField, addRowBtn, newIngBtn);
        addBar.setAlignment(javafx.geometry.Pos.CENTER_LEFT);

        // ---------------- lines table (the actual recipe) ----------------
        TableColumn<LineRow, String> lnIngCol = new TableColumn<>("Food");
        lnIngCol.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().ingredient.name()));
        lnIngCol.setPrefWidth(240);
        TableColumn<LineRow, String> lnAmtCol = new TableColumn<>("Amount (g)");
        lnAmtCol.setCellValueFactory(c -> new SimpleStringProperty(String.valueOf(c.getValue().amountGrams)));
        TableColumn<LineRow, String> lnKcalCol = new TableColumn<>("kcal");
        lnKcalCol.setCellValueFactory(c -> new SimpleStringProperty(String.valueOf(c.getValue().kcal())));
        TableColumn<LineRow, Void> lnDelCol = new TableColumn<>("");
        lnDelCol.setCellFactory(col -> new TableCell<>() {
            private final Button btn = new Button("x");
            {
                btn.getStyleClass().add("danger-button");
                btn.setOnAction(ev -> {
                    LineRow r = getTableView().getItems().get(getIndex());
                    lines.remove(r);
                    recomputeTotals();
                });
            }
            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                setGraphic(empty ? null : btn);
            }
        });
        linesTable.getColumns().add(lnIngCol);
        linesTable.getColumns().add(lnAmtCol);
        linesTable.getColumns().add(lnKcalCol);
        linesTable.getColumns().add(lnDelCol);
        Label lEmpty = new Label("No lines yet.");
        lEmpty.getStyleClass().add("muted");
        linesTable.setPlaceholder(lEmpty);

        cookedGramsField.setPromptText("auto from sum of raw");
        cookedGramsField.setPrefWidth(120);
        // Track whether the user has explicitly entered a cooked weight; if not,
        // we keep auto-syncing the field to the raw sum as they add ingredients.
        cookedGramsField.textProperty().addListener((obs, oldV, newV) -> {
            if (cookedGramsField.isFocused()) cookedGramsTouched = true;
            recomputeTotals();
        });

        Label hint = new Label(
                "Override the cooked weight if cooking changes the dish weight " +
                "(pasta absorbing water, meat losing it). Per-100g values are " +
                "based on the cooked weight.");
        hint.setWrapText(true);
        hint.setMaxWidth(Double.MAX_VALUE);
        hint.getStyleClass().add("muted");

        GridPane header = new GridPane();
        header.setHgap(10);
        header.setVgap(8);
        header.addRow(0, new Label("Name:"), nameField);
        header.addRow(1, new Label("Cooked weight (g):"), cookedGramsField);
        header.add(hint, 0, 2, 2, 1);
        javafx.scene.layout.ColumnConstraints col0 = new javafx.scene.layout.ColumnConstraints();
        col0.setMinWidth(javafx.scene.layout.Region.USE_PREF_SIZE);
        javafx.scene.layout.ColumnConstraints col1 = new javafx.scene.layout.ColumnConstraints();
        col1.setHgrow(javafx.scene.layout.Priority.ALWAYS);
        header.getColumnConstraints().addAll(col0, col1);
        javafx.scene.layout.GridPane.setHgrow(nameField, javafx.scene.layout.Priority.ALWAYS);

        totalsLabel.getStyleClass().add("entry-name");
        per100gLabel.getStyleClass().add("entry-name");
        totalsLabel.setWrapText(true);
        per100gLabel.setWrapText(true);
        totalsLabel.setMaxWidth(Double.MAX_VALUE);
        per100gLabel.setMaxWidth(Double.MAX_VALUE);

        Label findFoods = new Label("Find foods");
        findFoods.getStyleClass().add("section-title");
        Label recipeIngs = new Label("Recipe ingredients");
        recipeIngs.getStyleClass().add("section-title");

        linesTable.setPrefHeight(180);
        linesTable.setMinHeight(150);

        VBox box = new VBox(12, header, new Separator(),
                findFoods, searchTable, ingredientPager.loadMoreBar(), addBar, new Separator(),
                recipeIngs, linesTable, totalsLabel, per100gLabel);
        box.setPadding(new Insets(14));
        box.setPrefSize(900, 820);
        box.setMinWidth(680);
        getDialogPane().setContent(box);
        getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        // We do the POST inside the event filter so we can consume the event
        // when the server says no - the dialog stays open with the user's work
        // intact and the result converter just returns the cached recipe.
        Recipe[] createdHolder = new Recipe[1];

        Button okBtn = (Button) getDialogPane().lookupButton(ButtonType.OK);
        okBtn.addEventFilter(ActionEvent.ACTION, ev -> {
            if (nameField.getText().isBlank()) {
                Async.showWarning("Name is required.");
                ev.consume();
                return;
            }
            if (lines.isEmpty()) {
                Async.showWarning("Add at least one food to the recipe.");
                ev.consume();
                return;
            }
            try {
                List<CreateRecipeRequest.Line> apiLines = new ArrayList<>();
                for (LineRow r : lines) {
                    apiLines.add(new CreateRecipeRequest.Line(r.ingredient.id(), r.amountGrams));
                }
                double cookedGrams = parseCookedGramsOr(rawSum());
                CreateRecipeRequest payload = new CreateRecipeRequest(
                        nameField.getText().trim(), 1, apiLines, cookedGrams);
                createdHolder[0] = (editing != null)
                        ? api.updateRecipe(editing.id(), payload)
                        : api.createRecipe(payload);
            } catch (Exception e) {
                Async.showError(e);
                ev.consume();
            }
        });

        setResultConverter(bt -> bt == ButtonType.OK ? createdHolder[0] : null);

        ingredientPager.resetAndLoad("");

        // ---------------- pre-fill when editing an existing recipe ----------------
        if (editing != null) {
            nameField.setText(editing.name() == null ? "" : editing.name());
            // User-edited the cooked weight at create-time → preserve it; the
            // listener flag prevents the auto-sync from overwriting on first add.
            cookedGramsTouched = true;
            if (editing.totalCookedGrams() > 0) {
                cookedGramsField.setText(String.format("%.0f", editing.totalCookedGrams()));
            }
            // Load each ingredient by id, then append a LineRow. Async so we
            // don't block the dialog's first paint; recomputeTotals runs after
            // each row arrives so the totals are correct even mid-fetch.
            for (com.calorietracker.desktop.model.Recipe.Line ln : editing.ingredients()) {
                Async.run(() -> api.getIngredient(ln.ingredientId()), ing -> {
                    lines.add(new LineRow(ing, ln.amountGrams()));
                    recomputeTotals();
                });
            }
        }
    }

    private void addLineFromSelection() {
        Ingredient sel = searchTable.getSelectionModel().getSelectedItem();
        if (sel == null) {
            Async.showError(new RuntimeException("Pick a food first."));
            return;
        }
        try {
            double g = Double.parseDouble(amountField.getText().trim());
            lines.add(new LineRow(sel, g));
            recomputeTotals();
        } catch (NumberFormatException e) {
            Async.showError(new RuntimeException("Amount must be a number."));
        }
    }

    private void recomputeTotals() {
        double k = 0, p = 0, c = 0, f = 0, fib = 0;
        for (LineRow r : lines) {
            double factor = r.amountGrams / 100.0;
            k   += r.ingredient.kcalPer100g()    * factor;
            p   += r.ingredient.proteinPer100g() * factor;
            c   += r.ingredient.carbsPer100g()   * factor;
            f   += r.ingredient.fatPer100g()     * factor;
            fib += r.ingredient.fiberPer100g()   * factor;
        }
        double rawGrams = rawSum();
        // Keep the cooked-weight input in sync with raw sum until the user
        // explicitly edits it.
        if (!cookedGramsTouched) {
            String autoVal = rawGrams > 0 ? String.format("%.0f", rawGrams) : "";
            if (!autoVal.equals(cookedGramsField.getText())) {
                cookedGramsField.setText(autoVal);
            }
        }
        double cooked = parseCookedGramsOr(rawGrams);

        totalsLabel.setText(String.format(
                "Raw totals: %d kcal  ·  P %.1f g  ·  C %.1f g  ·  F %.1f g  ·  Fiber %.1f g  (raw %.0f g, cooked %.0f g)",
                Math.round(k), p, c, f, fib, rawGrams, cooked));

        if (cooked <= 0) {
            per100gLabel.setText("Per 100 g (cooked): —");
        } else {
            double m = 100.0 / cooked;
            per100gLabel.setText(String.format(
                    "Per 100 g (cooked): %d kcal  ·  P %.1f g  ·  C %.1f g  ·  F %.1f g  ·  Fiber %.1f g",
                    Math.round(k * m), p * m, c * m, f * m, fib * m));
        }
    }

    private double rawSum() {
        double s = 0;
        for (LineRow r : lines) s += r.amountGrams;
        return s;
    }

    /** Parse the cooked-weight input, falling back to {@code defaultGrams} on any problem. */
    private double parseCookedGramsOr(double defaultGrams) {
        String raw = cookedGramsField.getText();
        if (raw == null || raw.isBlank()) return defaultGrams;
        try {
            double v = Double.parseDouble(raw.trim().replace(',', '.'));
            return v > 0 ? v : defaultGrams;
        } catch (NumberFormatException e) {
            return defaultGrams;
        }
    }
}
