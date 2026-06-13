package com.calorietracker.desktop.ui;

import com.calorietracker.desktop.api.ApiClient;
import com.calorietracker.desktop.model.DaySummary.MealBlock;
import com.calorietracker.desktop.model.DiaryEntry;
import com.calorietracker.desktop.model.Meal;
import java.time.LocalDate;
import java.util.List;
import javafx.beans.value.ChangeListener;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.MenuButton;
import javafx.scene.control.MenuItem;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.control.SplitPane;
import javafx.scene.control.TitledPane;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

/** Main day view: calendar nav, four meal sections, summary, water. */
public class DayView extends BorderPane {

    private final ApiClient api;
    private final DatePicker datePicker = new DatePicker(LocalDate.now());
    private final VBox mealsPane = new VBox(12);
    private final SummaryCard summaryCard = new SummaryCard();
    private final WaterCard waterCard;

    public DayView(ApiClient api) {
        this.api = api;
        this.waterCard = new WaterCard(api, this::refresh);

        setTop(buildToolbar());

        mealsPane.setPadding(new Insets(16));
        ScrollPane mealsScroll = new ScrollPane(mealsPane);
        mealsScroll.setFitToWidth(true);
        mealsScroll.getStyleClass().add("scroll-pane");

        VBox rightPane = new VBox(14, summaryCard, waterCard);
        rightPane.setPadding(new Insets(16));
        rightPane.setPrefWidth(340);

        SplitPane split = new SplitPane(mealsScroll, rightPane);
        split.setDividerPositions(0.62);
        setCenter(split);
        refresh();
    }

    private HBox buildToolbar() {
        Button prev = new Button("< Prev");
        Button next = new Button("Next >");
        Button today = new Button("Today");
        today.getStyleClass().add("primary-button");
        prev.setOnAction(e -> datePicker.setValue(datePicker.getValue().minusDays(1)));
        next.setOnAction(e -> datePicker.setValue(datePicker.getValue().plusDays(1)));
        today.setOnAction(e -> datePicker.setValue(LocalDate.now()));
        ChangeListener<LocalDate> dateListener = (o, oldV, v) -> refresh();
        datePicker.valueProperty().addListener(dateListener);

        HBox bar = new HBox(8, prev, datePicker, next, today);
        bar.setAlignment(Pos.CENTER_LEFT);
        bar.getStyleClass().add("toolbar");
        return bar;
    }

    public void refresh() {
        LocalDate date = datePicker.getValue();
        Async.run(() -> api.getDaySummary(date), summary -> {
            mealsPane.getChildren().clear();
            for (Meal m : Meal.values()) {
                MealBlock block = summary.byMeal().get(m);
                if (block == null) {
                    block = new MealBlock(0, 0, 0, 0, 0, List.of());
                }
                mealsPane.getChildren().add(buildMealPanel(m, block, date));
            }
            summaryCard.update(summary);
            waterCard.update(summary.water(), date);
        });
    }

    private TitledPane buildMealPanel(Meal meal, MealBlock block, LocalDate date) {
        String title = String.format("%s   -   %d kcal", prettyMeal(meal), block.kcal());
        VBox content = new VBox(6);
        content.setPadding(new Insets(10));
        if (block.entries().isEmpty()) {
            Label empty = new Label("nothing logged yet");
            empty.getStyleClass().add("muted");
            content.getChildren().add(empty);
        }
        for (DiaryEntry e : block.entries()) {
            content.getChildren().add(buildEntryRow(e, meal, date));
        }
        Button addBtn = new Button("+ Add to " + prettyMeal(meal));
        addBtn.getStyleClass().add("add-button");
        addBtn.setMaxWidth(Double.MAX_VALUE);
        addBtn.setOnAction(ev -> openAddDialog(meal, date));
        content.getChildren().add(addBtn);

        TitledPane pane = new TitledPane(title, content);
        pane.setExpanded(true);
        return pane;
    }

    private HBox buildEntryRow(DiaryEntry e, Meal currentMeal, LocalDate currentDate) {
        Label name = new Label(e.name());
        name.getStyleClass().add("entry-name");
        Label amount = new Label(e.amount() == null ? "" : e.amount());
        amount.getStyleClass().add("entry-meta");
        VBox text = new VBox(2, name, amount);

        HBox tags = new HBox(4,
                AiView.macroTag("P "   + AiView.fmt(e.protein()) + "g", "protein"),
                AiView.macroTag("C "   + AiView.fmt(e.carbs())   + "g", "carbs"),
                AiView.macroTag("F "   + AiView.fmt(e.fat())     + "g", "fat"),
                AiView.macroTag("Fib " + AiView.fmt(e.fiber())   + "g", "fiber"));
        tags.setAlignment(Pos.CENTER_RIGHT);

        Label kcal = new Label(e.kcal() + " kcal");
        kcal.getStyleClass().add("entry-kcal");

        MenuButton actions = new MenuButton("...");
        actions.getStyleClass().add("entry-actions");
        MenuItem moveItem = new MenuItem("Move to other meal / day...");
        moveItem.setOnAction(ev -> openMoveCopy("Move entry", currentDate, currentMeal,
                target -> Async.run(() -> api.moveDiary(e.id(), target.date(), target.meal()),
                        ignored -> refresh())));
        MenuItem copyItem = new MenuItem("Copy to other meal / day...");
        copyItem.setOnAction(ev -> openMoveCopy("Copy entry", currentDate, currentMeal,
                target -> Async.run(() -> api.copyDiary(e.id(), target.date(), target.meal()),
                        ignored -> refresh())));
        MenuItem deleteItem = new MenuItem("Delete");
        deleteItem.setOnAction(ev -> Async.run(() -> {
            api.deleteDiary(e.id());
            return null;
        }, ignored -> refresh()));
        actions.getItems().addAll(moveItem, copyItem, new SeparatorMenuItem(), deleteItem);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        VBox right = new VBox(4, kcal, tags);
        right.setAlignment(Pos.CENTER_RIGHT);

        HBox row = new HBox(10, text, spacer, right, actions);
        row.setAlignment(Pos.CENTER_LEFT);
        row.getStyleClass().add("entry-row");
        return row;
    }

    private void openMoveCopy(String title, LocalDate currentDate, Meal currentMeal,
                              java.util.function.Consumer<MoveCopyDialog.Target> onPick) {
        new MoveCopyDialog(title, currentDate, currentMeal).showAndWait()
                .ifPresent(onPick);
    }

    private void openAddDialog(Meal meal, LocalDate date) {
        new AddFoodDialog(api, date, meal).showAndWait().ifPresent(req ->
                Async.run(() -> api.addDiary(req), ignored -> refresh()));
    }

    static String prettyMeal(Meal m) {
        String s = m.name();
        return s.charAt(0) + s.substring(1).toLowerCase();
    }
}
