package it.univr.mokamintminer.gui;

import io.hotmoka.crypto.Entropies;
import io.mokamint.miner.api.MiningSpecification;
import it.univr.mokamintminer.services.MinerInstance;
import it.univr.mokamintminer.services.MinerService;
import it.univr.mokamintminer.services.MinerXmlManager;
import it.univr.mokamintminer.utils.DialogUtils;
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

            // Esporto l'entropia nel file .pem definitivo dell'identità
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

                // Copio il file PEM caricato nella cartella di storage interna
                File identitiesDir = new File("miner_storage/identities");
                if (!identitiesDir.exists()) identitiesDir.mkdirs();
                File finalPemFile = new File(identitiesDir, temporaryMiner.getUuid() + ".pem");
                Files.copy(Paths.get(path), finalPemFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                temporaryMiner.setPemPath(finalPemFile.getAbsolutePath());

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
            DialogUtils.applyDarkStyle(alertChoice);

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

            } else if (resultChoice.get() == btnCreateNew) {
                // Non creo nessun file sul disco: registro solo il percorso in cui nascerà
                // il plot. Avendo dimensione 0, la dashboard saprà che va ancora generato.
                temporaryMiner.setPlotPath(finalPlotFile.getAbsolutePath());
            }

            // Scrittura finale del miner nell'XML
            MinerXmlManager.addMiner(temporaryMiner);

            // Chiudo la finestra del wizard e torno alla dashboard
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
        DialogUtils.applyDarkStyle(alert);
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
