package it.univr.mokamintminer.gui;

import io.mokamint.miner.api.MiningSpecification;
import io.mokamint.nonce.Prologs;
import io.mokamint.nonce.api.Prolog;
import io.mokamint.plotter.Plots;
import it.univr.mokamintminer.core.DesktopMinerService;
import it.univr.mokamintminer.services.MinerService;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;

import java.io.File;
import java.net.URI;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.KeyPair;

public class MiningController {

    @FXML private Label statusLabel;
    @FXML private Label pubKeyLabel;
    @FXML private Label plotPathLabel;
    @FXML private ProgressBar progressBar;
    @FXML private TextArea logArea;
    @FXML private Button btnCreatePlot;
    @FXML private Button startMiningButton;
    @FXML private Button stopMiningButton;
    @FXML private HBox sizeInputBox;
    @FXML private TextField localSizeField;
    @FXML private Label nodeUrlLabel;
    @FXML private Label chainIdLabel;
    @FXML private Label plotSizeLabel;

    private DesktopMinerService miner;
    private MinerService minerService;
    private KeyPair userKeys;
    private Task<Void> plotTask;
    private MiningSpecification miningSpecification;

    /**
     * Riceve i dati dai controller precedenti.
     */
    public void setMiningData(MinerService service, KeyPair keys, MiningSpecification specification) {
        this.minerService = service;
        this.userKeys = keys;
        this.miningSpecification = specification;
        System.out.println("chiavi nel mining controller: " + userKeys.getPublic());

        try {
            byte[] rawPubBytes = keys.getPublic().getEncoded();
            String pubKeyHex = MinerService.bytesToHex(rawPubBytes);

            if (pubKeyHex.length() > 100) {
                pubKeyLabel.setText(pubKeyHex.substring(pubKeyHex.length() - 25) + "...");
            } else {
                pubKeyLabel.setText(pubKeyHex);
            }

        } catch (Exception e) {
            pubKeyLabel.setText("Errore lettura chiave");
            System.err.println("Errore nel popolamento della label: " + e.getMessage());
        }
        plotPathLabel.setText(minerService.getPlotPath());
        nodeUrlLabel.setText(minerService.getNodeUri());
        // prende la Chain ID
        if (miningSpecification != null) {
            chainIdLabel.setText(miningSpecification.getChainId());
        } else {
            chainIdLabel.setText("N/A");
        }

        updatePlotSizeInfo();

        startMiningButton.setDisable(false); // Acceso (se il plot esiste)
        stopMiningButton.setDisable(true);
        updateButtonsState();
        log("Dati e specifiche caricati dal server. Pronto per il mining.");
    }

    private void updateButtonsState() {
        File plotFile = new File(minerService.getPlotPath());
        boolean exists = plotFile.exists();

        if (exists) {
            // FILE ESISTE: Nascondi input size e disabilita "Genera"
            sizeInputBox.setVisible(false);
            sizeInputBox.setManaged(false);
            btnCreatePlot.setDisable(true);
            startMiningButton.setDisable(false);
            statusLabel.setText("Status: Plot pronto per il mining.");
        } else {
            // FILE MANCANTE: Mostra input size e abilita "Genera"
            sizeInputBox.setVisible(true);
            sizeInputBox.setManaged(true);
            btnCreatePlot.setDisable(false);
            startMiningButton.setDisable(true);
            statusLabel.setText("Status: Inserisci la dimensione per generare il plot.");
        }
    }

    /**
     * Gestisce la creazione del file di Plot.
     */
    @FXML
    private void handleCreatePlot(){
        if (plotTask != null && plotTask.isRunning()) return;

        if (miningSpecification == null) {
            log("Errore: Specifiche del server mancanti!");
            return;
        }

        String sizeText = localSizeField.getText().trim();
        if (sizeText.isEmpty()) {
            log("Errore: Inserisci una dimensione!");
            return;
        }

        long size = Long.parseLong(sizeText);

        btnCreatePlot.setDisable(true);
        startMiningButton.setDisable(true);

        plotTask = new Task<>() {
            @Override
            protected Void call() throws Exception {
                updateMessage("Creazione Plot (" + size + " nonces)...");
                long sizeInMB = (size * 262144) / (1024 * 1024);

                // 2. Mandiamo i log alla UI in modo sicuro (tramite Platform.runLater)
                javafx.application.Platform.runLater(() -> {
                    logArea.appendText("[PLOT] Avvio creazione plot deterministico...\n");
                    logArea.appendText("[PLOT] Dimensione stimata: " + sizeInMB + " MB\n");
                });

                // Spacchetto le info dalla specifica,
                String chainId = miningSpecification.getChainId();
                var signatureForBlocks = miningSpecification.getSignatureForBlocks();
                var publicKeyOfServer = miningSpecification.getPublicKeyForSigningBlocks();
                var signatureForDeadlines = miningSpecification.getSignatureForDeadlines();
                var hashingForDeadlines = miningSpecification.getHashingForDeadlines();

                // Costruisco il Prolog
                Prolog prolog = Prologs.of(
                        chainId,
                        signatureForBlocks,
                        publicKeyOfServer,
                        signatureForDeadlines,
                        userKeys.getPublic(), // La chiave pubblica del miner
                        new byte[0] // I metadati extra vuoti
                );

                Path path = Paths.get(minerService.getPlotPath());

                // crea il file di plot
                Plots.create(
                        path,
                        prolog,
                        0L, // startNonce
                        size,
                        hashingForDeadlines,
                        progress -> {
                            updateProgress(progress, 100);
                            updateMessage("In corso: " + progress + "%");
                        }
                );
                    javafx.application.Platform.runLater(() ->
                            logArea.appendText("[PLOT] File creato con successo!\n")
                    );

                return null;
            }
        };

        System.out.println("AVVIO PLOTTING UFFICIALE CON LA CHIAVE: " + MinerService.bytesToHex(this.userKeys.getPublic().getEncoded()));
        // barra di progresso e messaggi
        progressBar.progressProperty().bind(plotTask.progressProperty());
        statusLabel.textProperty().bind(plotTask.messageProperty());

        plotTask.setOnSucceeded(e -> {
            unbindUI();
            log("Plot completato con successo! " + size + " nonces creati.");
            statusLabel.setText("Status: Plot pronto.");
            updatePlotSizeInfo();
            updateButtonsState();
        });

        plotTask.setOnFailed(e -> {
            unbindUI();
            Throwable ex = plotTask.getException();
            log("Errore durante il plotting: " + plotTask.getException().getMessage());
            statusLabel.setText("Status: Errore creazione.");
            ex.printStackTrace();
        });

        new Thread(plotTask).start();
    }

