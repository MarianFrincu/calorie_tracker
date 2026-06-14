package com.calorietracker.desktop.ui;

import com.calorietracker.desktop.api.ApiClient;
import com.calorietracker.desktop.model.AddDiaryRequest;
import com.calorietracker.desktop.model.Ingredient;
import com.calorietracker.desktop.model.Meal;
import com.calorietracker.desktop.model.Recipe;
import java.time.LocalDate;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.event.ActionEvent;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

/**
 * Modal "Add food" dialog: two tabs (Food / Recipe). Both search the full
 * library (own + public) and load 20 results at a time with a "Load more"
 * button. Returns an AddDiaryRequest ready to POST to /api/diary.
 * <p>
 * Resizable - the user's last chosen size is remembered for the lifetime of
 * the JVM so reopening the dialog feels stable. Validation errors render
 * inline in a yellow hint bar and DO NOT close the dialog.
 */
public class AddFoodDialog extends Dialog<AddDiaryRequest> {

    private static final int PAGE_SIZE = 20;

    /** Sticky across reopens within the same app run. */
    private static double  lastWidth     = 720;
    private static double  lastHeight    = 600;
    private static boolean lastMaximized = false;

    private static class IngredientTabState {
        Ingredient selected;
        final TextField amount = new TextField("100");
    }

    private static class RecipeTabState {
        Recipe selected;
        final TextField amount = new TextField("1");
    }

    public AddFoodDialog(ApiClient api, LocalDate date, Meal preset) {
        setTitle("Add to " + DayView.prettyMeal(preset));
        setHeaderText("Search the library (yours + public), then choose an amount.");
        setResizable(true);
        com.calorietracker.desktop.AppContext.prepareDialog(this);

        Tab foodTab = buildIngredientTab(api);
        Tab recipeTab = buildRecipeTab(api);
        TabPane tabs = new TabPane(foodTab, recipeTab);
        tabs.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        VBox.setVgrow(tabs, Priority.ALWAYS);

        VBox content = new VBox(10, tabs);
        content.setPadding(new Insets(12));
        content.setPrefSize(lastWidth, lastHeight);
        content.setMinSize(600, 480);
        getDialogPane().setContent(content);
        getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        // Validation pops up as a warning alert and consumes the OK event, so
        // the dialog stays open with the user's selections intact.
        Button okBtn = (Button) getDialogPane().lookupButton(ButtonType.OK);
        okBtn.addEventFilter(ActionEvent.ACTION, ae -> {
            String error = validate(tabs, foodTab, recipeTab);
            if (error != null) {
                Async.showWarning(error);
                ae.consume();
            }
        });

        // Stash the dialog's Stage while the scene still exists. setOnHidden
        // fires AFTER the scene has been nulled, so we can't safely call
        // getScene().getWindow() in there - that's the NPE.
        Stage[] dialogStageHolder = new Stage[1];
        setOnShown(e -> {
            javafx.scene.Scene sc = getDialogPane().getScene();
            if (sc != null && sc.getWindow() instanceof Stage st) {
                dialogStageHolder[0] = st;
                st.setMaximized(lastMaximized);
            }
        });

        // Save the user's chosen size + maximized state so the next open uses them.
        setOnHidden(e -> {
            Stage st = dialogStageHolder[0];
            if (st != null) lastMaximized = st.isMaximized();
            if (!lastMaximized) {
                lastWidth = content.getWidth() > 0 ? content.getWidth() : lastWidth;
                lastHeight = content.getHeight() > 0 ? content.getHeight() : lastHeight;
            }
        });

        setResultConverter(bt -> {
            if (bt != ButtonType.OK) return null;
            try {
                if (tabs.getSelectionModel().getSelectedItem() == foodTab) {
                    IngredientTabState s = (IngredientTabState) foodTab.getUserData();
                    double grams = Double.parseDouble(s.amount.getText().trim());
                    return AddDiaryRequest.ingredient(date.toString(), preset, s.selected.id(), grams);
                } else {
                    RecipeTabState s = (RecipeTabState) recipeTab.getUserData();
                    double grams = Double.parseDouble(s.amount.getText().trim());
                    return AddDiaryRequest.recipeGrams(date.toString(), preset, s.selected.id(), grams);
                }
            } catch (NumberFormatException e) {
                Async.showError(new RuntimeException("Amount must be a number."));
                return null;
            }
        });
    }

    /** Returns a friendly message if the user can't submit yet, or null if OK to proceed. */
    private static String validate(TabPane tabs, Tab foodTab, Tab recipeTab) {
        if (tabs.getSelectionModel().getSelectedItem() == foodTab) {
            IngredientTabState s = (IngredientTabState) foodTab.getUserData();
            if (s.selected == null) return "Pick a food from the list before clicking OK.";
            if (s.amount.getText().trim().isEmpty()) return "Enter the amount in grams.";
        } else {
            RecipeTabState s = (RecipeTabState) recipeTab.getUserData();
            if (s.selected == null) return "Pick a recipe from the list before clicking OK.";
            if (s.amount.getText().trim().isEmpty()) return "Enter the amount in grams (cooked).";
        }
        return null;
    }

