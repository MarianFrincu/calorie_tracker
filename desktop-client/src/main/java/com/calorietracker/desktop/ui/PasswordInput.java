package com.calorietracker.desktop.ui;

import javafx.beans.property.StringProperty;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;

/**
 * A password box with a Show/Hide toggle: a masked field and a plain one
 * sharing the same text, only one visible at a time. Same behaviour as the
 * web client's PasswordField.
 */
public final class PasswordInput extends HBox {

    private final PasswordField masked = new PasswordField();
    private final TextField plain = new TextField();
    private final ToggleButton toggle = new ToggleButton("Show");

    public PasswordInput(String prompt) {
        super(6);
        plain.textProperty().bindBidirectional(masked.textProperty());
        for (TextField f : new TextField[] {masked, plain}) {
            f.setPromptText(prompt);
            f.getStyleClass().add("auth-field");
        }
        plain.setVisible(false);
        plain.setManaged(false);

        toggle.getStyleClass().add("password-toggle");
        toggle.setFocusTraversable(false);
        toggle.selectedProperty().addListener((o, was, show) -> {
            TextField from = show ? masked : plain;
            TextField to = show ? plain : masked;
            to.setVisible(true);
            to.setManaged(true);
            from.setVisible(false);
            from.setManaged(false);
            toggle.setText(show ? "Hide" : "Show");
            if (from.isFocused()) {
                to.requestFocus();
                to.positionCaret(to.getText().length());
            }
        });

        StackPane fields = new StackPane(masked, plain);
        HBox.setHgrow(fields, Priority.ALWAYS);
        getChildren().addAll(fields, toggle);
        getStyleClass().add("password-input");
    }

    public StringProperty textProperty() {
        return masked.textProperty();
    }

    public String getText() {
        return masked.getText();
    }

    public void clear() {
        masked.clear();
    }

    @Override
    public void requestFocus() {
        (plain.isVisible() ? plain : masked).requestFocus();
    }
}