    @FXML
    private void onStartMining() {
        log("Avvio sessione di mining...");
        System.out.println("AVVIO MINING CON CHIAVE REALE: " + MinerService.bytesToHex(this.userKeys.getPublic().getEncoded()));

        try {
            // Recupera i dati dal service
            URI uri = URI.create(minerService.getNodeUri());
            Path path = Path.of(minerService.getPlotPath());
            String chainId = minerService.getChainID();

            progressBar.setProgress(-1.0);
            statusLabel.setText("Mining in corso... in ascolto di sfide");

            // Crea il miner reale
            // Passa un listener che intercetta i log e le deadline
            miner = new DesktopMinerService(
                    uri,
                    chainId,
                    path,
                    userKeys,
                    new DesktopMinerService.MinerListener() {
                        @Override
                        public void onConnected() {
                            Platform.runLater(() -> {
                                log("Connesso al nodo: " + uri);
                                statusLabel.setText("Status: Connesso");
                            });
                        }

                        @Override
                        public void onDisconnected() {
                            Platform.runLater(() -> {
                                log("Disconnesso dal nodo.");
                                statusLabel.setText("Status: Disconnesso");
                                onStopMining(); // Reset interfaccia
                            });
                        }

                        @Override
                        public void onDeadline(int totalDeadlines) {
                            Platform.runLater(() -> {
                                log("Nuova deadline trovata! (Totale: " + totalDeadlines + ")");
                                statusLabel.setText("Status: Mining attivo...");
                            });
                        }

                        public void onMessage(String message) {
                            Platform.runLater(() -> log("INFO: " + message));
                        }
                    }
            );

            startMiningButton.setDisable(true);
            stopMiningButton.setDisable(false);
            statusLabel.setText("Status: Inizializzazione...");

        } catch (Exception e) {
            log("Errore fatale: " + e.getMessage());
            statusLabel.setText("Status: Errore");
            e.printStackTrace();
        }
    }

    @FXML
    private void onStopMining() {
        if (miner != null) {
            miner.close(); // chiude il WebSocket
            miner = null;
        }
        startMiningButton.setDisable(false);
        stopMiningButton.setDisable(true);
        log("Mining fermato.");
        progressBar.setProgress(0.0);
        statusLabel.setText("Status: Pronto");
    }

    // Helper per pulire i collegamenti UI
    private void unbindUI() {
        progressBar.progressProperty().unbind();
        statusLabel.textProperty().unbind();
    }

    // Helper per scrivere i log nella TextArea
    private void log(String message) {
        Platform.runLater(() -> {
            logArea.appendText("[" + java.time.LocalTime.now().withNano(0) + "] " + message + "\n");
        });
    }

    private void updatePlotSizeInfo() {
        try {
            File plotFile = new File(minerService.getPlotPath());
            if (plotFile.exists()) {
                long fileBytes = plotFile.length();

                // (1 nonce = 262144 bytes)
                long nonces = fileBytes / 262144;

                // Calcola i MB/GB
                double fileInMB = (double) fileBytes / (1024 * 1024);

                if (fileInMB >= 1024) {
                    double fileInGB = fileInMB / 1024;
                    plotSizeLabel.setText(String.format("%d nonces (~%.2f GB)", nonces, fileInGB));
                } else {
                    plotSizeLabel.setText(String.format("%d nonces (~%.2f MB)", nonces, fileInMB));
                }
            } else {
                plotSizeLabel.setText("File non ancora generato");
            }
        } catch (Exception e) {
            plotSizeLabel.setText("Errore lettura dimensioni");
        }
    }
}