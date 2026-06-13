package com.calorietracker.desktop.ui;

import com.calorietracker.desktop.api.ApiClient;
import com.calorietracker.desktop.model.Ingredient;
import com.calorietracker.desktop.model.Objective;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Spinner;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

/**
 * Rule-based food search with grouped AND/OR expressions.
 *
 * <p>Each rule renders as a short readable summary (e.g. {@code "1 × Protein
 * > 20"} or {@code "1 × Fat < 0.5 × Daily fat target"}) with an Edit button.
 * Clicking Edit opens a roomy modal dialog where every dropdown has space to
 * show its full label - so building expressions doesn't require squinting.
 *
 * <p>Groups can be nested up to {@code MAX_NESTING} deep; each group has its
 * own AND/OR connector. The evaluation engine runs in-memory over up to
 * {@code FETCH_LIMIT} foods pulled once when the tab opens.
 */
public class AdvancedSearchView extends BorderPane {

    private static final int FETCH_LIMIT = 500;
    private static final int MAX_NESTING = 4;

    // ---------------- DOMAIN ENUMS ----------------

    private enum FoodField {
        KCAL    ("Calories",  i -> (double) i.kcalPer100g()),
        PROTEIN ("Protein",   Ingredient::proteinPer100g),
        CARBS   ("Carbs",     Ingredient::carbsPer100g),
        FAT     ("Fat",       Ingredient::fatPer100g),
        FIBER   ("Fiber",     Ingredient::fiberPer100g);
        final String label;
        final Function<Ingredient, Double> extract;
        FoodField(String label, Function<Ingredient, Double> extract) {
            this.label = label; this.extract = extract;
        }
        @Override public String toString() { return label; }
    }

    private enum GoalRef {
        CALORIE("Daily calorie target", o -> o == null ? null : o.dailyCalorieTarget()),
        PROTEIN("Daily protein target", o -> o == null ? null : o.dailyProteinTargetG()),
        CARBS  ("Daily carbs target",   o -> o == null ? null : o.dailyCarbsTargetG()),
        FAT    ("Daily fat target",     o -> o == null ? null : o.dailyFatTargetG()),
        FIBER  ("Daily fiber target",   o -> o == null ? null : o.dailyFiberTargetG()),
        WATER  ("Daily water target",   o -> o == null ? null : o.dailyWaterTargetMl());
        final String label;
        final Function<Objective, Integer> extract;
        GoalRef(String label, Function<Objective, Integer> extract) {
            this.label = label; this.extract = extract;
        }
        @Override public String toString() { return label; }
    }

    private enum SideKind {
        PLAIN("Plain number"),
        FOOD ("Food field"),
        GOAL ("Daily goal");
        final String label;
        SideKind(String label) { this.label = label; }
        @Override public String toString() { return label; }
    }

    private enum Op {
        GT(">"), GE(">="), LT("<"), LE("<="), EQ("=");
        final String symbol;
        Op(String symbol) { this.symbol = symbol; }
        @Override public String toString() { return symbol; }
        boolean test(double l, double r) {
            return switch (this) {
                case GT -> l >  r;
                case GE -> l >= r;
                case LT -> l <  r;
                case LE -> l <= r;
                case EQ -> Math.abs(l - r) < 1e-9;
            };
        }
    }

    private enum BoolOp {
        AND("Match ALL (AND)"),
        OR ("Match ANY (OR)");
        final String label;
        BoolOp(String label) { this.label = label; }
        @Override public String toString() { return label; }
    }

    // ---------------- PLAIN DATA MODEL ----------------

    /** One side of a rule: {@code coef × (plain | food field | goal)}. */
    private static final class Side {
        double coef;
        SideKind kind;
        FoodField foodField;
        GoalRef goalRef;

        Side(double coef, SideKind kind, FoodField foodField, GoalRef goalRef) {
            this.coef = coef;
            this.kind = kind;
            this.foodField = foodField;
            this.goalRef = goalRef;
        }

        Side copy() { return new Side(coef, kind, foodField, goalRef); }

        double evaluate(Ingredient food, Objective obj) {
            return switch (kind) {
                case PLAIN -> coef;
                case FOOD  -> coef * (foodField == null ? 0.0 : foodField.extract.apply(food));
                case GOAL  -> {
                    Integer g = (goalRef == null || obj == null) ? null : goalRef.extract.apply(obj);
                    yield (g == null) ? Double.NaN : coef * g;
                }
            };
        }

