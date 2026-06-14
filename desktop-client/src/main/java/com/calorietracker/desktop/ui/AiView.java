package com.calorietracker.desktop.ui;

import com.calorietracker.desktop.api.ApiClient;
import com.calorietracker.desktop.model.AddDiaryRequest;
import com.calorietracker.desktop.model.Meal;
import com.calorietracker.desktop.model.ParseResult;
import com.calorietracker.desktop.model.ParsedIngredient;
import com.calorietracker.desktop.model.ParsedRecipe;
import com.calorietracker.desktop.model.ParsedRecipeIngredient;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

/**
 * Chat-style AI view with two modes:
 *   - DIARY  -> calls /api/ai/parse, response has "Add all to diary" controls
 *   - RECIPE -> calls /api/ai/parse-recipe, response has "Save as recipe" controls
 * The chat is preserved across tab switches because CalorieTrackerApp caches
 * the AiView instance. A "Clear chat" button wipes the history on demand.
 */
public class AiView extends BorderPane {

    private enum Mode {
        DIARY("Diary entries"),
        RECIPE("Recipe");
        final String label;
        Mode(String label) { this.label = label; }
        @Override public String toString() { return label; }
    }

    private final ApiClient api;
    private final VBox chatBox = new VBox(14);
    private final ScrollPane chatScroll = new ScrollPane(chatBox);
    private final TextArea input = new TextArea();
    private final ComboBox<Mode> modeBox = new ComboBox<>(FXCollections.observableArrayList(Mode.values()));

    public AiView(ApiClient api) {
        this.api = api;
        setTop(buildHeader());
        setCenter(buildChat());
        setBottom(buildInputBar());
    }

    // ---------------- Header (title + clear chat) ----------------

    private VBox buildHeader() {
        Label title = new Label("AI Food Parser");
        title.getStyleClass().add("section-title");
        Label subtitle = new Label(
                "Describe a meal in plain language. Choose \"Diary entries\" to log it for today, "
                + "or \"Recipe\" to save a reusable recipe.");
        subtitle.getStyleClass().add("muted");
        subtitle.setWrapText(true);

        Button clearBtn = new Button("Clear chat");
        clearBtn.setOnAction(e -> {
            chatBox.getChildren().clear();
            chatBox.getChildren().add(welcomeBubble());
        });

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox top = new HBox(10, title, spacer, clearBtn);
        top.setAlignment(Pos.CENTER_LEFT);

        VBox header = new VBox(4, top, subtitle);
        header.setPadding(new Insets(16, 20, 12, 20));
        return header;
    }

    // ---------------- Chat history ----------------

    private ScrollPane buildChat() {
        chatBox.setPadding(new Insets(8, 20, 8, 20));
        chatScroll.setFitToWidth(true);
        chatScroll.getStyleClass().add("scroll-pane");
        chatBox.getChildren().add(welcomeBubble());
        return chatScroll;
    }

    private HBox welcomeBubble() {
        Label l = new Label("Hi! Type a meal and pick a mode. In Diary mode I list foods to log; "
                + "in Recipe mode I draft a reusable recipe you can save with one click.");
        l.setWrapText(true);
        VBox bubble = new VBox(l);
        bubble.getStyleClass().add("chat-ai");
        HBox row = new HBox(bubble);
        row.setAlignment(Pos.CENTER_LEFT);
        return row;
    }

    // ---------------- Input bar ----------------

    private HBox buildInputBar() {
        input.setPromptText("e.g. 2 scrambled eggs, 1 slice of whole wheat toast with butter, a banana");
        input.setPrefRowCount(2);
        input.setWrapText(true);
        HBox.setHgrow(input, Priority.ALWAYS);

        modeBox.setValue(Mode.DIARY);

        Button sendBtn = new Button("Send");
        sendBtn.getStyleClass().add("primary-button");
        sendBtn.setOnAction(e -> send());
        input.setOnKeyPressed(e -> {
            if (e.isControlDown() && e.getCode() == KeyCode.ENTER) send();
        });

        VBox modeBox2 = new VBox(2, smallLabel("Mode"), modeBox);
        HBox right = new HBox(8, modeBox2, sendBtn);
        right.setAlignment(Pos.BOTTOM_CENTER);

        HBox bar = new HBox(10, input, right);
        bar.setAlignment(Pos.CENTER);
        bar.setPadding(new Insets(12, 20, 16, 20));
        bar.setStyle("-fx-background-color: white;");
        return bar;
    }

