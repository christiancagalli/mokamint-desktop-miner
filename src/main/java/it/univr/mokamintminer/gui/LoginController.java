package it.univr.mokamintminer.gui;

import io.hotmoka.crypto.Entropies;
import io.mokamint.miner.api.MiningSpecification;
import it.univr.mokamintminer.services.MinerInstance;
import it.univr.mokamintminer.services.MinerService;
import it.univr.mokamintminer.services.MinerXmlManager; // IMPORTATO
import javafx.animation.FadeTransition;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import javafx.util.Duration;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;

public class LoginController {

    @FXML private VBox manualInputBox;
    @FXML private VBox loadPemBox;
    @FXML private TextField pemPathField;
    @FXML private Button loginPemButton;
    @FXML private TextArea mnemonicTextArea;

    private final MinerService minerService = new MinerService();
    private MinerInstance temporaryMiner;
    private MiningSpecification miningSpecification;

    @FXML
    public void initialize() {
        // Inizializzazione pulita
    }

    public void setTemporaryMiner(MinerInstance temporaryMiner) {
        this.temporaryMiner = temporaryMiner;
        this.miningSpecification = temporaryMiner.getMiningSpecification();
        System.out.println("LoginController: Ricevuto miner " + temporaryMiner.getName() + " con UUID: " + temporaryMiner.getUuid());
    }

    @FXML
    private void handleGenerateNew() {
        if (miningSpecification == null) {
            showError("Specifiche del server non caricate. Impossibile generare le chiavi.");
            return;
        }

        try {
            String newMnemonic = minerService.generateNewMnemonic();
            String[] words = newMnemonic.split("[,\\s]+");

            var b39 = io.hotmoka.crypto.BIP39Mnemonics.of(words);
            byte[] entropyBytes = b39.getBytes();
            var entropy = io.hotmoka.crypto.Entropies.of(entropyBytes);

            var signatureForDeadlines = miningSpecification.getSignatureForDeadlines();
            var keyPair = entropy.keys("", signatureForDeadlines);

            temporaryMiner.setKeyPair(keyPair);

            var publicKey = keyPair.getPublic();
            String publicKeyBase58 = io.hotmoka.crypto.Base58.toBase58String(signatureForDeadlines.encodingOf(publicKey));
            // Solo la chiave per le DEADLINE è quella del miner; quella per i BLOCCHI è del
            // nodo ed è già stata salvata da ConnectionController: non va sovrascritta.
            temporaryMiner.setPublicKeyDeadlinesBase58(publicKeyBase58);

            // Salviamo l'entropia o esportiamo subito il .pem temporaneo se necessario
            File identitiesDir = new File("miner_storage/identities");
            if (!identitiesDir.exists()) identitiesDir.mkdirs();
            File finalPemFile = new File(identitiesDir, temporaryMiner.getUuid() + ".pem");
            entropy.dump(finalPemFile.toPath());
            temporaryMiner.setPemPath(finalPemFile.getAbsolutePath());

            showMnemonicPopup(newMnemonic);
            handlePlotAndFinalize();

        } catch (Exception e) {
            showError("Errore durante la creazione delle chiavi: " + e.getMessage());
        }
    }