        /** Compact human description, e.g. {@code "1 × Protein"} or {@code "0.5 × Daily fat target"}. */
        String describe() {
            return switch (kind) {
                case PLAIN -> fmt(coef);
                case FOOD  -> coefPrefix() + (foodField == null ? "?" : foodField.label);
                case GOAL  -> coefPrefix() + (goalRef == null ? "?" : goalRef.label);
            };
        }

        private String coefPrefix() {
            return Math.abs(coef - 1.0) < 1e-9 ? "" : fmt(coef) + " × ";
        }

        private static String fmt(double v) {
            return (v == Math.floor(v) && !Double.isInfinite(v))
                    ? String.valueOf((long) v)
                    : String.format("%.2f", v).replaceAll("0+$", "").replaceAll("\\.$", "");
        }
    }

    /** Mutable rule state - the editor dialog reads from and writes back to one of these. */
    private static final class RuleState {
        Side lhs;
        Op op;
        Side rhs;

        RuleState(Side lhs, Op op, Side rhs) {
            this.lhs = lhs;
            this.op = op;
            this.rhs = rhs;
        }

        static RuleState defaults() {
            return new RuleState(
                    new Side(1.0, SideKind.FOOD, FoodField.PROTEIN, GoalRef.PROTEIN),
                    Op.GT,
                    new Side(20.0, SideKind.PLAIN, FoodField.CARBS, GoalRef.CALORIE));
        }

        String summary() {
            return lhs.describe() + "  " + op.symbol + "  " + rhs.describe();
        }

        boolean evaluate(Ingredient food, Objective obj) {
            double l = lhs.evaluate(food, obj);
            double r = rhs.evaluate(food, obj);
            if (Double.isNaN(l) || Double.isNaN(r)) return true; // missing goal target = pass-through
            return op.test(l, r);
        }
    }

    /** Common contract for things that can sit inside a GroupBox. */
    private interface RuleNode {
        boolean evaluate(Ingredient food, Objective obj);
    }

    // ---------------- RULE EDIT DIALOG ----------------

    /**
     * Wide modal editor for a single rule. The user picks Source on each side
     * (Plain / Food / Goal), the matching field, the coefficient, and an
     * operator; a live preview at the bottom shows the resulting summary.
     */
    private static final class RuleEditDialog extends Dialog<RuleState> {

