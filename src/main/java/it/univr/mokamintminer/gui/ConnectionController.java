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
            // 1. Rimuovi dalle preferenze
            MinerPrefsManager.removeUri(selectedUri);
            // 2. Rimuovi dalla ComboBox graficamente
            uriComboBox.getItems().remove(selectedUri);
            uriComboBox.getSelectionModel().clearSelection();
        }
    }

    @FXML
    private void handleBrowsePlot() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setInitialFileName("plotfile.plot");
        fileChooser.setTitle("Seleziona o crea il File di Plot");
        fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Plot Files", "*.plot"));
        //fileChooser.setInitialDirectory(new File(System.getProperty("user.home")));                                                      //TODO: da scommentare prima della consegna
        String home = System.getProperty("user.home");                                                                                     //QUESTA DA ELIMINARE
        File initialDir = new File(home + File.separator + "Documenti" + File.separator + "tesi" + File.separator + "temp");      //QUESTA PURE
        fileChooser.setInitialDirectory(initialDir);                                                                                       //ANCHE QUESTA
        File selectedFile = fileChooser.showSaveDialog(uriComboBox.getScene().getWindow());
        if (selectedFile != null) {
            plotPathField.setText(selectedFile.getAbsolutePath());
        }
    }


    private void showErrorMessage(String message) {
        errorLabel.setText(message);
        errorLabel.setVisible(true);

        // Opzionale: dopo 5 secondi la scritta scompare
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

        errorLabel.setText("Connessione a " + uri + "in corso ...");
        errorLabel.setVisible(true);

        // Creiamo un Task in background per non bloccare la GUI durante la connessione di rete
        Task<MiningSpecification> connectionTask = new Task<>() {
            @Override
            protected MiningSpecification call() throws Exception {
                URI serverUri = new URI(uri);
                // Apriamo il servizio con il timeout inviato dal prof (es. 10000ms)
                try (var service = MinerServices.of(serverUri, 10000)) {
                    // Chiediamo le informazioni al server e le restituiamo
                    return service.getMiningSpecification();
                }
            }
        };

        // Cosa fare se la connessione ha successo
        connectionTask.setOnSucceeded(event -> {
            MiningSpecification specification = connectionTask.getValue();
            System.out.println("Specifiche ricevute con successo dal server!");

            // Salva l'URI solo se valido e connesso
            MinerPrefsManager.saveUri(uri);

            // Passiamo alla scena successiva includendo le specifiche scaricate
            switchToLoginScene(uri, path, specification);
        });

        // Cosa fare se la connessione fallisce (es: server spento, URI errato)
        connectionTask.setOnFailed(event -> {
            Throwable exception = connectionTask.getException();
            showErrorMessage("Connessione fallita: " + exception.getMessage());
            System.err.println("Errore connessione: " + exception.getMessage());
        });

        // Avviamo il thread in background
        new Thread(connectionTask).start();
    }

    private void switchToLoginScene(String uri, String path, MiningSpecification specification) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/layout/login.fxml"));
            Parent root = loader.load();

            // 1. Otteniamo il controller della pagina di login
            LoginController loginController = loader.getController();

            // 2. Passiamo 'uri del miner (dovrai creare questo metodo nel LoginController)
            loginController.setConnectionData(uri, path, specification);

            // 3. Cambiamo effettivamente la scena sul desktop
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