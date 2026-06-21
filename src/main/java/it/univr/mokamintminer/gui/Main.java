//prova github
package it.univr.mokamintminer.gui;

import it.univr.mokamintminer.services.MinerManager;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.stage.Stage;

public class Main extends Application {

    public void start(Stage stage) throws Exception {
        FXMLLoader fxmlLoader = new FXMLLoader(getClass().getResource("/layout/manager.fxml"));
        Scene scene = new Scene(fxmlLoader.load(), 900, 600);
        stage.setTitle("Mokamint Desktop Miner");
        stage.setScene(scene);
        stage.setMinWidth(760);
        stage.setMinHeight(500);
        stage.centerOnScreen();

        // Chiudere la dashboard = chiudere l'intera app: stop() ferma tutti i miner e
        // termina la JVM, chiudendo anche eventuali console ancora aperte.
        stage.setOnCloseRequest(e -> stop());

        stage.show();
    }

    /**
     * Invocato da JavaFX alla chiusura dell'ultima finestra: fermiamo tutti i miner
     * attivi e forziamo l'uscita, altrimenti i thread di rete (non-daemon) tengono
     * viva la JVM e il mining continuerebbe in background.
     */
    @Override
    public void stop() {
        System.out.println("[APP] Chiusura: arresto di tutti i miner attivi...");
        MinerManager.getInstance().stopAllMiners();
        Platform.exit();
        System.exit(0);
    }

    public static void main(String[] args) {
        launch(args);
    }

}
