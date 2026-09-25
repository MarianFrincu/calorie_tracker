package com.calorietracker.desktop;

/**
 * Entry point for the packaged app (java -jar / jpackage).
 *
 * <p>The JavaFX launcher refuses to start when the main class itself extends
 * {@link javafx.application.Application} and JavaFX sits on the classpath
 * rather than the module path ("JavaFX runtime components are missing").
 * Delegating from a plain class sidesteps that without a module-info.
 * {@code mvn javafx:run} still starts {@link CalorieTrackerApp} directly.
 */
public final class Launcher {

    private Launcher() {}

    public static void main(String[] args) {
        CalorieTrackerApp.main(args);
    }
}
