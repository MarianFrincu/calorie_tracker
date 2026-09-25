package com.calorietracker.desktop.ui;

import javafx.scene.control.Spinner;
import javafx.scene.control.SpinnerValueFactory;
import javafx.scene.control.TextFormatter;
import javafx.util.StringConverter;

/**
 * Makes an editable number spinner's text box behave like the web client's
 * NumberInput: only digits (and one decimal point) can be typed, a comma
 * counts as the decimal point ("75,5"), leading zeros are dropped as you type
 * (no "067"), and an empty or half-typed box keeps the last valid value
 * instead of breaking the spinner.
 */
public final class NumericSpinners {

    private NumericSpinners() {}

    public static <T extends Number> void tidy(Spinner<T> spinner, boolean integer) {
        SpinnerValueFactory<T> factory = spinner.getValueFactory();
        StringConverter<T> base = factory.getConverter();
        factory.setConverter(new StringConverter<>() {
            @Override public String toString(T value) {
                return base.toString(value);
            }

            @Override public T fromString(String text) {
                String t = text == null ? "" : text.trim().replace(',', '.');
                if (t.isEmpty() || t.equals(".")) return factory.getValue(); // cleared: keep the last value
                try {
                    return base.fromString(t);
                } catch (RuntimeException e) {
                    return factory.getValue();
                }
            }
        });

        spinner.getEditor().setTextFormatter(new TextFormatter<>(change -> {
            if (change.getText().contains(",")) change.setText(change.getText().replace(',', '.'));
            String next = change.getControlNewText();
            if (!next.matches(integer ? "\\d*" : "\\d*\\.?\\d*")) return null; // ignore the keystroke
            String stripped = next.replaceFirst("^0+(?=\\d)", "");
            if (!stripped.equals(next)) {
                change.setRange(0, change.getControlText().length());
                change.setText(stripped);
                change.setCaretPosition(stripped.length());
                change.setAnchor(stripped.length());
            }
            return change;
        }));
    }
}