        RuleEditDialog(RuleState initial) {
            setTitle("Edit rule");
            setHeaderText("Build a comparison between two values.");
            setResizable(true);
            com.calorietracker.desktop.AppContext.prepareDialog(this);

            // --- LHS controls ---
            Spinner<Double>     lhsCoef = wideSpinner(initial.lhs.coef);
            ComboBox<SideKind>  lhsKind = wideCombo(SideKind.values(), initial.lhs.kind, 200);
            ComboBox<FoodField> lhsFood = wideCombo(FoodField.values(),
                    initial.lhs.foodField == null ? FoodField.PROTEIN : initial.lhs.foodField, 220);
            ComboBox<GoalRef>   lhsGoal = wideCombo(GoalRef.values(),
                    initial.lhs.goalRef == null ? GoalRef.PROTEIN : initial.lhs.goalRef, 220);

            // --- Op picker: 5 toggle buttons so the > < = signs are always fully visible.
            //     The previous ComboBox hid the symbol behind the dropdown chevron.
            javafx.scene.control.ToggleGroup opGroup = new javafx.scene.control.ToggleGroup();
            HBox opButtons = new HBox(4);
            opButtons.setAlignment(Pos.CENTER);
            for (Op opVal : Op.values()) {
                javafx.scene.control.ToggleButton tb = new javafx.scene.control.ToggleButton(opVal.symbol);
                tb.setUserData(opVal);
                tb.setToggleGroup(opGroup);
                tb.getStyleClass().add("op-toggle");
                tb.setMinWidth(44);
                if (opVal == initial.op) tb.setSelected(true);
                opButtons.getChildren().add(tb);
            }
            // ToggleGroup default allows zero selected; guard against the user
            // accidentally clicking the chosen one twice to deselect it.
            opGroup.selectedToggleProperty().addListener((o, prev, now) -> {
                if (now == null && prev != null) prev.setSelected(true);
            });
            // Helper used by snapshot()/preview to read the picked op safely.
            java.util.function.Supplier<Op> opReader = () -> {
                javafx.scene.control.Toggle t = opGroup.getSelectedToggle();
                return t == null ? initial.op : (Op) t.getUserData();
            };

            // --- RHS controls ---
            Spinner<Double>     rhsCoef = wideSpinner(initial.rhs.coef);
            ComboBox<SideKind>  rhsKind = wideCombo(SideKind.values(), initial.rhs.kind, 200);
            ComboBox<FoodField> rhsFood = wideCombo(FoodField.values(),
                    initial.rhs.foodField == null ? FoodField.CARBS : initial.rhs.foodField, 220);
            ComboBox<GoalRef>   rhsGoal = wideCombo(GoalRef.values(),
                    initial.rhs.goalRef == null ? GoalRef.CALORIE : initial.rhs.goalRef, 220);

            // --- Visibility: hide irrelevant combos based on Source ---
            Runnable refreshVis = () -> {
                lhsFood.setVisible(lhsKind.getValue() == SideKind.FOOD);
                lhsFood.setManaged(lhsKind.getValue() == SideKind.FOOD);
                lhsGoal.setVisible(lhsKind.getValue() == SideKind.GOAL);
                lhsGoal.setManaged(lhsKind.getValue() == SideKind.GOAL);
                rhsFood.setVisible(rhsKind.getValue() == SideKind.FOOD);
                rhsFood.setManaged(rhsKind.getValue() == SideKind.FOOD);
                rhsGoal.setVisible(rhsKind.getValue() == SideKind.GOAL);
                rhsGoal.setManaged(rhsKind.getValue() == SideKind.GOAL);
            };
            lhsKind.valueProperty().addListener((o, a, b) -> refreshVis.run());
            rhsKind.valueProperty().addListener((o, a, b) -> refreshVis.run());
            refreshVis.run();

            // --- Same-food-field exclusion: LHS Food/Protein <-> RHS Food/Protein ---
            Runnable refreshFoodExclusion = () -> {
                FoodField lhsPick = lhsKind.getValue() == SideKind.FOOD ? lhsFood.getValue() : null;
                FoodField rhsPick = rhsKind.getValue() == SideKind.FOOD ? rhsFood.getValue() : null;
                rebuildFoodCombo(lhsFood, rhsPick);
                rebuildFoodCombo(rhsFood, lhsPick);
            };
            lhsKind.valueProperty().addListener((o, a, b) -> refreshFoodExclusion.run());
            rhsKind.valueProperty().addListener((o, a, b) -> refreshFoodExclusion.run());
            lhsFood.valueProperty().addListener((o, a, b) -> refreshFoodExclusion.run());
            rhsFood.valueProperty().addListener((o, a, b) -> refreshFoodExclusion.run());
            refreshFoodExclusion.run();

            // --- Layout ---
            Label leftTitle  = new Label("LEFT SIDE");
            leftTitle.getStyleClass().add("card-title");
            Label opTitle    = new Label("OPERATOR");
            opTitle.getStyleClass().add("card-title");
            Label rightTitle = new Label("RIGHT SIDE");
            rightTitle.getStyleClass().add("card-title");

            GridPane leftGrid  = sideGrid(lhsCoef, lhsKind, lhsFood, lhsGoal);
            GridPane rightGrid = sideGrid(rhsCoef, rhsKind, rhsFood, rhsGoal);

            VBox leftCol  = new VBox(8, leftTitle, leftGrid);
            VBox opCol    = new VBox(8, opTitle, opButtons);
            opCol.setAlignment(Pos.TOP_CENTER);
            opCol.setMinWidth(260);
            VBox rightCol = new VBox(8, rightTitle, rightGrid);

            HBox sides = new HBox(20, leftCol, opCol, rightCol);
            sides.setAlignment(Pos.TOP_LEFT);

            // Live preview
            Label previewLabel = new Label();
            previewLabel.getStyleClass().add("medium-number");
            previewLabel.setStyle("-fx-text-fill: #2c5282;");
            Label previewTitle = new Label("Preview:");
            previewTitle.getStyleClass().add("muted");
            HBox previewRow = new HBox(8, previewTitle, previewLabel);
            previewRow.setAlignment(Pos.CENTER_LEFT);
            previewRow.setPadding(new Insets(8, 12, 8, 12));
            previewRow.setStyle("-fx-background-color: #f7fafc; -fx-background-radius: 6;");

            Runnable refreshPreview = () -> {
                RuleState s = snapshot(lhsCoef, lhsKind, lhsFood, lhsGoal,
                                       opReader,
                                       rhsCoef, rhsKind, rhsFood, rhsGoal);
                previewLabel.setText(s.summary());
            };
            lhsCoef.valueProperty().addListener((o, a, b) -> refreshPreview.run());
            rhsCoef.valueProperty().addListener((o, a, b) -> refreshPreview.run());
            lhsKind.valueProperty().addListener((o, a, b) -> refreshPreview.run());
            rhsKind.valueProperty().addListener((o, a, b) -> refreshPreview.run());
            lhsFood.valueProperty().addListener((o, a, b) -> refreshPreview.run());
            rhsFood.valueProperty().addListener((o, a, b) -> refreshPreview.run());
            lhsGoal.valueProperty().addListener((o, a, b) -> refreshPreview.run());
            rhsGoal.valueProperty().addListener((o, a, b) -> refreshPreview.run());
            opGroup.selectedToggleProperty().addListener((o, a, b) -> refreshPreview.run());
            refreshPreview.run();

            VBox content = new VBox(14, sides, previewRow);
            content.setPadding(new Insets(16));
            content.setMinWidth(880);
            getDialogPane().setContent(content);
            getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

            setResultConverter(bt -> bt != ButtonType.OK ? null
                    : snapshot(lhsCoef, lhsKind, lhsFood, lhsGoal,
                               opReader,
                               rhsCoef, rhsKind, rhsFood, rhsGoal));
        }

