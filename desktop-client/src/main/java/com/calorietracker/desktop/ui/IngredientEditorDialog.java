package com.calorietracker.desktop.ui;

import com.calorietracker.desktop.api.ApiClient;
import com.calorietracker.desktop.model.CreateIngredientRequest;
import com.calorietracker.desktop.model.Ingredient;
import javafx.event.ActionEvent;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;

/**
 * Modal "New food" dialog: full nutrition form. Every field starts empty;
 * the user must enter every macro explicitly (typing {@code 0} is valid,
 * leaving blank is not). On OK we cross-check that the calorie value is
 * within +/- 10 kcal of the value implied by the macros
 * ({@code 4*protein + 4*carbs + 9*fat}) so blatantly inconsistent rows
 * can't slip into the library.
 * <p>
 * Validation errors render inline at the bottom of the dialog and the
 * dialog stays open until the data is valid.
 */
public class IngredientEditorDialog extends Dialog<Ingredient> {

    /** Per-100-g calorie energies. */
    private static final double KCAL_PER_G_PROTEIN = 4.0;
    private static final double KCAL_PER_G_CARBS   = 4.0;
    private static final double KCAL_PER_G_FAT     = 9.0;
    /** How far the entered kcal can differ from the implied kcal before we reject. */
    private static final double KCAL_TOLERANCE     = 10.0;

    private final TextField nameField    = new TextField();
    private final TextField brandField   = new TextField();
    private final TextField kcalField    = numericField();
    private final TextField proteinField = numericField();
    private final TextField carbsField   = numericField();
    private final TextField fatField     = numericField();
    private final TextField fiberField   = numericField();

    public IngredientEditorDialog(ApiClient api) {
        setTitle("New food");
        setHeaderText("All values are per 100 g of the food (check the label).");
        com.calorietracker.desktop.AppContext.prepareDialog(this);

        nameField.setPromptText("e.g. Greek yogurt, full fat");
        brandField.setPromptText("optional");
        kcalField.setPromptText("required");
        proteinField.setPromptText("required");
        carbsField.setPromptText("required");
        fatField.setPromptText("required");
        fiberField.setPromptText("required");

        GridPane form = new GridPane();
        form.setHgap(12);
        form.setVgap(10);
        form.setPadding(new Insets(10));
        form.addRow(0, muted("Name"),           nameField);
        form.addRow(1, muted("Brand"),          brandField);
        form.addRow(2, muted("kcal per 100 g"), kcalField);
        form.addRow(3, muted("Protein (g)"),    proteinField);
        form.addRow(4, muted("Carbs (g)"),      carbsField);
        form.addRow(5, muted("Fat (g)"),        fatField);
        form.addRow(6, muted("Fiber (g)"),      fiberField);

        VBox content = new VBox(8, form);
        content.setPrefSize(480, 360);
        getDialogPane().setContent(content);
        getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        // Validation errors surface as a popup (an alert) and consume the OK
        // event so the dialog stays open with the user's typed values intact.
        Button okBtn = (Button) getDialogPane().lookupButton(ButtonType.OK);
        okBtn.addEventFilter(ActionEvent.ACTION, ev -> {
            String err = validate();
            if (err != null) {
                Async.showWarning(err);
                ev.consume();
            }
        });

        setResultConverter(bt -> {
            if (bt != ButtonType.OK) return null;
            try {
                CreateIngredientRequest req = new CreateIngredientRequest(
                        nameField.getText().trim(),
                        brandField.getText().isBlank() ? null : brandField.getText().trim(),
                        parseInt(kcalField.getText()),
                        parseDouble(proteinField.getText()),
                        parseDouble(carbsField.getText()),
                        parseDouble(fatField.getText()),
                        parseDouble(fiberField.getText()));
                return api.createIngredient(req);
            } catch (Exception ex) {
                Async.showError(ex);
                return null;
            }
        });
    }

    /** @return the user-friendly error string, or {@code null} when the form is valid. */
    private String validate() {
        if (nameField.getText().isBlank()) return "Name is required.";
        if (kcalField.getText().isBlank())    return "kcal is required (type 0 if you mean zero).";
        if (proteinField.getText().isBlank()) return "Protein is required (type 0 if you mean zero).";
        if (carbsField.getText().isBlank())   return "Carbs is required (type 0 if you mean zero).";
        if (fatField.getText().isBlank())     return "Fat is required (type 0 if you mean zero).";
        if (fiberField.getText().isBlank())   return "Fiber is required (type 0 if you mean zero).";

        Integer kcal = parseIntOrNull(kcalField.getText());
        Double  p    = parseDoubleOrNull(proteinField.getText());
        Double  c    = parseDoubleOrNull(carbsField.getText());
        Double  f    = parseDoubleOrNull(fatField.getText());
        Double  fib  = parseDoubleOrNull(fiberField.getText());
        if (kcal == null || p == null || c == null || f == null || fib == null) {
            return "One of the numeric fields isn't a number. Use a dot for decimals.";
        }
        if (kcal < 0 || p < 0 || c < 0 || f < 0 || fib < 0) {
            return "Values can't be negative.";
        }
        if (p > 100 || c > 100 || f > 100 || fib > 100) {
            return "Macros per 100 g can't exceed 100.";
        }
        if (p + c + f > 100.5) {
            return "Protein + carbs + fat per 100 g can't exceed 100 g.";
        }

        double expected = KCAL_PER_G_PROTEIN * p + KCAL_PER_G_CARBS * c + KCAL_PER_G_FAT * f;
        // The user's spec: kcal - 10 <= 4*p + 4*c + 9*f <= kcal + 10.
        // Equivalent to |expected - kcal| <= 10, with the formula direction the
        // user described kept explicit so the comparison is unambiguous.
        if (expected < kcal - KCAL_TOLERANCE || expected > kcal + KCAL_TOLERANCE) {
            return String.format("kcal doesn't match the macros. Macros give ~%.0f kcal "
                    + "but you entered %d. Adjust either to within %.0f kcal.",
                    expected, kcal, KCAL_TOLERANCE);
        }
        return null;
    }

    /** A TextField that silently rejects non-numeric input as the user types. */
    private static TextField numericField() {
        TextField tf = new TextField();
        tf.setTextFormatter(new javafx.scene.control.TextFormatter<>(change -> {
            String next = change.getControlNewText();
            if (next.isEmpty()) return change;
            return next.matches("\\d*(\\.\\d*)?") ? change : null;
        }));
        return tf;
    }

    private static int parseInt(String s) {
        return (int) Math.round(Double.parseDouble(s.trim()));
    }
    private static double parseDouble(String s) { return Double.parseDouble(s.trim()); }
    private static Integer parseIntOrNull(String s) {
        try { return parseInt(s); } catch (Exception e) { return null; }
    }
    private static Double parseDoubleOrNull(String s) {
        try { return parseDouble(s); } catch (Exception e) { return null; }
    }

    private static Label muted(String text) {
        Label l = new Label(text);
        l.getStyleClass().add("muted");
        return l;
    }
}
