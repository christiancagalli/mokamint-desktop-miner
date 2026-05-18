package it.univr.mokamintminer.gui;

import io.hotmoka.crypto.BIP39Mnemonics;
import io.hotmoka.crypto.api.Entropy;
import io.hotmoka.crypto.Entropies;
import io.hotmoka.crypto.api.BIP39Mnemonic;
import io.mokamint.miner.api.MiningSpecification;
import io.mokamint.miner.service.MinerServices;
import io.mokamint.node.remote.api.RemoteNode;
import it.univr.mokamintminer.services.MinerService;
import javafx.animation.FadeTransition;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import javafx.util.Duration;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.KeyPair;

public class LoginController {

    @FXML
    private VBox manualInputBox;
    @FXML private VBox loadPemBox;
    @FXML private TextField pemPathField;
    @FXML private Button loginPemButton;


    @FXML
    private TextArea mnemonicTextArea;

    private final MinerService minerService = new MinerService();
    private KeyPair loggedKeyPair;
    private MiningSpecification miningSpecification;

    @FXML
    private void initialize() {
        // Logica eseguita all'avvio della pagina
    }

    //BOTTONE PER GENERARE IDENTITÀ NEL CASO NON SI POSSEDESSE
    @FXML
    private void handleGenerateNew() {
        if (miningSpecification == null) {
            showError("Specifiche del server non caricate. Impossibile generare le chiavi.");
            return;
        }

        manualInputBox.setVisible(false);
        manualInputBox.setManaged(false);
        loadPemBox.setVisible(false);
        loadPemBox.setManaged(false);

        // Configura il salvataggio file
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Salva la tua Identità (.pem)");
        fileChooser.setInitialFileName("mokamint_identity.pem");
        fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("PEM Files", "*.pem"));

        //fileChooser.setInitialDirectory(new File(System.getProperty("user.home")));                                                      //TODO: da scommentare prima della consegna
        String home = System.getProperty("user.home");                                                                                     //QUESTA DA ELIMINARE
        File initialDir = new File(home + File.separator + "Documenti" + File.separator + "tesi" + File.separator + "temp");      //QUESTA PURE
        fileChooser.setInitialDirectory(initialDir);                                                                                       //ANCHE QUESTA

        File file = fileChooser.showSaveDialog(mnemonicTextArea.getScene().getWindow());