        private static RuleState snapshot(Spinner<Double> lhsCoef, ComboBox<SideKind> lhsKind,
                                          ComboBox<FoodField> lhsFood, ComboBox<GoalRef> lhsGoal,
                                          java.util.function.Supplier<Op> opReader,
                                          Spinner<Double> rhsCoef, ComboBox<SideKind> rhsKind,
                                          ComboBox<FoodField> rhsFood, ComboBox<GoalRef> rhsGoal) {
            return new RuleState(
                    new Side(lhsCoef.getValue() == null ? 0.0 : lhsCoef.getValue(),
                             lhsKind.getValue(),
                             lhsFood.getValue(),
                             lhsGoal.getValue()),
                    opReader.get(),
                    new Side(rhsCoef.getValue() == null ? 0.0 : rhsCoef.getValue(),
                             rhsKind.getValue(),
                             rhsFood.getValue(),
                             rhsGoal.getValue()));
        }

        private static GridPane sideGrid(Spinner<Double> coef, ComboBox<SideKind> kind,
                                         ComboBox<FoodField> food, ComboBox<GoalRef> goal) {
            GridPane g = new GridPane();
            g.setHgap(10);
            g.setVgap(8);
            g.addRow(0, mutedLabel("Coefficient ×"), coef);
            g.addRow(1, mutedLabel("Source"),        kind);
            g.addRow(2, mutedLabel("Food field"),    food);
            g.addRow(3, mutedLabel("Goal"),          goal);
            return g;
        }

        private static Label mutedLabel(String text) {
            Label l = new Label(text);
            l.getStyleClass().add("muted");
            l.setMinWidth(110);
            return l;
        }

        private static Spinner<Double> wideSpinner(double initial) {
            Spinner<Double> s = new Spinner<>(-100_000.0, 100_000.0, initial, 0.5);
            s.setEditable(true);
            s.setPrefWidth(140);
            return s;
        }

        private static <T> ComboBox<T> wideCombo(T[] options, T initial, double width) {
            ComboBox<T> c = new ComboBox<>(FXCollections.observableArrayList(options));
            c.setValue(initial);
            c.setPrefWidth(width);
            return c;
        }

        private static void rebuildFoodCombo(ComboBox<FoodField> combo, FoodField forbidden) {
            FoodField keep = combo.getValue();
            List<FoodField> allowed = new ArrayList<>();
            for (FoodField f : FoodField.values()) if (f != forbidden) allowed.add(f);
            if (!allowed.contains(keep)) keep = allowed.isEmpty() ? null : allowed.get(0);
            combo.getItems().setAll(allowed);
            combo.setValue(keep);
        }
    }

    // ---------------- TREE WIDGETS ----------------

    /** A rule line: summary text + Edit + Delete buttons. */
    private final class RuleRow extends HBox implements RuleNode {
        final RuleState state;
        final Label summary = new Label();