    @FXML
    private void handleLoadPem() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Seleziona il tuo file Identità (.pem)");
        fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("PEM Files", "*.pem"));
        fileChooser.setInitialDirectory(new File(System.getProperty("user.home")));
        File selectedFile = fileChooser.showOpenDialog(pemPathField.getScene().getWindow());

        if (selectedFile != null) {
            pemPathField.setText(selectedFile.getAbsolutePath());
            loginPemButton.setVisible(true);
            loginPemButton.setManaged(true);
        }
    }

    @FXML
    private void handleFinalPemLogin() {
        String path = pemPathField.getText();
        if (!path.isEmpty()) {
            try {
                var entropy = Entropies.load(Paths.get(path));
                var signatureForDeadlines = miningSpecification.getSignatureForDeadlines();
                var keyPair = entropy.keys("", signatureForDeadlines);

                temporaryMiner.setKeyPair(keyPair);

                var publicKey = keyPair.getPublic();
                String publicKeyBase58 = io.hotmoka.crypto.Base58.toBase58String(signatureForDeadlines.encodingOf(publicKey));
                // Solo la chiave per le DEADLINE è quella del miner; quella per i BLOCCHI è del
                // nodo ed è già stata salvata da ConnectionController: non va sovrascritta.
                temporaryMiner.setPublicKeyDeadlinesBase58(publicKeyBase58);

                // Copiamo il file PEM caricato nella nostra cartella di storage interna
                File identitiesDir = new File("miner_storage/identities");
                if (!identitiesDir.exists()) identitiesDir.mkdirs();
                File finalPemFile = new File(identitiesDir, temporaryMiner.getUuid() + ".pem");
                Files.copy(Paths.get(path), finalPemFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                temporaryMiner.setPemPath(finalPemFile.getAbsolutePath());

                System.out.println("Login da file PEM riuscito!");
                handlePlotAndFinalize();

            } catch (Exception e) {
                e.printStackTrace();
                showError("File PEM non valido o incompatibile con il server.");
            }
        }
    }

    @FXML
    private void handleManualLogin() {
        String mnemonic = mnemonicTextArea.getText().trim();
        if (minerService.isValidMnemonic(mnemonic)) {
            try {
                String[] words = mnemonic.split("[,\\s]+");
                var b39 = io.hotmoka.crypto.BIP39Mnemonics.of(words);
                byte[] entropyBytes = b39.getBytes();
                var entropy = io.hotmoka.crypto.Entropies.of(entropyBytes);

                var signatureForDeadlines = miningSpecification.getSignatureForDeadlines();
                var keyPair = entropy.keys("", signatureForDeadlines);

                temporaryMiner.setKeyPair(keyPair);

                var publicKey = keyPair.getPublic();
                String publicKeyBase58 = io.hotmoka.crypto.Base58.toBase58String(signatureForDeadlines.encodingOf(publicKey));
                // Solo la chiave per le DEADLINE è quella del miner; quella per i BLOCCHI è del
                // nodo ed è già stata salvata da ConnectionController: non va sovrascritta.
                temporaryMiner.setPublicKeyDeadlinesBase58(publicKeyBase58);

                File identitiesDir = new File("miner_storage/identities");
                if (!identitiesDir.exists()) identitiesDir.mkdirs();
                File finalPemFile = new File(identitiesDir, temporaryMiner.getUuid() + ".pem");
                entropy.dump(finalPemFile.toPath());
                temporaryMiner.setPemPath(finalPemFile.getAbsolutePath());

                handlePlotAndFinalize();
            } catch (Exception e) {
                showError("Errore nella derivatione crittografica: " + e.getMessage());
            }
        } else {
            showError("La frase mnemonica inserita non è valida.");
        }
    }

    private void handlePlotAndFinalize() {
        try {
            String uuid = temporaryMiner.getUuid();

            File dataDir = new File("miner_storage/data");
            if (!dataDir.exists()) dataDir.mkdirs();

            File finalPlotFile = new File(dataDir, uuid + ".plot");

            // --- POP-UP: SCELTA IMPORTA O GENERA DA ZERO ---
            Alert alertChoice = new Alert(Alert.AlertType.CONFIRMATION);
            alertChoice.setTitle("Configurazione File di Plot");
            alertChoice.setHeaderText("Gestione del file .plot per il miner: " + temporaryMiner.getName());
            alertChoice.setContentText("Scegli se importare un file .plot esistente oppure configurare il miner per generarne uno nuovo.");

            ButtonType btnImport = new ButtonType("Importa Esistente");
            ButtonType btnCreateNew = new ButtonType("Genera Nuovo (In seguito)");
            ButtonType btnCancel = new ButtonType("Annulla", ButtonBar.ButtonData.CANCEL_CLOSE);

            alertChoice.getButtonTypes().setAll(btnImport, btnCreateNew, btnCancel);
            var resultChoice = alertChoice.showAndWait();

            if (resultChoice.isEmpty() || resultChoice.get() == btnCancel) {
                return;
            }

            if (resultChoice.get() == btnImport) {
                FileChooser fileChooser = new FileChooser();
                fileChooser.setTitle("Seleziona il tuo vecchio File di Plot");
                fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Plot Files", "*.plot"));
                File selectedPlot = fileChooser.showOpenDialog(mnemonicTextArea.getScene().getWindow());

                if (selectedPlot == null) {
                    showError("Importazione annullata.");
                    return;
                }

                Files.copy(selectedPlot.toPath(), finalPlotFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                temporaryMiner.setPlotPath(finalPlotFile.getAbsolutePath());

                // Leggiamo la grandezza reale del file importato per salvarla nell'XML
                long fileBytes = finalPlotFile.length();
                long nonces = fileBytes / 262144;
                // Impostiamo la dimensione reale
                // temporaryMiner.setPlotSize((int) nonces);

                System.out.println("Plot esistente importato con successo!");

            } else if (resultChoice.get() == btnCreateNew) {
                // Sotto richiesta del prof: NON creiamo alcun file fittizio sul disco.
                // Registriamo semplicemente il percorso in cui DOVRÀ nascere il file.
                // Avendo dimensione fisica 0 sul disco, il MiningController saprà autonomamente di dover mostrare i pulsanti di generazione.
                temporaryMiner.setPlotPath(finalPlotFile.getAbsolutePath());
                System.out.println("Configurato per la generazione futura. File fisico non creato, pronto per la GUI.");
            }

            // --- SCRITTURA FINALE SULL'XML ---
            MinerXmlManager.addMiner(temporaryMiner);
            System.out.println("Miner memorizzato nell'XML con successo!");

            // Chiudiamo il pop-up del wizard e torniamo alla dashboard principal
            Stage stage = (Stage) mnemonicTextArea.getScene().getWindow();
            stage.close();

        } catch (Exception e) {
            e.printStackTrace();
            showError("Errore durante la finalizzazione del miner: " + e.getMessage());
        }
    }

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

    @FXML
    private void handleShowLoadArea() {
        manualInputBox.setVisible(false);
        manualInputBox.setManaged(false);
        boolean isVisible = loadPemBox.isVisible();
        loadPemBox.setVisible(!isVisible);
        loadPemBox.setManaged(!isVisible);
        loadPemBox.setOpacity(0);

        FadeTransition fade = new FadeTransition(Duration.millis(500), loadPemBox);
        fade.setFromValue(0.0);
        fade.setToValue(1.0);
        fade.play();
    }

    private void showError(String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setContentText(message);
        alert.showAndWait();
    }

    private void showMnemonicPopup(String mnemonic) {
        Alert alert = new Alert(Alert.AlertType.WARNING);
        alert.setTitle("Identità Creata");
        alert.setHeaderText("Chiavi generate in memoria con successo!");

        DialogPane dialogPane = alert.getDialogPane();
        try {
            String cssPath = LoginController.class.getResource("/layout/style.css").toExternalForm();
            dialogPane.getStylesheets().add(cssPath);
            dialogPane.getStyleClass().add("dialog-pane");
        } catch (Exception e) {
            // Ignora se lo stile non si carica
        }

        Label warningLabel = new Label(
                "Le chiavi crittografiche sono pronte in memoria RAM.\n\n" +
                        "IMPORTANTE: Trascrivi queste 12 parole in un luogo sicuro.\n" +
                        "Il file definitivo dell'identità è stato salvato nel tuo storage protetto."
        );

        TextArea mnemonicArea = new TextArea(mnemonic);
        mnemonicArea.setEditable(false);
        mnemonicArea.setWrapText(true);
        mnemonicArea.setPrefRowCount(2);
        mnemonicArea.setPrefWidth(400);

        Button btnCopy = new Button("📋 Copia negli appunti");
        btnCopy.setOnAction(e -> {
            Clipboard clipboard = Clipboard.getSystemClipboard();
            ClipboardContent content = new ClipboardContent();
            content.putString(mnemonic);
            clipboard.setContent(content);
            btnCopy.setText("✓ Copiato!");
            btnCopy.setDisable(true);
        });

        VBox customContent = new VBox(12);
        customContent.getChildren().addAll(warningLabel, mnemonicArea, btnCopy);
        dialogPane.setContent(customContent);

        alert.showAndWait();
    }
}
/*package it.univr.mokamintminer.gui;

import io.hotmoka.crypto.Entropies;
import io.mokamint.miner.api.MiningSpecification;
import it.univr.mokamintminer.services.MinerService;
import javafx.animation.FadeTransition;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import javafx.util.Duration;

import java.io.File;
import java.io.IOException;
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
        fileChooser.setInitialDirectory(new File(System.getProperty("user.home")));
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

                entropy.dump(Paths.get(file.getAbsolutePath()));    // scrittura file .pem
                showMnemonicPopup(newMnemonic); // Mostra il pop-up informativo (mostra le parole)

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
        fileChooser.setInitialDirectory(new File(System.getProperty("user.home")));
        File selectedFile = fileChooser.showOpenDialog(pemPathField.getScene().getWindow());

        if (selectedFile != null) {
            pemPathField.setText(selectedFile.getAbsolutePath());
            loginPemButton.setVisible(true);    // MOSTRA IL TASTO ACCEDI
            loginPemButton.setManaged(true);
        }
    }

    //BOTTONE DI LOGIN DOPO AVER CARICATO IL FILE
    @FXML
    private void handleFinalPemLogin() {
        String path = pemPathField.getText();
        if (!path.isEmpty()) {
            try {
                var entropy = Entropies.load(Paths.get(path));

                // Ricava la coppia di chiavi usando l'algoritmo dinamico del server
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

            // Ottengo il controller della pagina di mining
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


    // Metodo helper per gli errori
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

            // Configura il servizio helper interno
            this.minerService.configure(uri, path, signatureAlg, chainID);

            System.out.println("Specifiche caricate nel LoginController! Algoritmo del server: " + signatureAlg.getName());
        } catch (Exception e) {
            e.printStackTrace();
            showError("Errore nella configurazione delle specifiche: " + e.getMessage());
        }
    }

    private void showMnemonicPopup(String mnemonic) {
        Alert alert = new Alert(Alert.AlertType.WARNING);
        alert.setTitle("Identità Creata");
        alert.setHeaderText("Salvataggio completato!");

        DialogPane dialogPane = alert.getDialogPane();
        String cssPath = LoginController.class.getResource("/layout/style.css").toExternalForm();
        dialogPane.getStylesheets().add(cssPath);
        dialogPane.getStyleClass().add("dialog-pane");

        Label warningLabel = new Label(
                "Il file PEM è pronto.\n\n" +
                        "ATTENZIONE: Salva queste 12 parole in un luogo sicuro.\n" +
                        "Questa è l'UNICA volta che ti verranno mostrate.\n" +
                        "Se le perdi, non potrai più recuperare il tuo account!"
        );
        warningLabel.getStyleClass().add("dialog-warning-label");

        TextArea mnemonicArea = new TextArea(mnemonic);
        mnemonicArea.setEditable(false);
        mnemonicArea.setWrapText(true);
        mnemonicArea.setPrefRowCount(2);
        mnemonicArea.setPrefWidth(400);
        mnemonicArea.getStyleClass().add("dialog-mnemonic-area");

        Button btnCopy = new Button("📋 Copia negli appunti");
        btnCopy.getStyleClass().add("button-dialog-copy");
        btnCopy.setOnAction(e -> {
            Clipboard clipboard = Clipboard.getSystemClipboard();
            ClipboardContent content = new ClipboardContent();
            content.putString(mnemonic);
            clipboard.setContent(content);

            btnCopy.setText("✓ Copiato!");
            btnCopy.setDisable(true);
        });

        VBox customContent = new VBox(12);
        customContent.getChildren().addAll(warningLabel, mnemonicArea, btnCopy);
        dialogPane.setContent(customContent);

        alert.showAndWait();
    }
}*/