    private Tab buildIngredientTab(ApiClient api) {
        IngredientTabState state = new IngredientTabState();

        TextField search = new TextField();
        search.setPromptText("search foods (press Enter)");

        TableView<Ingredient> table = new TableView<>();
        TableColumn<Ingredient, String> nameCol = new TableColumn<>("Name");
        nameCol.setCellValueFactory(c -> new SimpleStringProperty(
                c.getValue().name() + (c.getValue().isPublic() ? "" : "  (mine)")));
        nameCol.setPrefWidth(260);
        TableColumn<Ingredient, String> kcalCol = new TableColumn<>("kcal/100g");
        kcalCol.setCellValueFactory(c -> new SimpleStringProperty(String.valueOf(c.getValue().kcalPer100g())));
        TableColumn<Ingredient, String> macroCol = new TableColumn<>("P / C / F / Fib (per 100g)");
        macroCol.setCellValueFactory(c -> new SimpleStringProperty(String.format("%.1f / %.1f / %.1f / %.1f",
                c.getValue().proteinPer100g(), c.getValue().carbsPer100g(),
                c.getValue().fatPer100g(), c.getValue().fiberPer100g())));
        macroCol.setPrefWidth(220);
        table.getColumns().add(nameCol);
        table.getColumns().add(kcalCol);
        table.getColumns().add(macroCol);
        Label empty = new Label("No foods yet. Try searching a common one, or add one in the Recipes -> Foods tab.");
        empty.getStyleClass().add("muted");
        empty.setWrapText(true);
        table.setPlaceholder(empty);
        table.getSelectionModel().selectedItemProperty().addListener((o, oldV, v) -> state.selected = v);
        VBox.setVgrow(table, Priority.ALWAYS);

        Pager<Ingredient> pager = new Pager<>(
                (q, page) -> api.searchAllIngredients(q, page, PAGE_SIZE),
                table);
        search.setOnAction(e -> pager.resetAndLoad(search.getText()));

        HBox amountRow = new HBox(8, new Label("Amount (g):"), state.amount);

        VBox box = new VBox(8, search, table, pager.loadMoreBar(), amountRow);
        box.setPadding(new Insets(10));

        Tab tab = new Tab("Food", box);
        tab.setUserData(state);
        pager.resetAndLoad("");
        return tab;
    }

    private Tab buildRecipeTab(ApiClient api) {
        RecipeTabState state = new RecipeTabState();

        TextField search = new TextField();
        search.setPromptText("search recipes (press Enter)");

        TableView<Recipe> table = new TableView<>();
        TableColumn<Recipe, String> nameCol = new TableColumn<>("Name");
        nameCol.setCellValueFactory(c -> new SimpleStringProperty(
                c.getValue().name() + (c.getValue().isPublic() ? "" : "  (mine)")));
        nameCol.setPrefWidth(240);
        TableColumn<Recipe, String> kcalCol = new TableColumn<>("kcal/100g");
        kcalCol.setCellValueFactory(c -> new SimpleStringProperty(
                String.valueOf((int) Math.round(c.getValue().kcalPer100g()))));
        TableColumn<Recipe, String> macroCol = new TableColumn<>("P / C / F / Fib (per 100g)");
        macroCol.setCellValueFactory(c -> new SimpleStringProperty(String.format("%.1f / %.1f / %.1f / %.1f",
                c.getValue().proteinPer100g(), c.getValue().carbsPer100g(),
                c.getValue().fatPer100g(), c.getValue().fiberPer100g())));
        macroCol.setPrefWidth(220);
        table.getColumns().add(nameCol);
        table.getColumns().add(kcalCol);
        table.getColumns().add(macroCol);
        Label empty = new Label("No recipes match. Build one in the Recipes tab, or have the AI tab draft one for you.");
        empty.getStyleClass().add("muted");
        empty.setWrapText(true);
        table.setPlaceholder(empty);
        table.getSelectionModel().selectedItemProperty().addListener((o, oldV, v) -> state.selected = v);
        VBox.setVgrow(table, Priority.ALWAYS);

        Pager<Recipe> pager = new Pager<>(
                (q, page) -> api.searchAllRecipes(q, page, PAGE_SIZE),
                table);
        search.setOnAction(e -> pager.resetAndLoad(search.getText()));

        HBox amountRow = new HBox(8, new Label("Amount (g, cooked):"), state.amount);

        VBox box = new VBox(8, search, table, pager.loadMoreBar(), amountRow);
        box.setPadding(new Insets(10));

        Tab tab = new Tab("Recipe", box);
        tab.setUserData(state);
        pager.resetAndLoad("");
        return tab;
    }
}