        RuleRow(RuleState state, Runnable onDelete) {
            this.state = state;
            summary.getStyleClass().add("rule-summary");
            summary.setMaxWidth(Double.MAX_VALUE);
            refreshSummary();

            Button edit = new Button("Edit");
            edit.setOnAction(e -> {
                RuleEditDialog dlg = new RuleEditDialog(copyOf(state));
                Optional<RuleState> result = dlg.showAndWait();
                result.ifPresent(updated -> {
                    state.lhs = updated.lhs;
                    state.op  = updated.op;
                    state.rhs = updated.rhs;
                    refreshSummary();
                });
            });

            Button del = new Button("✖");
            del.getStyleClass().add("danger-button");
            del.setOnAction(e -> onDelete.run());

            HBox.setHgrow(summary, Priority.ALWAYS);
            setSpacing(8);
            setAlignment(Pos.CENTER_LEFT);
            setPadding(new Insets(4, 8, 4, 8));
            getStyleClass().add("rule-row");
            getChildren().addAll(summary, edit, del);
        }

        private void refreshSummary() {
            summary.setText(state.summary());
        }

        @Override
        public boolean evaluate(Ingredient food, Objective obj) {
            return state.evaluate(food, obj);
        }

        private static RuleState copyOf(RuleState s) {
            return new RuleState(s.lhs.copy(), s.op, s.rhs.copy());
        }
    }

    /** A group node: connector + ordered children (each child is itself a RuleNode). */
    private final class GroupBox extends VBox implements RuleNode {
        final ComboBox<BoolOp> connector = new ComboBox<>(FXCollections.observableArrayList(BoolOp.values()));
        final VBox childrenBox = new VBox(4);
        final int depth;

        GroupBox(int depth, Runnable onDelete) {
            this.depth = depth;
            connector.setValue(BoolOp.AND);
            connector.setPrefWidth(200);

            Button addRule = new Button("+ Rule");
            addRule.setOnAction(e -> openAddRuleDialog());

            Button addGroup = new Button("+ Group");
            addGroup.setDisable(depth >= MAX_NESTING);
            addGroup.setOnAction(e -> childrenBox.getChildren().add(newSubGroup()));

            HBox header = new HBox(8, new Label("Combine:"), connector, addRule, addGroup);
            header.setAlignment(Pos.CENTER_LEFT);

            if (onDelete != null) {
                Region spacer = new Region();
                HBox.setHgrow(spacer, Priority.ALWAYS);
                Button delGroup = new Button("Delete group");
                delGroup.getStyleClass().add("danger-button");
                delGroup.setOnAction(e -> onDelete.run());
                header.getChildren().addAll(spacer, delGroup);
            }

            setSpacing(8);
            setPadding(new Insets(10));
            getStyleClass().add("rule-group");
            getChildren().addAll(header, childrenBox);
        }

        /** Pops the editor with default values; only adds a RuleRow if the user clicks OK. */
        private void openAddRuleDialog() {
            RuleEditDialog dlg = new RuleEditDialog(RuleState.defaults());
            dlg.showAndWait().ifPresent(saved -> addRuleRow(saved));
        }

        /** Append a RuleRow with the X-button wired to remove only that row. */
        void addRuleRow(RuleState state) {
            RuleRow[] rowHolder = new RuleRow[1];
            rowHolder[0] = new RuleRow(state, () -> childrenBox.getChildren().remove(rowHolder[0]));
            childrenBox.getChildren().add(rowHolder[0]);
        }

        /** Sub-group that knows how to remove itself from its parent. */
        GroupBox newSubGroup() {
            final GroupBox[] holder = new GroupBox[1];
            holder[0] = new GroupBox(depth + 1, () -> childrenBox.getChildren().remove(holder[0]));
            return holder[0];
        }

        @Override
        public boolean evaluate(Ingredient food, Objective obj) {
            BoolOp c = connector.getValue();
            boolean anyChild = false;
            for (Node n : childrenBox.getChildren()) {
                if (!(n instanceof RuleNode r)) continue;
                anyChild = true;
                boolean v = r.evaluate(food, obj);
                if (c == BoolOp.AND && !v) return false;
                if (c == BoolOp.OR && v) return true;
            }
            // Empty group is treated as "no constraint": vacuously TRUE for AND, FALSE for OR.
            if (!anyChild) return c == BoolOp.AND;
            return c == BoolOp.AND; // (all children passed if we got here under AND)
        }
    }

    // ---------------- VIEW STATE ----------------

    private final GroupBox rootGroup = new GroupBox(0, null);
    private final TableView<Ingredient> results = new TableView<>();
    private final Label statusLabel = new Label("Loading foods...");
    private List<Ingredient> lastFetched;
    private Objective currentObjective;

