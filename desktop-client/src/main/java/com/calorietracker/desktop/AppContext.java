package com.calorietracker.desktop;

import javafx.application.Platform;
import javafx.beans.value.ChangeListener;
import javafx.scene.control.Dialog;
import javafx.scene.control.DialogEvent;
import javafx.stage.Stage;

/**
 * Process-wide holder for the primary JavaFX {@link Stage} plus dialog-prep
 * helpers.
 *
 * <p><b>Why prepareDialog exists.</b> On most Linux window managers (and on
 * some Windows builds) opening any new top-level window causes the WM to
 * un-maximize / exit-fullscreen the existing window, even when the new one
 * has an explicit owner. JavaFX itself doesn't reapply the state because
 * from its point of view "the user resized it". So we install a listener
 * that re-applies the state every time the WM tries to take it away,
 * for as long as the dialog is open. As soon as the dialog closes we
 * remove the listener so the user can resize freely again.
 */
public final class AppContext {

    private static volatile Stage mainStage;

    private AppContext() {}

    public static void setMainStage(Stage stage) { mainStage = stage; }
    public static Stage mainStage() { return mainStage; }

    /**
     * Wire the dialog up to the main stage and defend the parent's
     * {@code maximized} / {@code fullScreen} state for as long as the
     * dialog is on screen. Safe to call repeatedly; no-ops if the main
     * stage hasn't been set yet.
     */
    public static void prepareDialog(Dialog<?> dialog) {
        Stage parent = mainStage;
        if (parent == null || dialog == null) return;
        if (dialog.getOwner() == null) dialog.initOwner(parent);
        // WINDOW_MODAL ties the dialog more tightly to the parent Window than
        // the default APPLICATION_MODAL, which on Linux WMs reduces the chance
        // the parent gets resized when the child appears.
        try { dialog.initModality(javafx.stage.Modality.WINDOW_MODAL); }
        catch (IllegalStateException ignored) { /* already shown - leave it */ }
        // StageStyle.UTILITY tells the WM "this is a tool palette" - KDE/GNOME
        // both leave the parent window state alone for utility children, even
        // when the parent is fullscreen. This is the most reliable trigger we
        // have for "don't resize my main window."
        try { dialog.initStyle(javafx.stage.StageStyle.UTILITY); }
        catch (IllegalStateException ignored) { /* already shown - leave it */ }

        final boolean wasMaximized  = parent.isMaximized();
        final boolean wasFullScreen = parent.isFullScreen();
        if (!wasMaximized && !wasFullScreen) return; // nothing to defend

        // Save the exact geometry too. Some WMs flip the size of the parent
        // without ever touching maximizedProperty, so a size-watching listener
        // is the only way to catch it.
        final double savedWidth  = parent.getWidth();
        final double savedHeight = parent.getHeight();
        // The bar below which we treat a parent resize as "the WM stole our state".
        final double shrinkThreshold = 80.0;

        // Re-applies the requested state via Platform.runLater so we never fight
        // a property update mid-listener-fire. A short delayed re-apply (via a
        // PauseTransition) covers WMs that complete their resize a few frames
        // after the property change has fired.
        final Runnable reapply = () -> {
            Platform.runLater(() -> {
                if (wasFullScreen && !parent.isFullScreen()) parent.setFullScreen(true);
                if (wasMaximized  && !parent.isMaximized())  parent.setMaximized(true);
            });
            javafx.animation.PauseTransition delayed = new javafx.animation.PauseTransition(
                    javafx.util.Duration.millis(150));
            delayed.setOnFinished(ev -> {
                if (wasFullScreen && !parent.isFullScreen()) parent.setFullScreen(true);
                if (wasMaximized  && !parent.isMaximized())  parent.setMaximized(true);
            });
            delayed.play();
        };

        final ChangeListener<Boolean> maxL = (obs, oldV, newV) -> {
            if (Boolean.FALSE.equals(newV)) reapply.run();
        };
        final ChangeListener<Boolean> fsL = (obs, oldV, newV) -> {
            if (Boolean.FALSE.equals(newV)) reapply.run();
        };
        // The third line of defence: catch the WMs that resize without flipping
        // maximizedProperty/fullScreenProperty.
        final ChangeListener<Number> sizeL = (obs, oldV, newV) -> {
            if (parent.getWidth()  < savedWidth  - shrinkThreshold
             || parent.getHeight() < savedHeight - shrinkThreshold) {
                reapply.run();
            }
        };

        if (wasMaximized)  parent.maximizedProperty().addListener(maxL);
        if (wasFullScreen) parent.fullScreenProperty().addListener(fsL);
        parent.widthProperty().addListener(sizeL);
        parent.heightProperty().addListener(sizeL);

        // Re-apply right before the dialog is shown (lets us beat the WM to
        // the punch) and again right after (covers any state lost during show).
        dialog.addEventHandler(DialogEvent.DIALOG_SHOWING, e -> reapply.run());
        dialog.addEventHandler(DialogEvent.DIALOG_SHOWN,   e -> reapply.run());

        dialog.addEventHandler(DialogEvent.DIALOG_HIDDEN, e -> {
            if (wasMaximized)  parent.maximizedProperty().removeListener(maxL);
            if (wasFullScreen) parent.fullScreenProperty().removeListener(fsL);
            parent.widthProperty().removeListener(sizeL);
            parent.heightProperty().removeListener(sizeL);
            // Final re-apply: some WMs unmaximize right as the child closes.
            reapply.run();
        });
    }
}
