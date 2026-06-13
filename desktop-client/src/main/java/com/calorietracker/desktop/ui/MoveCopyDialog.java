package com.calorietracker.desktop.ui;

import com.calorietracker.desktop.model.Meal;
import java.time.LocalDate;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.layout.GridPane;

/**
 * Shared dialog for "Move to..." and "Copy to..." entry actions. Returns the
 * chosen (date, meal) when the user clicks OK, or null on cancel.
 */
public class MoveCopyDialog extends Dialog<MoveCopyDialog.Target> {

    public record Target(LocalDate date, Meal meal) {}

    public MoveCopyDialog(String title, LocalDate initialDate, Meal initialMeal) {
        setTitle(title);
        setHeaderText("Pick a destination day and meal slot.");
        com.calorietracker.desktop.AppContext.prepareDialog(this);

        DatePicker datePicker = new DatePicker(initialDate);
        ComboBox<Meal> mealBox = new ComboBox<>(FXCollections.observableArrayList(Meal.values()));
        mealBox.setValue(initialMeal);
        mealBox.setConverter(new javafx.util.StringConverter<>() {
            @Override public String toString(Meal m) {
                if (m == null) return "";
                String s = m.name();
                return s.charAt(0) + s.substring(1).toLowerCase();
            }
            @Override public Meal fromString(String s) { return null; }
        });

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(14));
        grid.addRow(0, new Label("Day"),  datePicker);
        grid.addRow(1, new Label("Meal"), mealBox);

        getDialogPane().setContent(grid);
        getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        setResultConverter(bt -> {
            if (bt != ButtonType.OK) return null;
            if (datePicker.getValue() == null || mealBox.getValue() == null) return null;
            return new Target(datePicker.getValue(), mealBox.getValue());
        });
    }
}