    public AdvancedSearchView(ApiClient api) {
        // --- LEFT: rule tree ---
        Label title = new Label("Rules");
        title.getStyleClass().add("section-title");

        Label hint = new Label(
                "Click + Rule and a dialog opens with comfortable controls for "
                        + "the comparison. Each rule is shown as a one-line summary you can "
                        + "click Edit on later. Use + Group to nest with its own AND/OR.");
        hint.setWrapText(true);
        hint.getStyleClass().add("muted");

        Button apply = new Button("Apply");
        apply.getStyleClass().add("primary-button");
        apply.setOnAction(e -> runSearch());

        Button clear = new Button("Clear");
        clear.setOnAction(e -> {
            rootGroup.childrenBox.getChildren().clear();
            rootGroup.connector.setValue(BoolOp.AND);
            results.getItems().clear();
            statusLabel.setText("Cleared. Click + Rule to add one.");
        });

        HBox actions = new HBox(8, apply, clear);
        actions.setAlignment(Pos.CENTER_LEFT);

        ScrollPane rulesScroll = new ScrollPane(rootGroup);
        rulesScroll.setFitToWidth(true);
        rulesScroll.getStyleClass().add("scroll-pane");
        VBox.setVgrow(rulesScroll, Priority.ALWAYS);

        VBox leftPane = new VBox(10, title, hint, rulesScroll, actions, statusLabel);
        leftPane.setPadding(new Insets(16));
        leftPane.getStyleClass().add("card");
        leftPane.setPrefWidth(560);
        leftPane.setMaxHeight(Double.MAX_VALUE);

        // --- RIGHT: results table ---
        results.getColumns().add(col("Name",  240, c -> c.name()));
        results.getColumns().add(col("Brand", 140, c -> c.brand() == null ? "" : c.brand()));
        results.getColumns().add(col("kcal",   70, c -> String.valueOf(c.kcalPer100g())));
        results.getColumns().add(col("P",      60, c -> AiView.fmt(c.proteinPer100g())));
        results.getColumns().add(col("C",      60, c -> AiView.fmt(c.carbsPer100g())));
        results.getColumns().add(col("F",      60, c -> AiView.fmt(c.fatPer100g())));
        results.getColumns().add(col("Fib",    60, c -> AiView.fmt(c.fiberPer100g())));
        results.setPlaceholder(new Label("Apply at least one rule to see results."));
        VBox.setVgrow(results, Priority.ALWAYS);

        Label resultsTitle = new Label("Matching foods (per 100 g)");
        resultsTitle.getStyleClass().add("section-title");
        VBox rightPane = new VBox(10, resultsTitle, results);
        rightPane.setPadding(new Insets(16));
        rightPane.getStyleClass().add("card");

        HBox center = new HBox(14, leftPane, rightPane);
        HBox.setHgrow(rightPane, Priority.ALWAYS);
        center.setPadding(new Insets(16));
        setCenter(center);

        // Pre-seed the root with one obvious rule so the panel isn't empty.
        rootGroup.addRuleRow(RuleState.defaults());

        // Async-load the user's objective + the food pool so Apply feels instant.
        Async.run(api::getObjective, o -> currentObjective = o);
        Async.run(() -> api.searchAllIngredients("", 0, FETCH_LIMIT), list -> {
            lastFetched = list;
            statusLabel.setText(list.size() + " foods loaded. Build rules and click Apply.");
        });
    }

    private TableColumn<Ingredient, String> col(String name, double width, Function<Ingredient, String> extract) {
        TableColumn<Ingredient, String> c = new TableColumn<>(name);
        c.setCellValueFactory(cd -> new SimpleStringProperty(extract.apply(cd.getValue())));
        c.setMinWidth(Math.min(width, 50));
        c.setPrefWidth(width);
        return c;
    }

    private void runSearch() {
        if (lastFetched == null) {
            statusLabel.setText("Foods still loading - try again in a second.");
            return;
        }
        if (rootGroup.childrenBox.getChildren().isEmpty()) {
            statusLabel.setText("Add at least one rule first.");
            return;
        }
        List<Ingredient> hits = new ArrayList<>();
        for (Ingredient food : lastFetched) {
            if (rootGroup.evaluate(food, currentObjective)) hits.add(food);
        }
        results.getItems().setAll(hits);
        statusLabel.setText(hits.size() + " of " + lastFetched.size() + " foods match the rules.");
    }
}
