package it.univr.mokamintminer.gui;

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
import java.util.concurrent.atomic.AtomicBoolean;

public class GUIController {

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

    // PLOT
    private Task<Void> plotTask;
    private Thread plotThread;

    // MINING
    private DesktopMinerService miner;

    private final java.util.concurrent.atomic.AtomicBoolean simulationActive = new AtomicBoolean(false);


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
                );*/

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
        if (plotTask != null && plotTask.isRunning())
            return;  // Mining disabilitato durante il plot

        if (miner != null) {
            statusLabel.setText("Status: mining already running");
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

        // Reset contatore deadlines ad ogni nuova sessione di mining
        updateDeadlinesLabel(0);

        try {
            miner = new DesktopMinerService(
                    endpointUri,
                    Path.of(plotFile),
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

            log(" Starting mining...");
            statusLabel.setText("Status: Mining started");
            startSimulationIfNoConnection();

        } catch (Exception e) {
            statusLabel.setText("Error: " + e.getMessage());
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

    private void startSimulationIfNoConnection() {
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
    }

}