    private Label smallLabel(String text) {
        Label l = new Label(text);
        l.setStyle("-fx-font-size: 10px; -fx-text-fill: #718096;");
        return l;
    }

    // ---------------- Send dispatch ----------------

    private void send() {
        String text = input.getText().trim();
        if (text.isEmpty()) return;
        Mode m = modeBox.getValue();
        chatBox.getChildren().add(userBubble(text));
        chatBox.getChildren().add(thinkingBubble());
        scrollToBottom();
        input.clear();

        if (m == Mode.DIARY) {
            Async.run(() -> api.parseIngredients(text), result -> {
                replaceLastBubble(aiDiaryBubble(result));
                scrollToBottom();
            });
        } else {
            Async.run(() -> api.parseRecipe(text), result -> {
                replaceLastBubble(aiRecipeBubble(result));
                scrollToBottom();
            });
        }
    }

    private HBox userBubble(String text) {
        Label l = new Label(text);
        l.setWrapText(true);
        VBox bubble = new VBox(l);
        bubble.getStyleClass().add("chat-user");
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox row = new HBox(spacer, bubble);
        row.setAlignment(Pos.CENTER_RIGHT);
        return row;
    }

    private HBox thinkingBubble() {
        Label l = new Label("thinking...");
        l.setStyle("-fx-font-style: italic;");
        VBox bubble = new VBox(l);
        bubble.getStyleClass().add("chat-ai");
        HBox row = new HBox(bubble);
        row.setAlignment(Pos.CENTER_LEFT);
        return row;
    }

    private void replaceLastBubble(HBox replacement) {
        if (!chatBox.getChildren().isEmpty()) {
            chatBox.getChildren().remove(chatBox.getChildren().size() - 1);
        }
        chatBox.getChildren().add(replacement);
    }

    // ---------------- Diary mode response bubble ----------------

