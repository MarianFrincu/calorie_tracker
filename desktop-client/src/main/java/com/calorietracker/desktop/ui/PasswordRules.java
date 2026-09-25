package com.calorietracker.desktop.ui;

import java.util.List;
import java.util.function.Predicate;
import javafx.beans.value.ObservableStringValue;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;

/**
 * The user pool's password policy (infra/cloudformation/03-cognito.yaml) as a
 * live checklist under new-password fields: each rule turns green with a tick
 * once met. Same rules and wording as the web client (auth/password.ts).
 */
public final class PasswordRules extends VBox {

    public record Rule(String label, Predicate<String> met) {}

    public static final List<Rule> RULES = List.of(
            new Rule("At least 12 characters", p -> p.length() >= 12),
            new Rule("An uppercase letter (A-Z)", p -> p.chars().anyMatch(c -> c >= 'A' && c <= 'Z')),
            new Rule("A lowercase letter (a-z)", p -> p.chars().anyMatch(c -> c >= 'a' && c <= 'z')),
            new Rule("A number (0-9)", p -> p.chars().anyMatch(c -> c >= '0' && c <= '9')));

    public static boolean meetsAll(String password) {
        return RULES.stream().allMatch(r -> r.met().test(password));
    }

    public PasswordRules(ObservableStringValue password, ObservableStringValue confirm) {
        super(3);
        getStyleClass().add("password-rules");
        Label[] labels = new Label[RULES.size()];
        for (int i = 0; i < labels.length; i++) {
            labels[i] = new Label();
            getChildren().add(labels[i]);
        }
        Label match = new Label();
        getChildren().add(match);

        Runnable refresh = () -> {
            String p = password.get() == null ? "" : password.get();
            for (int i = 0; i < labels.length; i++) {
                mark(labels[i], RULES.get(i).label(), RULES.get(i).met().test(p));
            }
            String c = confirm.get() == null ? "" : confirm.get();
            match.setVisible(!c.isEmpty());
            match.setManaged(!c.isEmpty());
            mark(match, "Both passwords match", p.equals(c));
        };
        password.addListener((o, a, b) -> refresh.run());
        confirm.addListener((o, a, b) -> refresh.run());
        refresh.run();
    }

    private static void mark(Label label, String text, boolean met) {
        label.setText((met ? "✓  " : "○  ") + text);
        label.getStyleClass().removeAll("met");
        if (met) label.getStyleClass().add("met");
    }
}
