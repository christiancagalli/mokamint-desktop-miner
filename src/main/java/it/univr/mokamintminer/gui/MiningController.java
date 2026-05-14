package it.univr.mokamintminer.gui;

import it.univr.mokamintminer.core.DesktopMinerService;
import it.univr.mokamintminer.services.MinerService;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

import java.io.File;
import java.net.URI;
import java.nio.file.Path;
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
    @FXML private VBox sizeInputBox;
    @FXML private TextField localSizeField;

    private DesktopMinerService miner;
    private MinerService minerService;
    private KeyPair userKeys;
    private Task<Void> plotTask;

    /**
     * Riceve i dati dai controller precedenti.
     */
    public void setMiningData(MinerService service, KeyPair keys) {
        this.minerService = service;
        this.userKeys = keys;

        // Popoliamo la UI con i dati salvati
        String pubKeyHex = MinerService.bytesToHex(keys.getPublic().getEncoded());
        pubKeyLabel.setText(pubKeyHex.substring(0, 20) + "...");
        plotPathLabel.setText(minerService.getPlotPath());

        startMiningButton.setDisable(false); // Acceso (se il plot esiste)
        stopMiningButton.setDisable(true);
        updateButtonsState();
        log("Dati caricati. Pronto per il mining.");
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
    private void handleCreatePlot() {
        if (plotTask != null && plotTask.isRunning()) return;
        //int plotSize = minerService.getPlotSize();                                    //PLOTSIZE

        String sizeText = localSizeField.getText().trim();
        if (sizeText.isEmpty()) {
            log("Errore: Inserisci una dimensione!");
            return;
        }

        int size = Integer.parseInt(sizeText);

        btnCreatePlot.setDisable(true);
        startMiningButton.setDisable(true);

        plotTask = new Task<>() {
            @Override
            protected Void call() throws Exception {
                updateMessage("Creazione Plot (" + size + " nonces)...");

                // Usiamo il metodo che abbiamo scritto nel MinerService
                minerService.createPlot(
                        Path.of(minerService.getPlotPath()),
                        userKeys,
                        0L, // startNonce
                        size, // plotSize (puoi recuperarlo dal service)
                        progress -> updateProgress(progress, 100),
                        message -> log(message)
                );
                return null;
            }
        };

        // Colleghiamo la barra di progresso e i messaggi
        progressBar.progressProperty().bind(plotTask.progressProperty());
        statusLabel.textProperty().bind(plotTask.messageProperty());

        plotTask.setOnSucceeded(e -> {
            unbindUI();
            log("Plot completato con successo! " + size + " nonces creati.");
            statusLabel.setText("Status: Plot pronto.");
            updateButtonsState();
        });

        plotTask.setOnFailed(e -> {
            unbindUI();
            log("Errore durante il plotting: " + plotTask.getException().getMessage());
            statusLabel.setText("Status: Errore creazione.");
        });

        new Thread(plotTask).start();
    }

    @FXML
    private void onStartMining() {
        log("Avvio sessione di mining...");

        try {
            // 1. Recuperiamo i dati dal service
            URI uri = URI.create(minerService.getNodeUri());
            Path path = Path.of(minerService.getPlotPath());

            // 2. Creiamo il miner reale
            // Passiamo un listener che intercetta i log e le deadline
            miner = new DesktopMinerService(
                    uri,
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

                        // Se la tua versione di DesktopMinerService lo supporta:
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
            miner.close(); // Metodo fondamentale per chiudere il WebSocket
            miner = null;
        }
        startMiningButton.setDisable(false);
        stopMiningButton.setDisable(true);
        log("Mining fermato.");
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
}

 /*@FXML
    private void onStartMining() {
        log("Identificazione rete in corso...");
        startMiningButton.setDisable(true);
        statusLabel.setText("Status: Identificazione...");

        // STEP 1: Creiamo un Task per recuperare il ChainID
        Task<String> getChainIdTask = new Task<>() {
            @Override
            protected String call() throws Exception {
                URI uri = URI.create(minerService.getNodeUri());
                // Apriamo una connessione temporanea al nodo per leggere le info
                try (var node = io.mokamint.node.remote.RemotePublicNodes.of(uri, 5000)) {
                    return node.getInfo().toString();
                }
            }
        };

        // Cosa fare se il Task ha successo
        getChainIdTask.setOnSucceeded(e -> {
            String discoveredChainId = getChainIdTask.getValue();
            log("Rete identificata: " + discoveredChainId);

            // STEP 2: Facciamo partire il miner vero e proprio con l'ID scoperto
            runActualMining(discoveredChainId);
        });

        // Cosa fare se il Task fallisce (es. nodo spento)
        getChainIdTask.setOnFailed(e -> {
            Throwable exception = getChainIdTask.getException();
            log("Errore identificazione: " + exception.getMessage());
            statusLabel.setText("Status: Errore connessione");
            startMiningButton.setDisable(false);
        });

        new Thread(getChainIdTask).start();
    }

    private void runActualMining(String chainId) {
        try {
            miner = new DesktopMinerService(
                    URI.create(minerService.getNodeUri()),
                    chainId, // <-- Ora passiamo l'ID dinamico!
                    Path.of(minerService.getPlotPath()),
                    userKeys,
                    new DesktopMinerService.MinerListener() {
                        @Override
                        public void onConnected() {
                            Platform.runLater(() -> {
                                log("Miner connesso e operativo.");
                                statusLabel.setText("Status: Mining attivo (" + chainId + ")");
                                stopMiningButton.setDisable(false);
                            });
                        }

                        @Override
                        public void onDisconnected() {
                            Platform.runLater(() -> {
                                log("Connessione persa.");
                                onStopMining();
                            });
                        }

                        @Override
                        public void onDeadline(int total) {
                            Platform.runLater(() -> log("Nuova deadline trovata! (Totale: " + total + ")"));
                        }

                        @Override
                        public void onMessage(String msg) {
                            Platform.runLater(() -> log(msg));
                        }
                    }
            );
        } catch (Exception ex) {
            log("Errore durante l'avvio del motore: " + ex.getMessage());
            startMiningButton.setDisable(false);
        }
    }*/





/*package it.univr.mokamintminer.gui;

import it.univr.mokamintminer.core.DesktopMinerService;
import it.univr.mokamintminer.services.MinerService;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.TextField;

import java.net.URI;
import java.nio.file.Path;
import java.security.KeyPair;
import java.util.concurrent.atomic.AtomicBoolean;

public class MiningController {

    @FXML
    private TextField endpointField;

    @FXML
    private TextField plotFileField;

    @FXML
    private TextField plotSizeField;

    @FXML
    private Label statusLabel;

    @FXML
    private Label deadlinesLabel;

    @FXML
    private ProgressBar progressBar;

    @FXML
    private Button createPlotButton;

    @FXML
    private Button stopPlotButton;

    @FXML
    private Button startMiningButton;

    @FXML
    private Button stopMiningButton;

    @FXML
    private javafx.scene.control.TextArea logArea;

    private final MinerService minerService = new MinerService();

    private KeyPair userKeys;

    // PLOT
    private Task<Void> plotTask;
    private Thread plotThread;

    // MINING
    private DesktopMinerService miner;

    private final java.util.concurrent.atomic.AtomicBoolean simulationActive = new AtomicBoolean(false);

    //PASSAGGIO CHIAVI
    public void setUserKeys(KeyPair keys) {
        this.userKeys = keys;
        System.out.println("MiningController: Chiavi ricevute correttamente!");
    }

    // CREATE PLOT
    @FXML
    private void onCreatePlot() {
        if (plotTask != null && plotTask.isRunning()) {
            statusLabel.setText("Status: plot already running");
            return;
        }

        String endpoint = endpointField.getText().trim();
        String plotFile = plotFileField.getText().trim();

        if (endpoint.isEmpty() || plotFile.isEmpty()) {
            statusLabel.setText("Status: endpoint missing or file missing");
            return;
        }

        // Legge la plot size dal campo, con fallback al valore di default
        long plotSize;
        try {
            String sizeText = plotSizeField.getText().trim();
            plotSize = sizeText.isEmpty() ? MinerService.DEFAULT_PLOT_SIZE : Long.parseLong(sizeText);
            if (plotSize <= 0)
                throw new NumberFormatException();
        } catch (NumberFormatException e) {
            statusLabel.setText("Status invalid plot size (must be a positive number)");
            return;
        }

        Path plotPath = Path.of(plotFile);
        final long finalPlotSize = plotSize;
        setPlotMode(true);

        plotTask = new Task<>() {
            @Override
            protected Void call() throws Exception {
                log(" Creating plot (" + finalPlotSize + " nonces, ~" + (finalPlotSize * 262144 / (1024 * 1024))
                        + " MB)...");
                updateMessage("Status: Creating plot...");

                /*minerService.createPlot(
                        plotPath,
                        0L,
                        finalPlotSize,
                        endpoint,
                        progress -> {
                            if (isCancelled())
                                return;

                            updateProgress(progress, 100);
                            updateMessage("Progress: " + progress + "%");
                        }
                );

                return null;
            }
        };

        //  UI binding
        statusLabel.textProperty().bind(plotTask.messageProperty());
        progressBar.progressProperty().bind(plotTask.progressProperty());

        plotTask.setOnSucceeded(e -> {
            cleanupPlotBindings();
            log(" Plot completed");
            statusLabel.setText("Status: Plot created successfully");
            progressBar.setProgress(1.0);
            setPlotMode(false);
            plotTask = null;
        });

        plotTask.setOnCancelled(e -> {
            cleanupPlotBindings();
            log(" Plot stopped");
            statusLabel.setText("Status: Plot creation stopped");
            progressBar.setProgress(0);
            setPlotMode(false);
            plotTask = null;
        });

        plotTask.setOnFailed(e -> {
            cleanupPlotBindings();
            statusLabel.setText("Error: " + plotTask.getException().getMessage());
            progressBar.setProgress(0);
            setPlotMode(false);
            plotTask = null;
        });

        plotThread = new Thread(plotTask, "plot-creation-thread");
        plotThread.setDaemon(true);
        plotThread.start();
    }

    // STOP PLOT
    @FXML
    private void onStopPlot() {
        if (plotTask != null && plotTask.isRunning()) {
            plotTask.cancel();
        }
    }

    // START MINING
    @FXML
    private void onStartMining() {
        if (plotTask != null && plotTask.isRunning()) return;

        if (miner != null) {
            statusLabel.setText("Status: mining already running");
            return;
        }

        // --- AGGIUNTA: Controllo Chiavi ---
        if (this.userKeys == null) {
            statusLabel.setText("Status: Error - No identity loaded!");
            log(" Errore: Chiavi non trovate. Torna al login.");
            return;
        }

        String plotFile = plotFileField.getText().trim();
        if (plotFile.isEmpty()) {
            statusLabel.setText("Status: plot file missing");
            return;
        }

        URI endpointUri;
        try {
            endpointUri = URI.create(endpointField.getText().trim());
        } catch (Exception e) {
            statusLabel.setText("Status: Invalid endpoint URI");
            return;
        }

        updateDeadlinesLabel(0);

        try {
            // --- MODIFICA: Passiamo 'this.userKeys' al costruttore ---
            miner = new DesktopMinerService(
                    endpointUri,
                    Path.of(plotFile),
                    this.userKeys,
                    new DesktopMinerService.MinerListener() {
                        @Override
                        public void onConnected() {
                            simulationActive.set(false);
                            Platform.runLater(() -> {
                                log("Connect to node");
                                statusLabel.setText("Status: Connected");
                            });
                        }

                        @Override
                        public void onDisconnected() {
                            Platform.runLater(() -> {
                                log(" Disconnected from node");
                                statusLabel.setText("Status: Disconnected");
                            });
                        }

                        @Override
                        public void onDeadline(int totalDeadlines) {
                            Platform.runLater(() -> {
                                log(" Deadline computed (total: " + totalDeadlines + ")");
                                statusLabel.setText("Status: Mining...");
                                updateDeadlinesLabel(totalDeadlines);
                            });
                        }
                    }
            );

            log(" Starting mining with identity: " +
                    it.univr.mokamintminer.services.MinerService.bytesToHex(userKeys.getPublic().getEncoded()).substring(0, 15) + "...");
            statusLabel.setText("Status: Mining started");
            startSimulationIfNoConnection();

        } catch (Exception e) {
            statusLabel.setText("Error: " + e.getMessage());
            e.printStackTrace();
        }
    }

    // STOP MINING
    @FXML
    private void onStopMining() {
        // Se il plot è attivo, non fa nulla
        if (plotTask != null && plotTask.isRunning())
            return;

        // ferma anche la simulazione se attiva
        simulationActive.set(false);

        if (miner != null) {
            miner.close();
            miner = null;
        }

        log(" Mining stopped");
        statusLabel.setText("Status: mining stopped");
    }

    // HELPERS
    private void updateDeadlinesLabel(int count) {
        deadlinesLabel.setText("Deadlines computed: " + count);
    }

    private void setPlotMode(boolean plotting) {
        createPlotButton.setDisable(plotting);
        stopPlotButton.setDisable(!plotting);
        startMiningButton.setDisable(plotting);
        stopMiningButton.setDisable(plotting);
    }

    private void cleanupPlotBindings() {
        if (statusLabel.textProperty().isBound())
            statusLabel.textProperty().unbind();

        if (progressBar.progressProperty().isBound())
            progressBar.progressProperty().unbind();
    }

    private void log(String message) {
        String time = java.time.LocalDateTime.now().withNano(0).toString();

        Platform.runLater(() -> {
            logArea.appendText("[" + time + "]" + message + "\n");
            logArea.setScrollTop(Double.MAX_VALUE);
        });
    }
}

/*private void startSimulationIfNoConnection() {
        // Attiva il flag prima di partire
        simulationActive.set(true);

        new Thread(() -> {
            try {
                Thread.sleep(3000);
                // Se onConnected() reale è già arrivato, non simulo
                if (!simulationActive.get())
                    return;

                log(" Simulated: Connected");
                Platform.runLater(() -> statusLabel.setText("Status: Connected (simulated)"));

                for (int i=0; i<5; i++) {
                    Thread.sleep(1500);
                    if (!simulationActive.get())
                        return;
                    final int count = i;

                    log(" Simulated: Deadline computed");
                    Platform.runLater(() -> updateDeadlinesLabel(count));
                }

                Thread.sleep(1000);
                if (!simulationActive.get())
                    return;

                log(" Simulated: Disconnected");
                Platform.runLater(() -> statusLabel.setText("Status: Disconnected (simulated)"));

            } catch (InterruptedException ignored) {}
        }, "simulation-thread").start();
    }*/