    private HBox aiDiaryBubble(ParseResult result) {
        VBox bubble = new VBox(10);
        bubble.getStyleClass().add("chat-ai");
        // Wider so the per-item rows + the "Add all to diary" action bar at the
        // bottom never run out of horizontal space.
        bubble.setMinWidth(720);
        bubble.setPrefWidth(820);
        bubble.setMaxWidth(900);

        Label header = new Label("Parsed for diary");
        header.getStyleClass().add("card-title");
        bubble.getChildren().add(header);

        if (result.items() == null || result.items().isEmpty()) {
            bubble.getChildren().add(new Label("(nothing detected)"));
        } else {
            for (ParsedIngredient item : result.items()) {
                VBox row = (VBox) diaryItemRow(item).getChildren().get(0);
                bubble.getChildren().add(diaryItemRowWrap(row));

                // Library origin badge + "Save to my library" button. We probe
                // searchAll to know whether the item exists, and the first hit
                // tells us the origin: isPublic() -> app library, else my lib.
                Label badge = new Label("checking library...");
                badge.getStyleClass().addAll("macro-tag", "library-check");

                Button saveLibBtn = new Button("Save to my library");
                saveLibBtn.getStyleClass().add("add-button");
                saveLibBtn.setVisible(false);
                saveLibBtn.managedProperty().bind(saveLibBtn.visibleProperty());
                saveLibBtn.setOnAction(ev -> {
                    saveLibBtn.setDisable(true);
                    saveLibBtn.setText("Saving...");
                    double grams = parseGrams(item.quantity());
                    double scale = grams > 0 ? 100.0 / grams : 1.0;
                    Async.run(() -> api.createIngredient(new com.calorietracker.desktop.model.CreateIngredientRequest(
                                    item.name(), null,
                                    (int) Math.round(item.calories() * scale),
                                    round1(item.protein() * scale),
                                    round1(item.carbs()   * scale),
                                    round1(item.fat()     * scale),
                                    round1(item.fiber()   * scale))),
                            saved -> {
                                saveLibBtn.setText("Saved!");
                                badge.setText("in my library");
                                badge.getStyleClass().setAll("macro-tag", "library-mine");
                            });
                });

                HBox actions = new HBox(8, badge, saveLibBtn);
                actions.setAlignment(Pos.CENTER_LEFT);
                row.getChildren().add(actions);

                Async.run(() -> api.searchAllIngredients(item.name(), 0, 1), hits -> {
                    if (hits != null && !hits.isEmpty()
                            && hits.get(0).name().equalsIgnoreCase(item.name().trim())) {
                        boolean appLib = hits.get(0).isPublic();
                        badge.setText(appLib ? "in app library" : "in my library");
                        badge.getStyleClass().setAll("macro-tag",
                                appLib ? "library-app" : "library-mine");
                        saveLibBtn.setVisible(false);
                    } else {
                        badge.setText("not in any library — AI estimate");
                        badge.getStyleClass().setAll("macro-tag", "library-missing");
                        // Only show Save-to-my-library when there's no existing match.
                        saveLibBtn.setVisible(true);
                    }
                });
            }
        }

        Label totals = new Label(String.format(
                "Totals: %d kcal  ·  P %.1f g  ·  C %.1f g  ·  F %.1f g  ·  Fiber %.1f g",
                result.totalCalories(), result.totalProtein(), result.totalCarbs(),
                result.totalFat(), result.totalFiber()));
        totals.getStyleClass().add("entry-name");
        totals.setWrapText(true);
        bubble.getChildren().add(totals);

        DatePicker date = new DatePicker(LocalDate.now());
        date.setPrefWidth(140);
        ComboBox<Meal> meal = new ComboBox<>(FXCollections.observableArrayList(Meal.values()));
        meal.setValue(defaultMealForNow());
        meal.setPrefWidth(130);
        Button addBtn = new Button("Add all to diary");
        addBtn.getStyleClass().add("success-button");
        // Force the button to keep its full label even when something upstream
        // is asking the parent HBox to shrink.
        addBtn.setMinWidth(Region.USE_PREF_SIZE);
        addBtn.setOnAction(e -> {
            addBtn.setDisable(true);
            addBtn.setText("Saving...");
            LocalDate d = date.getValue();
            Meal mealVal = meal.getValue();
            List<AddDiaryRequest> reqs = new ArrayList<>();
            for (ParsedIngredient i : result.items()) {
                reqs.add(AddDiaryRequest.custom(d.toString(), mealVal,
                        i.name() + (i.quantity() == null || i.quantity().isBlank() ? "" : " x" + i.quantity()),
                        i.calories(), i.protein(), i.carbs(), i.fat(), i.fiber()));
            }
            Async.run(() -> { for (AddDiaryRequest r : reqs) api.addDiary(r); return null; },
                    ignored -> { addBtn.setText("Added!"); });
        });
        HBox addRow = new HBox(10, new Label("Date:"), date, new Label("Meal:"), meal, addBtn);
        addRow.setAlignment(Pos.CENTER_LEFT);
        bubble.getChildren().add(addRow);

        HBox row = new HBox(bubble);
        row.setAlignment(Pos.CENTER_LEFT);
        return row;
    }

    private HBox diaryItemRow(ParsedIngredient i) {
        Label name = new Label(i.name() + (i.quantity() == null || i.quantity().isBlank() ? "" : "  x " + i.quantity()));
        name.getStyleClass().add("entry-name");
        Label kcal = new Label(i.calories() + " kcal");
        kcal.getStyleClass().add("entry-kcal");
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox top = new HBox(8, name, spacer, kcal);
        top.setAlignment(Pos.CENTER_LEFT);

        HBox tags = new HBox(6,
                macroTag("P "   + fmt(i.protein()) + " g", "protein"),
                macroTag("C "   + fmt(i.carbs())   + " g", "carbs"),
                macroTag("F "   + fmt(i.fat())     + " g", "fat"),
                macroTag("Fib " + fmt(i.fiber())   + " g", "fiber"));
        VBox row = new VBox(4, top, tags);
        row.getStyleClass().add("entry-row");
        HBox wrap = new HBox(row);
        HBox.setHgrow(row, Priority.ALWAYS);
        return wrap;
    }

    /** Wraps an already-built per-item VBox into an HBox row consistent with the chat layout. */
    private static HBox diaryItemRowWrap(VBox row) {
        row.getStyleClass().add("entry-row");
        HBox wrap = new HBox(row);
        HBox.setHgrow(row, Priority.ALWAYS);
        return wrap;
    }