        if (file != null) {
            try {
                // Genera le chiavi e salva
                String newMnemonic = minerService.generateNewMnemonic();
                String[] words = newMnemonic.split("[,\\s]+");

                var b39 = io.hotmoka.crypto.BIP39Mnemonics.of(words);
                byte[] entropyBytes = b39.getBytes();
                io.hotmoka.crypto.api.Entropy entropy = io.hotmoka.crypto.Entropies.of(entropyBytes);

                var signatureForDeadlines = miningSpecification.getSignatureForDeadlines();
                this.loggedKeyPair = entropy.keys("", signatureForDeadlines);

                // Dump nativo in formato PEM
                entropy.dump(Paths.get(file.getAbsolutePath()));

                // Mostra il pop-up informativo (mostra le parole)
                Alert alert = new Alert(Alert.AlertType.INFORMATION);
                alert.setTitle("Identità Creata");
                alert.setHeaderText("Salvataggio completato!");
                alert.setContentText("Il file PEM è pronto. \n\nPAROLE DI RECUPERO (Scrivile ora!):\n" + newMnemonic);

                alert.showAndWait();    // attendo che l'utente prema ok sul pop-up

                System.out.println("Auto-login in corso... chiavi: " + loggedKeyPair.getPublic());
                switchToMiningScene(this.loggedKeyPair);

            } catch (Exception e) {
                showError("Errore durante la creazione: " + e.getMessage());
            }
        }
    }

    // BOTTONE PER IL CARICAMENTO FILE
    @FXML
    private void handleLoadPem() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Seleziona il tuo file Identità");
        fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("PEM Files", "*.pem"));

        //fileChooser.setInitialDirectory(new File(System.getProperty("user.home")));                                                      //TODO: da scommentare prima della consegna
        String home = System.getProperty("user.home");                                                                                     //QUESTA DA ELIMINARE
        File initialDir = new File(home + File.separator + "Documenti" + File.separator + "tesi" + File.separator + "temp");      //QUESTA PURE
        fileChooser.setInitialDirectory(initialDir);                                                                                       //ANCHE QUESTA

        File selectedFile = fileChooser.showOpenDialog(pemPathField.getScene().getWindow());

        if (selectedFile != null) {
            pemPathField.setText(selectedFile.getAbsolutePath());

            // MOSTRA IL TASTO ACCEDI
            loginPemButton.setVisible(true);
            loginPemButton.setManaged(true);
        }
    }

    //BOTTONE DI LOGIN DOPO AVER CARICATO IL FILE
    @FXML
    private void handleFinalPemLogin() {
        String path = pemPathField.getText();
        if (!path.isEmpty()) {
            try {
                /*this.loggedKeyPair = minerService.deriveKeyPairFromMnemonic(minerService.loadMnemonicFromFile(Path.of(path)));
                System.out.println("Login da file JSON completato! chiavi: " + loggedKeyPair.getPublic());
                switchToMiningScene(this.loggedKeyPair);
            } catch (Exception e) {
                showError("File JSON non valido.");
            }*/
                var entropy = Entropies.load(Paths.get(path));

                // Ricaviamo la coppia di chiavi usando l'algoritmo dinamico del server
                var signatureForDeadlines = miningSpecification.getSignatureForDeadlines();
                this.loggedKeyPair = entropy.keys("", signatureForDeadlines);

                System.out.println("Login da file PEM completato con successo! chiavi: " + loggedKeyPair.getPublic());
                switchToMiningScene(this.loggedKeyPair);
            } catch (Exception e) {
                e.printStackTrace();
                showError("File PEM non valido o non compatibile con le specifiche del server.");
            }
        }
    }


    //CONTROLLO DELLA MNEMONICA INSERITA E IN CASO POSITIVO SWITCH ALLA PAGINA DI MINING
    @FXML
    private void handleManualLogin() {
        String mnemonic = mnemonicTextArea.getText().trim();
        if (minerService.isValidMnemonic(mnemonic)) {
            try {
                String[] words = mnemonic.split("[,\\s]+");
                var b39 = io.hotmoka.crypto.BIP39Mnemonics.of(words);
                byte[] entropyBytes = b39.getBytes();
                io.hotmoka.crypto.api.Entropy entropy = io.hotmoka.crypto.Entropies.of(entropyBytes);

                var signatureForDeadlines = miningSpecification.getSignatureForDeadlines();
                this.loggedKeyPair = entropy.keys("", signatureForDeadlines);

                System.out.println("Login effettuato con successo! chiavi: " + loggedKeyPair.getPublic());
                switchToMiningScene(this.loggedKeyPair);
            } catch (Exception e) {
                showError("Errore nella derivazione crittografica: " + e.getMessage());
            }
        } else {
            System.out.println("Mnemonica non valida!");
        }
    }

    //RENDERE VISIBILE LA TEXTAREA
    @FXML
    private void toggleManualInput() {
        loadPemBox.setVisible(false);
        loadPemBox.setManaged(false);
        boolean isVisible = manualInputBox.isVisible();
        manualInputBox.setVisible(!isVisible);
        manualInputBox.setManaged(!isVisible);
        manualInputBox.setOpacity(0);
        FadeTransition fade = new FadeTransition(Duration.millis(500), manualInputBox);
        fade.setFromValue(0.0);
        fade.setToValue(1.0);
        fade.play();
    }

    //RENDERE VISIBILE IL FILE CHOOSER
    @FXML
    private void handleShowLoadArea() {
        manualInputBox.setVisible(false);
        manualInputBox.setManaged(false);
        boolean isVisible = loadPemBox.isVisible();
        loadPemBox.setVisible(!isVisible);
        loadPemBox.setManaged(!isVisible);
        loadPemBox.setOpacity(0); // Parte da invisibile

        FadeTransition fade = new FadeTransition(Duration.millis(500), loadPemBox);
        fade.setFromValue(0.0);
        fade.setToValue(1.0);
        fade.play();
    }

    //FUNZIONE PER PASSARE DALLA PAGINA DI AUTENTICAZIONE ALLA PAGINA DI MINING
    private void switchToMiningScene(KeyPair keys) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/layout/mining.fxml"));
            Parent root = loader.load();

            // Otteniamo il controller della pagina di mining
            MiningController miningController = loader.getController();

            miningController.setMiningData(this.minerService, keys, this.miningSpecification);

            // cambio scena
            Stage stage = (Stage) mnemonicTextArea.getScene().getWindow();
            stage.setScene(new Scene(root));
            stage.setTitle("Mokamint Desktop Miner - Dashboard");
            stage.centerOnScreen();
            stage.show();

        } catch (IOException e) {
            e.printStackTrace();
        }
    }


    // Metodo helper veloce per gli errori
    private void showError(String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setContentText(message);
        alert.showAndWait();
    }

    public void setConnectionData(String uri, String path, MiningSpecification specification) {
        System.out.println("Dati ricevuti: "+ uri + path);
        this.miningSpecification = specification;

        try{
            String chainID = specification.getChainId();
            var signatureAlg = specification.getSignatureForBlocks();

            // Configura il tuo servizio helper interno
            this.minerService.configure(uri, path, signatureAlg, chainID);

            System.out.println("Specifiche caricate nel LoginController! Algoritmo del server: " + signatureAlg.getName());
        } catch (Exception e) {
            e.printStackTrace();
            showError("Errore nella configurazione delle specifiche: " + e.getMessage());
        }

    }
}