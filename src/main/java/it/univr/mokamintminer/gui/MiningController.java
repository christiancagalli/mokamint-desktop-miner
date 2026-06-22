package it.univr.mokamintminer.gui;

import io.hotmoka.crypto.Base58;
import io.mokamint.miner.api.MiningSpecification;
import io.mokamint.nonce.Prologs;
import io.mokamint.nonce.api.Prolog;
import io.mokamint.plotter.Plots;
import it.univr.mokamintminer.core.DesktopMinerService;
import it.univr.mokamintminer.services.MinerManager;
import it.univr.mokamintminer.services.MinerService;
import it.univr.mokamintminer.services.MinerInstance;
import it.univr.mokamintminer.services.MinerXmlManager;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;

import java.io.File;
import java.math.BigInteger;
import java.net.URI;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.KeyPair;
import java.util.Optional;

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
    @FXML private Label balanceLabel;

    private DesktopMinerService miner;
    private MinerService minerService;
    private MinerInstance minerData; // Modello dati dell'XML
    private KeyPair userKeys;
    private Task<Void> plotTask;
    private MiningSpecification miningSpecification;

    /**
     * Inizializza i dati nella console di mining gestendo sia flussi di rete che ripristini da XML.
     */
    public void setMiningData(MinerService service, KeyPair keys, MiningSpecification specification, MinerInstance minerInstance) {
        this.minerService = service;
        this.userKeys = keys;
        this.miningSpecification = specification;
        this.minerData = minerInstance;

        // 1. Chiave pubblica in formato base58 (lo stesso usato dal nodo), più leggibile dell'hex
        try {
            String pubBase58;
            if (minerInstance != null && minerInstance.getPublicKeyDeadlinesBase58() != null
                    && !minerInstance.getPublicKeyDeadlinesBase58().isBlank()) {
                pubBase58 = minerInstance.getPublicKeyDeadlinesBase58();
            } else {
                // Fallback: ricava il base58 dall'algoritmo di firma per le deadline
                String sigStr = (specification != null) ? specification.getSignatureForDeadlines().toString()
                        : (minerInstance != null ? minerInstance.getSignatureForDeadlines() : "ed25519");
                var sigAlgo = io.hotmoka.crypto.SignatureAlgorithms.of(sigStr);
                pubBase58 = Base58.toBase58String(sigAlgo.encodingOf(keys.getPublic()));
            }
            pubKeyLabel.setText(pubBase58);
        } catch (Exception e) {
            pubKeyLabel.setText("Errore lettura chiave");
            System.err.println("Errore nel popolamento della label: " + e.getMessage());
        }

        // 2. Recupero intelligente dei percorsi e URI (Fallback su minerData se il servizio è vuoto)
        String nodeUri = (minerInstance != null) ? minerInstance.getNodeUri() : service.getNodeUri();
        String plotPath = (minerInstance != null) ? minerInstance.getPlotPath() : service.getPlotPath();

        nodeUrlLabel.setText(nodeUri != null ? nodeUri : "N/A");
        plotPathLabel.setText(plotPath != null ? plotPath : "N/A");

        // 3. Recupero flessibile della Chain ID
        if (miningSpecification != null) {
            chainIdLabel.setText(miningSpecification.getChainId());
            log("Dati e specifiche caricati dal server. Pronto per il mining.");
        } else if (minerInstance != null && minerInstance.getChainId() != null && !minerInstance.getChainId().isEmpty()) {
            chainIdLabel.setText(minerInstance.getChainId());
            log("Dati e specifiche caricati dall'XML locale. Pronto per il mining.");
        } else {
            chainIdLabel.setText("N/A");
            log("Attenzione: Specifiche della chain mancanti.");
        }

        // 4. Aggiornamento degli elementi grafici e bottoni
        updatePlotSizeInfo();

        // Controllo prima lo stato fisico del file di plot su disco
        updateButtonsState();

        // Verifico se il manager sta già facendo girare questo miner in background
        if (minerInstance != null) {
            boolean running = MinerManager.getInstance().isMinerRunning(minerInstance.getUuid());

            if (running) {
                // Se sta già girando, aggancio il riferimento al servizio core
                this.miner = MinerManager.getInstance().getActiveService(minerInstance.getUuid());

                // Instrado i log del miner nella console di QUESTA finestra
                attachListener();

                // Aggiorno la UI di conseguenza: Start spento, Stop acceso, barra in
                // modalità indeterminata (l'animazione "rimbalzante" che indica mining attivo).
                startMiningButton.setDisable(true);
                stopMiningButton.setDisable(false);
                progressBar.setProgress(-1.0);
                statusLabel.setText("Status: Mining attivo in background...");
                log("Sincronizzato con il processo in background attivo.");
            }
        }

        // 5. Saldo: una SOLA chiamata automatica la prima volta (dopo l'aggancio, percorso
        //    veloce); riaprire la console mostra il valore in cache senza richiamare il nodo.
        //    Gli aggiornamenti successivi avvengono solo con "Aggiorna".
        if (minerInstance != null) {
            BigInteger cached = MinerManager.getInstance().getCachedBalance(minerInstance.getUuid());
            if (cached != null) {
                balanceLabel.setText(cached + " MOK");
            } else if (miningSpecification != null || minerInstance.getNodeUri() != null) {
                updateWalletBalance();
            }
        }
    }

    /**
     * Abilita o disabilita i pulsanti a seconda della presenza fisica del file plot su disco.
     */
    private void updateButtonsState() {
        String currentPlotPath = (minerData != null && minerData.getPlotPath() != null) ? minerData.getPlotPath() :
                (minerService != null ? minerService.getPlotPath() : "");

        File plotFile = new File(currentPlotPath);
        boolean exists = !currentPlotPath.isEmpty() && plotFile.exists();

        if (exists) {
            sizeInputBox.setVisible(false);
            sizeInputBox.setManaged(false);
            btnCreatePlot.setDisable(true);
            startMiningButton.setDisable(false);
            statusLabel.setText("Status: Plot pronto per il mining.");
        } else {
            sizeInputBox.setVisible(true);
            sizeInputBox.setManaged(true);
            btnCreatePlot.setDisable(false);
            startMiningButton.setDisable(true);
            statusLabel.setText("Status: Inserisci la dimension per generare il plot.");
        }
    }

    /**
     * Gestisce la creazione indipendente del file di Plot ricostruendo i Prolog dall'XML se offline.
     */
    @FXML
    private void handleCreatePlot(){
        if (plotTask != null && plotTask.isRunning()) return;

        if (miningSpecification == null && minerData == null) {
            log("Errore: Specifiche del server e locali mancanti!");
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

                javafx.application.Platform.runLater(() -> {
                    logArea.appendText("[PLOT] Avvio creazione plot deterministico...\n");
                    logArea.appendText("[PLOT] Dimensione stimata: " + sizeInMB + " MB\n");
                });

                // Estrazione parametri sicura (Usa l'XML locale se la specifica di rete è null)
                String chainId = (miningSpecification != null) ? miningSpecification.getChainId() : minerData.getChainId();

                // 1. FIRMA BLOCCHI: Trasforma es. "ed25519" -> "ED25519"
                String sigBlocksStr = (miningSpecification != null) ? miningSpecification.getSignatureForBlocks().toString() : minerData.getSignatureForBlocks();
                sigBlocksStr = (sigBlocksStr != null) ? sigBlocksStr.trim().toUpperCase() : "ED25519";
                var signatureForBlocks = io.hotmoka.crypto.SignatureAlgorithms.of(sigBlocksStr);

                // 2. FIRMA DEADLINES: Trasforma es. "ed25519" -> "ED25519"
                String sigDeadlinesStr = (miningSpecification != null) ? miningSpecification.getSignatureForDeadlines().toString() : minerData.getSignatureForDeadlines();
                sigDeadlinesStr = (sigDeadlinesStr != null) ? sigDeadlinesStr.trim().toUpperCase() : "ED25519";
                var signatureForDeadlines = io.hotmoka.crypto.SignatureAlgorithms.of(sigDeadlinesStr);

                // Recupero della chiave pubblica del server
                var publicKeyOfServer = (miningSpecification != null) ? miningSpecification.getPublicKeyForSigningBlocks() :
                        signatureForBlocks.publicKeyFromEncoding(io.hotmoka.crypto.Base58.fromBase58String(minerData.getPublicKeyBlocksBase58()));

                // 3. HASHING DEADLINES: Trasforma "shabal256" dell'XML in "SHABAL256" (Risolve il NullPointerException!)
                String hashStr = (miningSpecification != null) ? miningSpecification.getHashingForDeadlines().toString() : minerData.getHashingForDeadlines();
                hashStr = (hashStr != null) ? hashStr.trim().toUpperCase() : "SHABAL256";
                var hashingForDeadlines = io.hotmoka.crypto.HashingAlgorithms.of(hashStr);

                Prolog prolog = Prologs.of(
                        chainId,
                        signatureForBlocks,
                        publicKeyOfServer,
                        signatureForDeadlines,
                        userKeys.getPublic(),
                        new byte[0]
                );

                String finalPlotPath = (minerData != null) ? minerData.getPlotPath() : minerService.getPlotPath();
                Path path = Paths.get(finalPlotPath);

                Plots.create(
                        path,
                        prolog,
                        0L,
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

        progressBar.progressProperty().bind(plotTask.progressProperty());
        statusLabel.textProperty().bind(plotTask.messageProperty());

        plotTask.setOnSucceeded(e -> {
            unbindUI();
            log("Plot completato con successo! " + size + " nonces creati.");

            try {
                if (minerData != null) {
                    minerData.setPlotSize(size);
                    MinerXmlManager.addMiner(minerData);
                    log("File XML aggiornato correttamente con la dimensione del plot.");
                } else {
                    log("Avviso: Impossibile aggiornare l'XML, dati del miner non associati.");
                }
            } catch (Exception ex) {
                System.err.println("Errore salvataggio XML: " + ex.getMessage());
            }

            statusLabel.setText("Status: Plot pronto.");
            updatePlotSizeInfo();
            updateButtonsState();
        });

        plotTask.setOnFailed(e -> {
            unbindUI();
            Throwable ex = plotTask.getException();
            log("Errore durante il plotting: " + (ex != null ? ex.getMessage() : "Sconosciuto"));
            statusLabel.setText("Status: Errore creazione.");
            if (ex != null) ex.printStackTrace();
        });

        new Thread(plotTask).start();
    }

    @FXML
    private void onStartMining() {
        log("Richiesta avvio sessione di mining al Manager...");

        if (minerData == null) {
            log("Errore: Impossibile avviare, dati del miner (XML) mancanti.");
            return;
        }

        String uuid = minerData.getUuid();

        // L'avvio carica il plot e apre la connessione di rete: operazioni bloccanti
        // che NON devono girare sul JavaFX Application Thread: le sposto su un Task.
        progressBar.setProgress(-1.0);
        statusLabel.setText("Status: Inizializzazione...");
        startMiningButton.setDisable(true);

        Task<DesktopMinerService> startTask = new Task<>() {
            @Override
            protected DesktopMinerService call() {
                if (!MinerManager.getInstance().isMinerRunning(uuid)) {
                    MinerManager.getInstance().startMinerInBackground(minerData, userKeys);
                }
                return MinerManager.getInstance().getActiveService(uuid);
            }
        };

        startTask.setOnSucceeded(e -> {
            this.miner = startTask.getValue();
            if (this.miner != null) {
                // Avvio esplicito dell'utente: il miner torna "attivo" e ripartirà ai riavvii.
                MinerXmlManager.setActive(uuid, true);
                attachListener();
                stopMiningButton.setDisable(false);
                statusLabel.setText("Status: Mining attivo in background...");
                log("Mining avviato e sincronizzato con la centrale operativa!");
            } else {
                startMiningButton.setDisable(false);
                log("Errore: Il Manager non è riuscito a far partire il servizio.");
                statusLabel.setText("Status: Errore");
                progressBar.setProgress(0.0);
            }
        });

        startTask.setOnFailed(e -> {
            startMiningButton.setDisable(false);
            Throwable ex = startTask.getException();
            log("Errore fatale all'avvio: " + (ex != null ? ex.getMessage() : "Sconosciuto"));
            statusLabel.setText("Status: Errore");
            if (ex != null) ex.printStackTrace();
            progressBar.setProgress(0.0);
        });

        new Thread(startTask).start();
    }

    @FXML
    private void onStopMining() {
        log("Richiesta spegnimento sessione di mining al Manager...");

        if (minerData != null) {
            String uuid = minerData.getUuid();

            // Chiedo al Manager di fermare il thread in background e rimuoverlo dalla mappa
            MinerManager.getInstance().stopMiner(uuid);
        }

        // Pulisco il riferimento locale della finestra
        this.miner = null;

        // Aggiorno lo stato della UI
        startMiningButton.setDisable(false);
        stopMiningButton.setDisable(true);
        log("Mining fermato sul manager centrale e risorse liberate.");
        progressBar.setProgress(0.0);
        statusLabel.setText("Status: Pronto");
    }

    private void unbindUI() {
        progressBar.progressProperty().unbind();
        statusLabel.textProperty().unbind();
    }

    /** Costruisce il listener che instrada gli eventi del miner nella console di QUESTA finestra. */
    private DesktopMinerService.MinerListener buildUiListener() {
        return new DesktopMinerService.MinerListener() {
            @Override public void onConnected() {
                Platform.runLater(() -> statusLabel.setText("Status: Connesso, mining attivo..."));
                log("Connesso al nodo: mining in corso.");
            }
            @Override public void onDisconnected() {
                Platform.runLater(() -> statusLabel.setText("Status: Disconnesso, riconnessione automatica..."));
                log("Connessione persa: tentativo di riconnessione automatica...");
            }
            @Override public void onDeadline(int totalDeadlines) {
                log("Deadline #" + totalDeadlines + " inviata al nodo.");
            }
            @Override public void onMessage(String msg) {
                log(msg);
            }
        };
    }

    /** Aggancia la console al servizio attivo per ricevere i log in tempo reale. */
    private void attachListener() {
        if (miner != null) {
            miner.setListener(buildUiListener());
            log("Console agganciata al miner: ricezione log attiva.");
        }
    }

    /** Chiamato alla chiusura della finestra console: sgancia il listener dal servizio. */
    public void onWindowClosed() {
        if (miner != null) {
            miner.setListener(null);
        }
    }

    @FXML
    private void handleRefreshBalance() {
        updateWalletBalance();
    }

    private void log(String message) {
        Platform.runLater(() -> {
            logArea.appendText("[" + java.time.LocalTime.now().withNano(0) + "] " + message + "\n");
        });
    }

    private void updatePlotSizeInfo() {
        try {
            String pathStr = (minerData != null) ? minerData.getPlotPath() : minerService.getPlotPath();
            if (pathStr == null || pathStr.isEmpty()) {
                plotSizeLabel.setText("File non ancora generato");
                return;
            }

            File plotFile = new File(pathStr);
            if (plotFile.exists()) {
                long fileBytes = plotFile.length();
                long nonces = fileBytes / 262144;
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

    private void updateWalletBalance() {
        if (userKeys == null || (miningSpecification == null && minerData == null)) return;

        // Per leggere il saldo "offline" bisogna costruire un servizio sul plot: se il plot
        // non esiste ancora (miner non plottato) evito la FileNotFoundException ed esco.
        if (miner == null) {
            String currentPlotPath = (minerData != null) ? minerData.getPlotPath()
                    : (minerService != null ? minerService.getPlotPath() : null);
            if (currentPlotPath == null || !new File(currentPlotPath).exists()) {
                balanceLabel.setText("N/D (plot non generato)");
                return;
            }
        }

        log("Aggiornamento saldo in corso...");
        balanceLabel.setText("Aggiornamento...");

        Task<Optional<BigInteger>> balanceTask = new Task<>() {
            @Override
            protected Optional<BigInteger> call() throws Exception {
                String sigBlocksStr = (miningSpecification != null) ? miningSpecification.getSignatureForBlocks().toString() : minerData.getSignatureForBlocks();
                var signatureAlgo = io.hotmoka.crypto.SignatureAlgorithms.of(sigBlocksStr);

                String nodeUri = (minerData != null) ? minerData.getNodeUri() : minerService.getNodeUri();
                String chainId = (minerData != null) ? minerData.getChainId() : minerService.getChainID();
                String plotPath = (minerData != null) ? minerData.getPlotPath() : minerService.getPlotPath();

                if (miner != null) {
                    return miner.getBalance(signatureAlgo, userKeys.getPublic());
                } else {
                    try (DesktopMinerService tempService = new DesktopMinerService(
                            URI.create(nodeUri), chainId,
                            Path.of(plotPath), userKeys, new DesktopMinerService.MinerListener() {
                        @Override public void onConnected() {}
                        @Override public void onDisconnected() {}
                        @Override public void onDeadline(int totalDeadlines) {}
                        @Override public void onMessage(String message) {}
                    })) {
                        return tempService.getBalance(signatureAlgo, userKeys.getPublic());
                    }
                }
            }
        };

        balanceTask.setOnSucceeded(e -> {
            Optional<BigInteger> balanceOpt = balanceTask.getValue();
            BigInteger value = balanceOpt.orElse(BigInteger.ZERO);
            if (balanceOpt.isPresent()) {
                balanceLabel.setText(value + " MOK");
                log("Saldo aggiornato: " + value + " MOK");
            } else {
                balanceLabel.setText("0 MOK (Nuovo Account)");
                log("Saldo aggiornato: 0 MOK (nuovo account).");
            }
            // Aggiorno la cache così le riaperture della console non richiamano il nodo.
            if (minerData != null) {
                MinerManager.getInstance().setCachedBalance(minerData.getUuid(), value);
            }
        });

        balanceTask.setOnFailed(e -> {
            balanceLabel.setText("Errore bilancio");
            Throwable ex = balanceTask.getException();
            log("Errore durante l'aggiornamento del saldo: " + (ex != null ? ex.getMessage() : "sconosciuto"));
            if (ex != null) ex.printStackTrace();
        });

        new Thread(balanceTask).start();
    }
}