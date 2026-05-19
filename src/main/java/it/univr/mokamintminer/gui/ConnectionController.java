package it.univr.mokamintminer.gui;

import io.mokamint.miner.api.MiningSpecification;
import io.mokamint.miner.service.MinerServices;
import it.univr.mokamintminer.utils.MinerPrefsManager;
import javafx.animation.PauseTransition;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import javafx.util.Duration;

import java.io.File;
import java.io.IOException;
import java.net.URI;

public class ConnectionController {
    @FXML private ComboBox<String> uriComboBox;
    @FXML private TextField plotPathField;
    @FXML private Label errorLabel;

    @FXML
    public void initialize() {
        // Carica gli URI visitati nella ComboBox
        uriComboBox.getItems().addAll(MinerPrefsManager.getVisitedUris());
        if (!uriComboBox.getItems().isEmpty()) {
            uriComboBox.getSelectionModel().selectFirst();
        }
    }

    @FXML
    private void handleRemoveUri() {
        String selectedUri = uriComboBox.getSelectionModel().getSelectedItem();
        if (selectedUri != null && !selectedUri.isEmpty()) {
            MinerPrefsManager.removeUri(selectedUri);   // 1. Rimuovi dalle preferenze
            uriComboBox.getItems().remove(selectedUri); // 2. Rimuovi dalla ComboBox graficamente
            uriComboBox.getSelectionModel().clearSelection();
        }
    }

    @FXML
    private void handleBrowsePlot() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setInitialFileName("plotfile.plot");
        fileChooser.setTitle("Seleziona o crea il File di Plot");
        fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Plot Files", "*.plot"));
        fileChooser.setInitialDirectory(new File(System.getProperty("user.home")));
        File selectedFile = fileChooser.showSaveDialog(uriComboBox.getScene().getWindow());
        if (selectedFile != null) {
            plotPathField.setText(selectedFile.getAbsolutePath());
        }
    }

    private void showStatusMessage(String message) {
        errorLabel.setText(message);
        errorLabel.setStyle("-fx-text-fill: #ffb627; -fx-font-weight: bold; -fx-font-size: 13px;");
        errorLabel.setVisible(true);
    }

    private void showErrorMessage(String message) {
        errorLabel.setText(message);
        errorLabel.setStyle("-fx-text-fill: #ff4444; -fx-font-weight: bold; -fx-font-size: 13px;");
        errorLabel.setVisible(true);

        PauseTransition delay = new PauseTransition(Duration.seconds(5));
        delay.setOnFinished(e -> errorLabel.setVisible(false));
        delay.play();
    }

    @FXML
    private void handleConnect() {
        String uri = uriComboBox.getEditor().getText(); // Prende sia scelta che testo libero
        String path = plotPathField.getText();

        if (uri.isEmpty() || path.isEmpty()) {
            showErrorMessage("Errore: Campi vuoti!");
            return;
        }

        showStatusMessage("Connessione a " + uri + "in corso ...");

        // Creo un Task in background per non bloccare la GUI durante la connessione di rete
        Task<MiningSpecification> connectionTask = new Task<>() {
            @Override
            protected MiningSpecification call() throws Exception {
                URI serverUri = new URI(uri);
                // Apro il servizio
                try (var service = MinerServices.of(serverUri, 10000)) {
                    // Chiedo le informazioni al server e le restituisco
                    return service.getMiningSpecification();
                }
            }
        };

        // se la connessione ha successo
        connectionTask.setOnSucceeded(event -> {
            MiningSpecification specification = connectionTask.getValue();
            System.out.println("Specifiche ricevute con successo dal server!");

            MinerPrefsManager.saveUri(uri); // Salva l'URI solo se valido e connesso
            switchToLoginScene(uri, path, specification);
        });

        // se la connessione fallisce (es: server spento, URI errato)
        connectionTask.setOnFailed(event -> {
            Throwable exception = connectionTask.getException();
            showErrorMessage("Connessione fallita: " + exception.getMessage());
            System.err.println("Errore connessione: " + exception.getMessage());
        });
        // Avvia il thread in background
        new Thread(connectionTask).start();
    }

    private void switchToLoginScene(String uri, String path, MiningSpecification specification) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/layout/login.fxml"));
            Parent root = loader.load();

            // Ottengo il controller della pagina di login
            LoginController loginController = loader.getController();

            // Passo l'uri del miner
            loginController.setConnectionData(uri, path, specification);

            // Cambio effettivo della scena sul desktop
            Stage stage = (Stage) uriComboBox.getScene().getWindow();
            stage.setScene(new Scene(root));
            stage.setTitle("Mokamint - Accesso");
            stage.centerOnScreen();
            stage.show();

        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}