    /**
     * Extract a gram weight from the AI's free-text quantity field.
     * Handles "500g", "500 g", "500 grams", "1.5 kg", "1500ml" (≈ grams for
     * water-density foods). Returns 0 when the quantity isn't expressed as a
     * mass (e.g. "1 cup", "2 slices") — caller treats 0 as "leave values as-is,
     * the AI already reported per-portion numbers and that's the best we have".
     */
    private static double parseGrams(String s) {
        if (s == null || s.isBlank()) return 0;
        String t = s.trim().toLowerCase().replace(',', '.');
        java.util.regex.Matcher m = java.util.regex.Pattern
                .compile("(\\d+(?:\\.\\d+)?)\\s*(kg|g|gram|grams|ml|l)\\b").matcher(t);
        if (!m.find()) return 0;
        double n = Double.parseDouble(m.group(1));
        return switch (m.group(2)) {
            case "kg" -> n * 1000.0;
            case "l"  -> n * 1000.0;
            default   -> n;     // g, gram, grams, ml all treated as grams
        };
    }

    private static double round1(double v) {
        return Math.round(v * 10.0) / 10.0;
    }

    // ---------------- Recipe mode response bubble ----------------

    private HBox aiRecipeBubble(ParsedRecipe result) {
        VBox bubble = new VBox(12);
        bubble.getStyleClass().add("chat-ai");
        // Wide enough that the recipe-row "name + macros" line never collides with
        // the per-100g tag chips on the right.
        bubble.setMinWidth(740);
        bubble.setPrefWidth(840);
        bubble.setMaxWidth(960);

        Label header = new Label("Recipe blueprint — edit name + cooked weight before saving if you like");
        header.getStyleClass().add("card-title");
        header.setWrapText(true);
        bubble.getChildren().add(header);

        // Raw sum of ingredient grams — used as the default cooked-weight.
        double rawGrams = 0;
        if (result.ingredients() != null) {
            for (ParsedRecipeIngredient i : result.ingredients()) rawGrams += i.amountGrams();
        }

        TextField nameField = new TextField(result.name() == null ? "My recipe" : result.name());
        nameField.setPrefWidth(360);
        TextField cookedGramsField = new TextField(rawGrams > 0 ? String.format("%.0f", rawGrams) : "");
        cookedGramsField.setPromptText("auto from sum of raw");
        cookedGramsField.setPrefWidth(120);
        boolean[] cookedTouched = {false};
        cookedGramsField.focusedProperty().addListener((obs, was, isFocused) -> {
            if (isFocused) cookedTouched[0] = true;
        });

        HBox nameRow = new HBox(8,
                new Label("Name:"), nameField,
                new Label("Cooked (g):"), cookedGramsField);
        nameRow.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(nameField, Priority.ALWAYS);
        bubble.getChildren().add(nameRow);

        Label hint = new Label(
                "Cooked weight = raw sum by default. Override if cooking changes the dish weight " +
                "(pasta absorbs water, meat loses it). Per-100g values below are based on the cooked weight.");
        hint.setWrapText(true);
        hint.getStyleClass().add("muted");
        bubble.getChildren().add(hint);

        double k = 0, p = 0, c = 0, f = 0, fib = 0;
        if (result.ingredients() == null || result.ingredients().isEmpty()) {
            bubble.getChildren().add(new Label("(nothing detected)"));
        } else {
            for (ParsedRecipeIngredient i : result.ingredients()) {
                bubble.getChildren().add(recipeItemRow(i));
                double factor = i.amountGrams() / 100.0;
                k   += i.kcalPer100g()    * factor;
                p   += i.proteinPer100g() * factor;
                c   += i.carbsPer100g()   * factor;
                f   += i.fatPer100g()     * factor;
                fib += i.fiberPer100g()   * factor;
            }
        }
        final double kcalTotal = k, proTotal = p, carTotal = c, fatTotal = f, fibTotal = fib;
        final double rawSum = rawGrams;

        Label rawTotals = new Label(String.format(
                "Raw totals: %d kcal  ·  P %.1f g  ·  C %.1f g  ·  F %.1f g  ·  Fiber %.1f g  (raw %.0f g)",
                Math.round(kcalTotal), proTotal, carTotal, fatTotal, fibTotal, rawSum));
        rawTotals.getStyleClass().add("entry-name");
        rawTotals.setWrapText(true);
        bubble.getChildren().add(rawTotals);

        Label per100g = new Label();
        per100g.getStyleClass().add("entry-name");
        per100g.setWrapText(true);
        Runnable refreshPer100g = () -> {
            double cooked = parseCookedOr(cookedGramsField.getText(), rawSum);
            if (cooked <= 0) {
                per100g.setText("Per 100 g (cooked): —");
            } else {
                double m = 100.0 / cooked;
                per100g.setText(String.format(
                        "Per 100 g (cooked): %d kcal  ·  P %.1f g  ·  C %.1f g  ·  F %.1f g  ·  Fiber %.1f g  (cooked %.0f g)",
                        Math.round(kcalTotal * m), proTotal * m, carTotal * m, fatTotal * m, fibTotal * m, cooked));
            }
        };
        cookedGramsField.textProperty().addListener((obs, oldV, newV) -> refreshPer100g.run());
        refreshPer100g.run();
        bubble.getChildren().add(per100g);

        Button saveBtn = new Button("Save as recipe");
        saveBtn.getStyleClass().add("success-button");
        saveBtn.setMinWidth(Region.USE_PREF_SIZE);
        saveBtn.setOnAction(e -> {
            saveBtn.setDisable(true);
            saveBtn.setText("Saving…");
            double cooked = parseCookedOr(cookedGramsField.getText(), rawSum);
            ParsedRecipe blueprint = new ParsedRecipe(
                    nameField.getText().trim().isEmpty() ? "My recipe" : nameField.getText().trim(),
                    1,
                    result.ingredients(),
                    cooked > 0 ? cooked : null);
            Async.run(() -> api.saveAiRecipe(blueprint), saved -> {
                saveBtn.setText("Saved as \"" + saved.name() + "\"");
            });
        });
        HBox saveRow = new HBox(saveBtn);
        saveRow.setAlignment(Pos.CENTER_LEFT);
        bubble.getChildren().add(saveRow);

        HBox row = new HBox(bubble);
        row.setAlignment(Pos.CENTER_LEFT);
        return row;
    }

