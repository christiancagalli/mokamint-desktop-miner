package it.univr.mokamintminer.utils;

import javafx.scene.control.Dialog;

/**
 * Piccola utility per applicare il tema scuro dell'app (style.css) ai dialoghi
 * creati da codice (Alert, TextInputDialog...), che altrimenti userebbero lo
 * stile bianco di default di JavaFX.
 */
public final class DialogUtils {

    private DialogUtils() {}

    /** Aggancia il foglio di stile dell'app al dialogo e ne applica la classe scura. */
    public static void applyDarkStyle(Dialog<?> dialog) {
        try {
            var pane = dialog.getDialogPane();
            pane.getStylesheets().add(DialogUtils.class.getResource("/layout/style.css").toExternalForm());
            pane.getStyleClass().add("dialog-pane");
        } catch (Exception ignored) {
            // Se per qualche motivo lo stile non si carica, lascio il dialogo di default.
        }
    }
}
