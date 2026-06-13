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
    private final Spinner<Integer> servingsSpinner = new Spinner<>(1, 99, 1);
    private final TableView<Ingredient> searchTable = new TableView<>();
    private final TextField searchField = new TextField();
    private final TextField amountField = new TextField("100");
    private final ObservableList<LineRow> lines = FXCollections.observableArrayList();
    private final TableView<LineRow> linesTable = new TableView<>(lines);
    private final Label totalsLabel = new Label("Totals: 0 kcal");
    private final Pager<Ingredient> ingredientPager;

    public RecipeBuilderDialog(ApiClient api) {
        this.api = api;
        setTitle("New recipe");
        setHeaderText("Build a recipe by adding ingredients with their amount in grams.");
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
        searchTable.setPrefHeight(200);
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
        HBox addBar = new HBox(8, searchField, new Label("Amount (g):"), amountField, addRowBtn, newIngBtn);

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

        GridPane header = new GridPane();
        header.setHgap(10);
        header.setVgap(8);
        header.addRow(0, new Label("Name:"), nameField);
        header.addRow(1, new Label("Servings:"), servingsSpinner);

        totalsLabel.getStyleClass().add("entry-name");

        VBox box = new VBox(12, header, new Separator(),
                new Label("Find foods"), searchTable, ingredientPager.loadMoreBar(), addBar, new Separator(),
                new Label("Recipe ingredients"), linesTable, totalsLabel);
        box.setPadding(new Insets(14));
        box.setPrefSize(820, 760);
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
                createdHolder[0] = api.createRecipe(new CreateRecipeRequest(
                        nameField.getText().trim(), servingsSpinner.getValue(), apiLines));
            } catch (Exception e) {
                Async.showError(e);
                ev.consume();
            }
        });

        setResultConverter(bt -> bt == ButtonType.OK ? createdHolder[0] : null);

        ingredientPager.resetAndLoad("");
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
        totalsLabel.setText(String.format(
                "Totals: %d kcal  ·  P %.1f g  ·  C %.1f g  ·  F %.1f g  ·  Fiber %.1f g",
                Math.round(k), p, c, f, fib));
    }
}
