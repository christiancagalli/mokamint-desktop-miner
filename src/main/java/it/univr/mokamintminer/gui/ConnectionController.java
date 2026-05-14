package it.univr.mokamintminer.gui;

import it.univr.mokamintminer.utils.MinerPrefsManager;
import javafx.animation.PauseTransition;
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

public class ConnectionController {
    @FXML private ComboBox<String> uriComboBox;
    @FXML private TextField plotPathField;
    //@FXML private TextField plotSizeField;                                                         //PLOTSIZE
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
        //String size = plotSizeField.getText();                                                //PLOTSIZE

        //Creiamo un Task per non bloccare la GUI
        Task<String> discoveryTask = new Task<>() {
            @Override
            protected String call() throws Exception {
                // Usiamo il client di Mokamint per interpellare il nodo
                try (var node = io.mokamint.node.remote.RemotePublicNodes.of(URI.create(uri.replace("ws", "http")), 5000)) {
                    return node.getConfig().getChainId(); // Se il nodo espone le API
                }
            }
        };

        if (uri.isEmpty() || path.isEmpty() /*|| size.isEmpty()*/) {                            //PLOTSIZE
            showErrorMessage("Errore: Campi vuoti!");
            return;
        }

        // Salva l'URI nella memoria locale
        MinerPrefsManager.saveUri(uri);

        System.out.println("Connessione a: " + uri);

        // QUI chiamerai la funzione del prof per interrogare il nodo sull'algoritmo
        // E poi passerai alla LoginPage
        switchToLoginScene(uri, path/*, size*/);                                                //PLOTSIZE
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

    private void switchToLoginScene(String uri, String path/*, String size*/) {                                         //PLOTSIZE
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/layout/login.fxml"));
            Parent root = loader.load();

            // 1. Otteniamo il controller della pagina di login
            LoginController loginController = loader.getController();

            // 2. Passiamo 'uri del miner (dovrai creare questo metodo nel LoginController)
            loginController.setConnectionData(uri, path/*, size*/);                                                      //PLOTSIZE

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