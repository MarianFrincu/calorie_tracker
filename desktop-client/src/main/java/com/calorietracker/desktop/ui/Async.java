package com.calorietracker.desktop.ui;

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import javafx.concurrent.Task;
import javafx.scene.control.Alert;

/** Runs a blocking call off the JavaFX thread and delivers the result back on it. */
public final class Async {

    /** Bounded daemon pool so rapid retries (e.g. tab spam) can't spawn unbounded threads. */
    private static final ExecutorService POOL = Executors.newFixedThreadPool(8, new ThreadFactory() {
        private final AtomicInteger n = new AtomicInteger(1);
        @Override public Thread newThread(Runnable r) {
            Thread t = new Thread(r, "api-call-" + n.getAndIncrement());
            t.setDaemon(true);
            return t;
        }
    });

    private Async() {}

    public static <T> void run(Callable<T> work, Consumer<T> onSuccess) {
        Task<T> task = new Task<>() {
            @Override
            protected T call() throws Exception {
                return work.call();
            }
        };
        task.setOnSucceeded(e -> {
            try {
                onSuccess.accept(task.getValue());
            } catch (Exception ex) {
                showError(ex);
            }
        });
        task.setOnFailed(e -> showError(task.getException()));
        POOL.execute(task);
    }

    public static void showError(Throwable ex) {
        // Friendly copy — never surface raw JSON, stack traces, or class names.
        showAlert(Alert.AlertType.ERROR, Messages.friendly(ex));
    }

    /** Validation-style warning popup (used for "fill this field" prompts etc.). */
    public static void showWarning(String message) {
        showAlert(Alert.AlertType.WARNING, message == null ? "Please check the form." : message);
    }

    /** Plain informational popup. */
    public static void showInfo(String message) {
        showAlert(Alert.AlertType.INFORMATION, message == null ? "" : message);
    }

    private static void showAlert(Alert.AlertType type, String message) {
        Alert alert = new Alert(type, message);
        alert.setHeaderText(null); // strip JavaFX's noisy default header ("Error", "Warning")
        // Owner-aware so the parent stage keeps its maximized / fullscreen state.
        com.calorietracker.desktop.AppContext.prepareDialog(alert);
        alert.showAndWait();
    }
}
