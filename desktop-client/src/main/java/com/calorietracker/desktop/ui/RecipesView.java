package com.calorietracker.desktop.ui;

import com.calorietracker.desktop.api.ApiClient;
import com.calorietracker.desktop.model.Ingredient;
import com.calorietracker.desktop.model.Recipe;
import javafx.beans.property.SimpleStringProperty;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

/**
 * Library view with two tabs: Recipes and Ingredients. Both show ONLY the
 * user's own items (public ones surface in AddFoodDialog / RecipeBuilderDialog).
 * Each table is paginated - 20 rows per page with a "Load more" button.
 */
public class RecipesView extends BorderPane {

    private static final int PAGE_SIZE = 20;

    private final ApiClient api;

    public RecipesView(ApiClient api) {
        this.api = api;
        TabPane tabs = new TabPane(buildRecipesTab(), buildIngredientsTab());
        tabs.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        setCenter(tabs);
    }

    // ---------------- Recipes tab ----------------

    private Tab buildRecipesTab() {
        TextField search = new TextField();
        search.setPromptText("search your recipes (press Enter)");
        Button searchBtn = new Button("Search");
        Button newBtn = new Button("+ New recipe");
        newBtn.getStyleClass().add("primary-button");
        Button delBtn = new Button("Delete selected");

        TableView<Recipe> table = new TableView<>();
        TableColumn<Recipe, String> nameCol = new TableColumn<>("Name");
        nameCol.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().name()));
        nameCol.setPrefWidth(240);
        TableColumn<Recipe, String> servCol = new TableColumn<>("Servings");
        servCol.setCellValueFactory(c -> new SimpleStringProperty(String.valueOf(c.getValue().servings())));
        TableColumn<Recipe, String> kcalCol = new TableColumn<>("kcal");
        kcalCol.setCellValueFactory(c -> new SimpleStringProperty(String.valueOf(c.getValue().totalKcal())));
        TableColumn<Recipe, String> proCol = new TableColumn<>("Protein");
        proCol.setCellValueFactory(c -> new SimpleStringProperty(AiView.fmt(c.getValue().totalProtein()) + " g"));
        TableColumn<Recipe, String> carbCol = new TableColumn<>("Carbs");
        carbCol.setCellValueFactory(c -> new SimpleStringProperty(AiView.fmt(c.getValue().totalCarbs()) + " g"));
        TableColumn<Recipe, String> fatCol = new TableColumn<>("Fat");
        fatCol.setCellValueFactory(c -> new SimpleStringProperty(AiView.fmt(c.getValue().totalFat()) + " g"));
        TableColumn<Recipe, String> fibCol = new TableColumn<>("Fiber");
        fibCol.setCellValueFactory(c -> new SimpleStringProperty(AiView.fmt(c.getValue().totalFiber()) + " g"));
        TableColumn<Recipe, String> ingCol = new TableColumn<>("Ingredients");
        ingCol.setCellValueFactory(c -> new SimpleStringProperty(
                c.getValue().ingredients().stream()
                        .map(l -> l.ingredientName() + " " + l.amountGrams() + " g")
                        .reduce((a, b) -> a + ", " + b).orElse("(none)")));
        ingCol.setPrefWidth(360);
        table.getColumns().add(nameCol);
        table.getColumns().add(servCol);
        table.getColumns().add(kcalCol);
        table.getColumns().add(proCol);
        table.getColumns().add(carbCol);
        table.getColumns().add(fatCol);
        table.getColumns().add(fibCol);
        table.getColumns().add(ingCol);
        Label empty = new Label("No recipes yet. Click + New recipe (or build one via the AI tab).");
        empty.getStyleClass().add("muted");
        table.setPlaceholder(empty);

        Pager<Recipe> pager = new Pager<>(
                (q, page) -> api.listMyRecipes(q, page, PAGE_SIZE),
                table);
        searchBtn.setOnAction(e -> pager.resetAndLoad(search.getText()));
        search.setOnAction(e -> pager.resetAndLoad(search.getText()));
        newBtn.setOnAction(e -> new RecipeBuilderDialog(api).showAndWait()
                .ifPresent(created -> pager.resetAndLoad(search.getText())));
        delBtn.setOnAction(e -> {
            Recipe r = table.getSelectionModel().getSelectedItem();
            if (r == null) return;
            Async.run(() -> { api.deleteRecipe(r.id()); return null; },
                    ignored -> pager.resetAndLoad(search.getText()));
        });

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox toolbar = new HBox(8, search, searchBtn, spacer, delBtn, newBtn);
        toolbar.setAlignment(Pos.CENTER_LEFT);
        toolbar.getStyleClass().add("toolbar");

        VBox body = new VBox(table, pager.loadMoreBar());
        body.setPadding(new Insets(16));
        body.setSpacing(8);
        VBox.setVgrow(table, Priority.ALWAYS);

        BorderPane content = new BorderPane();
        content.setTop(toolbar);
        content.setCenter(body);
        pager.resetAndLoad("");
        return new Tab("Recipes", content);
    }

    // ---------------- Ingredients tab ----------------

    private Tab buildIngredientsTab() {
        TextField search = new TextField();
        search.setPromptText("search your foods (press Enter)");
        Button searchBtn = new Button("Search");
        Button newBtn = new Button("+ New food");
        newBtn.getStyleClass().add("primary-button");
        Button delBtn = new Button("Delete selected");

        TableView<Ingredient> table = new TableView<>();
        TableColumn<Ingredient, String> nameCol = new TableColumn<>("Name");
        nameCol.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().name()));
        nameCol.setPrefWidth(260);
        TableColumn<Ingredient, String> brandCol = new TableColumn<>("Brand");
        brandCol.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().brand() == null ? "" : c.getValue().brand()));
        brandCol.setPrefWidth(140);
        TableColumn<Ingredient, String> kcalCol = new TableColumn<>("kcal/100g");
        kcalCol.setCellValueFactory(c -> new SimpleStringProperty(String.valueOf(c.getValue().kcalPer100g())));
        TableColumn<Ingredient, String> proCol = new TableColumn<>("Protein/100g");
        proCol.setCellValueFactory(c -> new SimpleStringProperty(AiView.fmt(c.getValue().proteinPer100g()) + " g"));
        TableColumn<Ingredient, String> carbCol = new TableColumn<>("Carbs/100g");
        carbCol.setCellValueFactory(c -> new SimpleStringProperty(AiView.fmt(c.getValue().carbsPer100g()) + " g"));
        TableColumn<Ingredient, String> fatCol = new TableColumn<>("Fat/100g");
        fatCol.setCellValueFactory(c -> new SimpleStringProperty(AiView.fmt(c.getValue().fatPer100g()) + " g"));
        TableColumn<Ingredient, String> fibCol = new TableColumn<>("Fiber/100g");
        fibCol.setCellValueFactory(c -> new SimpleStringProperty(AiView.fmt(c.getValue().fiberPer100g()) + " g"));
        table.getColumns().add(nameCol);
        table.getColumns().add(brandCol);
        table.getColumns().add(kcalCol);
        table.getColumns().add(proCol);
        table.getColumns().add(carbCol);
        table.getColumns().add(fatCol);
        table.getColumns().add(fibCol);
        Label empty = new Label("No foods yet. Click + New food, or let the AI tab create some for you.");
        empty.getStyleClass().add("muted");
        table.setPlaceholder(empty);

        Pager<Ingredient> pager = new Pager<>(
                (q, page) -> api.listMyIngredients(q, page, PAGE_SIZE),
                table);
        searchBtn.setOnAction(e -> pager.resetAndLoad(search.getText()));
        search.setOnAction(e -> pager.resetAndLoad(search.getText()));
        newBtn.setOnAction(e -> new IngredientEditorDialog(api).showAndWait()
                .ifPresent(created -> pager.resetAndLoad(search.getText())));
        delBtn.setOnAction(e -> {
            Ingredient ing = table.getSelectionModel().getSelectedItem();
            if (ing == null) return;
            Async.run(() -> { api.deleteIngredient(ing.id()); return null; },
                    ignored -> pager.resetAndLoad(search.getText()));
        });

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox toolbar = new HBox(8, search, searchBtn, spacer, delBtn, newBtn);
        toolbar.setAlignment(Pos.CENTER_LEFT);
        toolbar.getStyleClass().add("toolbar");

        VBox body = new VBox(table, pager.loadMoreBar());
        body.setPadding(new Insets(16));
        body.setSpacing(8);
        VBox.setVgrow(table, Priority.ALWAYS);

        BorderPane content = new BorderPane();
        content.setTop(toolbar);
        content.setCenter(body);
        pager.resetAndLoad("");
        return new Tab("Foods", content);
    }
}
