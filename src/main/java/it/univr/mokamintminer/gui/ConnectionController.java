package it.univr.mokamintminer.gui;

import io.mokamint.miner.api.MiningSpecification;
import io.mokamint.miner.service.MinerServices;
import it.univr.mokamintminer.services.MinerInstance;
import it.univr.mokamintminer.utils.MinerPrefsManager;
import javafx.animation.PauseTransition;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.stage.Stage;
import javafx.util.Duration;

import java.io.IOException;
import java.net.URI;
import java.util.UUID;

public class ConnectionController {
    @FXML private ComboBox<String> uriComboBox;
    @FXML private TextField minerNameField;
    @FXML private Label errorLabel;

    @FXML
    public void initialize() {
        // Carica gli URI visitati nella ComboBox dalle preferenze locali
        uriComboBox.getItems().addAll(MinerPrefsManager.getVisitedUris());
        if (!uriComboBox.getItems().isEmpty()) {
            uriComboBox.getSelectionModel().selectFirst();
        }
    }

    @FXML
    private void handleRemoveUri() {
        String selectedUri = uriComboBox.getSelectionModel().getSelectedItem();
        if (selectedUri != null && !selectedUri.isEmpty()) {
            MinerPrefsManager.removeUri(selectedUri);
            uriComboBox.getItems().remove(selectedUri);
            uriComboBox.getSelectionModel().clearSelection();
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
        String uri = uriComboBox.getEditor().getText().trim();
        String minerName = minerNameField.getText().trim();

        if (uri.isEmpty() || minerName.isEmpty()) {
            showErrorMessage("Errore: Inserisci sia l'URI del nodo che il nome del Miner!");
            return;
        }

        showStatusMessage("Connessione a " + uri + " in corso...");

        // Task in background per non congelare l'interfaccia utente durante la chiamata di rete
        Task<MiningSpecification> connectionTask = new Task<>() {
            @Override
            protected MiningSpecification call() throws Exception {
                URI serverUri = new URI(uri);
                try (var service = MinerServices.of(serverUri, 10000)) {
                    return service.getMiningSpecification();
                }
            }
        };

        // Se la connessione al nodo ha successo e risponde correttamente
        connectionTask.setOnSucceeded(event -> {
            MiningSpecification specification = connectionTask.getValue();

            MinerPrefsManager.saveUri(uri); // Memorizzo l'URI tra quelli visitati

            // Genero l'UUID univoco
            String generatedUuid = UUID.randomUUID().toString();

            // Creo l'oggetto temporaneo
            MinerInstance temporaryMiner = new MinerInstance(generatedUuid, minerName, uri);

            // Estraggo i dati inviati dalla blockchain per l'XML
            temporaryMiner.setMiningSpecification(specification);
            temporaryMiner.setChainId(specification.getChainId());
            temporaryMiner.setHashingForDeadlines(specification.getHashingForDeadlines().toString());
            temporaryMiner.setSignatureForBlocks(specification.getSignatureForBlocks().toString());
            temporaryMiner.setSignatureForDeadlines(specification.getSignatureForDeadlines().toString());

            // La chiave pubblica per la firma dei BLOCCHI appartiene al NODO (non al miner):
            // va salvata dalla spec e inserita nel prolog del plot, altrimenti il nodo
            // rifiuta le deadline con "Wrong node key in deadline".
            try {
                var sigBlocks = specification.getSignatureForBlocks();
                temporaryMiner.setPublicKeyBlocksBase58(
                        io.hotmoka.crypto.Base58.toBase58String(
                                sigBlocks.encodingOf(specification.getPublicKeyForSigningBlocks())));
            } catch (java.security.InvalidKeyException ex) {
                showErrorMessage("Chiave del nodo non valida: " + ex.getMessage());
                return;
            }

            switchToLoginScene(temporaryMiner);
        });

        // Se la connessione fallisce (es. IP errato o server Mokamint offline)
        connectionTask.setOnFailed(event -> {
            Throwable exception = connectionTask.getException();
            // Stampa completa per diagnosi (causa radice: nodo offline vs problema client)
            System.err.println("[CONNECTION] Connessione a " + uri + " fallita:");
            if (exception != null) {
                exception.printStackTrace();
                Throwable root = exception;
                while (root.getCause() != null) root = root.getCause();
                showErrorMessage("Connessione fallita: " + root.getClass().getSimpleName()
                        + " - " + root.getMessage());
            } else {
                showErrorMessage("Connessione fallita (causa sconosciuta).");
            }
        });

        new Thread(connectionTask).start();
    }

    private void switchToLoginScene(MinerInstance temporaryMiner) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/layout/login.fxml"));
            Parent root = loader.load();

            LoginController loginController = loader.getController();

            loginController.setTemporaryMiner(temporaryMiner);

            Stage stage = (Stage) uriComboBox.getScene().getWindow();
            stage.setScene(new Scene(root, 640, 540));
            stage.setTitle("Mokamint Multi-Miner - Configurazione Identità");
            stage.centerOnScreen();
            stage.show();

        } catch (IOException e) {
            System.err.println("Errore nel caricamento del LoginController!");
            e.printStackTrace();
        }
    }
}