    /** Parse the cooked-weight text field, falling back to a default on any error. */
    private static double parseCookedOr(String raw, double defaultGrams) {
        if (raw == null || raw.isBlank()) return defaultGrams;
        try {
            double v = Double.parseDouble(raw.trim().replace(',', '.'));
            return v > 0 ? v : defaultGrams;
        } catch (NumberFormatException e) {
            return defaultGrams;
        }
    }

    private HBox recipeItemRow(ParsedRecipeIngredient i) {
        Label name = new Label(i.name());
        name.getStyleClass().add("entry-name");
        Label amount = new Label(fmt(i.amountGrams()) + " g  ·  " + i.kcalPer100g() + " kcal/100g");
        amount.getStyleClass().add("entry-meta");
        VBox text = new VBox(2, name, amount);
        text.setMinWidth(200);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        HBox tags = new HBox(6,
                macroTag("P "   + fmt(i.proteinPer100g()) + "/100g", "protein"),
                macroTag("C "   + fmt(i.carbsPer100g())   + "/100g", "carbs"),
                macroTag("F "   + fmt(i.fatPer100g())     + "/100g", "fat"),
                macroTag("Fib " + fmt(i.fiberPer100g())   + "/100g", "fiber"));
        tags.setAlignment(Pos.CENTER_RIGHT);
        tags.setMinWidth(Region.USE_PREF_SIZE);

        HBox row = new HBox(8, text, spacer, tags);
        row.setAlignment(Pos.CENTER_LEFT);
        row.getStyleClass().add("entry-row");
        return row;
    }

    // ---------------- Helpers ----------------

    static Label macroTag(String text, String variant) {
        Label l = new Label(text);
        l.getStyleClass().addAll("macro-tag", variant);
        return l;
    }

    static String fmt(double v) {
        return (v == Math.floor(v)) ? String.valueOf((int) v) : String.format("%.1f", v);
    }

    private static Meal defaultMealForNow() {
        int hour = java.time.LocalTime.now().getHour();
        if (hour < 10) return Meal.BREAKFAST;
        if (hour < 14) return Meal.LUNCH;
        if (hour < 18) return Meal.SNACK;
        return Meal.DINNER;
    }

    private void scrollToBottom() {
        Platform.runLater(() -> {
            chatScroll.layout();
            chatScroll.setVvalue(1.0);
        });
    }
}
