package it.univr.mokamintminer.gui;

import io.hotmoka.crypto.Entropies;
import io.mokamint.miner.api.MiningSpecification;
import it.univr.mokamintminer.core.DesktopMinerService;
import it.univr.mokamintminer.services.MinerInstance;
import it.univr.mokamintminer.services.MinerManager;
import it.univr.mokamintminer.services.MinerService;
import it.univr.mokamintminer.services.MinerXmlManager;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.stage.Stage;
import javafx.util.Duration;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.KeyPair;
import java.util.List;

public class MinerManagerController {

    @FXML
    private ListView<MinerInstance> minerListView;

    @FXML
    private Button addMinerButton;

    private final ObservableList<MinerInstance> minersList = FXCollections.observableArrayList();
    private Timeline ledRefresher;
    private String lastLedSignature = "";

    @FXML
    public void initialize() {
        // 1. Colleghiamo la lista dei dati alla ListView grafica
        minerListView.setItems(minersList);

        // 2. Configuriamo la Cell Factory per iniettare la grafica personalizzata
        minerListView.setCellFactory(param -> new ListCell<>() {
            @Override
            protected void updateItem(MinerInstance miner, boolean empty) {
                super.updateItem(miner, empty);

                if (empty || miner == null) {
                    setText(null);
                    setGraphic(null);
                } else {
                    HBox cardLayout = new HBox(15);
                    cardLayout.setAlignment(Pos.CENTER_LEFT);
                    cardLayout.setPadding(new Insets(10, 15, 10, 15));
                    cardLayout.setStyle("-fx-background-color: #2a2a35; -fx-background-radius: 8; -fx-border-color: #3a3a45; -fx-border-radius: 8;");

                    Circle led = new Circle(8);
                    // Feedback reale a quattro stati
                    boolean running = MinerManager.getInstance().isMinerRunning(miner.getUuid());
                    boolean connected = MinerManager.getInstance().isMinerConnected(miner.getUuid());
                    File plotFile = new File(miner.getPlotPath() != null ? miner.getPlotPath() : "");
                    if (running && connected) {
                        led.setFill(Color.web("#2ec4b6")); // Verde  -> connesso e in mining
                    } else if (running) {
                        led.setFill(Color.web("#e71d36")); // Rosso  -> attivo ma disconnesso (rete giù / errore)
                    } else if (plotFile.exists()) {
                        led.setFill(Color.web("#ffb627")); // Giallo -> plot pronto, miner fermo
                    } else {
                        led.setFill(Color.web("#6c757d")); // Grigio -> da plottare
                    }

                    VBox textLayout = new VBox(4);
                    HBox nameLayout = new HBox(10);
                    nameLayout.setAlignment(Pos.CENTER_LEFT);

                    Label nameLabel = new Label(miner.getName());
                    nameLabel.setStyle("-fx-text-fill: #e2dbf0; -fx-font-weight: bold; -fx-font-size: 14px;");

                    Button renameBtn = new Button("✏");
                    renameBtn.setStyle("-fx-background-color: transparent; -fx-text-fill: #9d9da4; -fx-cursor: hand; -fx-padding: 0;");
                    renameBtn.setOnAction(e -> handleRenameMiner(miner));

                    nameLayout.getChildren().addAll(nameLabel, renameBtn);

                    Label infoLabel = new Label("UUID: " + miner.getUuid() + "  |  Nodo: " + miner.getNodeUri());
                    infoLabel.setStyle("-fx-text-fill: #9d9da4; -fx-font-size: 11px;");

                    textLayout.getChildren().addAll(nameLayout, infoLabel);
                    HBox.setHgrow(textLayout, Priority.ALWAYS);

                    Button consoleBtn = new Button("Apri Console");
                    consoleBtn.setStyle("-fx-background-color: #8a63d2; -fx-text-fill: white; -fx-cursor: hand; -fx-background-radius: 4;");
                    consoleBtn.setOnAction(e -> handleOpenMinerConsole(miner));

                    Button deleteBtn = new Button("Elimina");
                    deleteBtn.setStyle("-fx-background-color: #e71d36; -fx-text-fill: white; -fx-cursor: hand; -fx-background-radius: 4;");
                    deleteBtn.setOnAction(e -> handleDeleteMiner(miner));

                    cardLayout.getChildren().addAll(led, textLayout, consoleBtn, deleteBtn);
                    setGraphic(cardLayout);
                }
            }

        });
        // 3. Carichiamo i veri miner salvati nell'XML all'avvio dell'applicazione
        loadMinersFromXML();

        // 4. Avvio automatico in background di tutti i miner con plot pronto.
        //    Gira su un thread dedicato; al termine aggiorniamo i LED delle card.
        MinerManager.getInstance().autoStartAllMiners(
                () -> Platform.runLater(minerListView::refresh));

        // 5. Aggiornamento dei LED senza sfarfallio: ridisegniamo le card SOLO quando
        //    lo stato di almeno un miner cambia davvero (connesso/disconnesso/plot),
        //    non a ogni tick.
        ledRefresher = new Timeline(new KeyFrame(Duration.seconds(2), e -> {
            String sig = computeLedSignature();
            if (!sig.equals(lastLedSignature)) {
                lastLedSignature = sig;
                minerListView.refresh();
            }
        }));
        ledRefresher.setCycleCount(Timeline.INDEFINITE);
        ledRefresher.play();
    }

    /**
     * Firma compatta dello stato dei LED. Cambia solo quando un miner cambia stato
     * (attivo/connesso) o quando compare/sparisce il suo plot: serve a ridisegnare
     * le card solo allora, evitando lo sfarfallio del refresh continuo.
     */
    private String computeLedSignature() {
        MinerManager mgr = MinerManager.getInstance();
        StringBuilder sb = new StringBuilder();
        for (MinerInstance m : minersList) {
            sb.append(m.getUuid())
              .append(mgr.isMinerRunning(m.getUuid()) ? '1' : '0')
              .append(mgr.isMinerConnected(m.getUuid()) ? '1' : '0')
              .append(new File(m.getPlotPath() != null ? m.getPlotPath() : "").exists() ? 'P' : 'n')
              .append(';');
        }
        return sb.toString();
    }

    private void loadMinersFromXML() {
        try {
            minersList.clear();
            // Sostituito getMiners() con il tuo loadMiners() reale
            List<MinerInstance> savedMiners = MinerXmlManager.loadMiners();
            minersList.addAll(savedMiners);
            System.out.println("XML caricato con successo. Trovati " + savedMiners.size() + " miner.");
        } catch (Exception e) {
            System.err.println("Errore nel caricamento del file XML: " + e.getMessage());
        }
    }

    @FXML
    private void handleCreateNewMiner() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/layout/connection.fxml"));
            Parent root = loader.load();

            Stage stage = new Stage();
            stage.setTitle("Configurazione Nuovo Miner");
            stage.setScene(new Scene(root, 640, 540));
            stage.setMinWidth(560);
            stage.setMinHeight(480);
            stage.initOwner(addMinerButton.getScene().getWindow());

            // Quando la finestra di creazione guidata viene chiusa, aggiorna automaticamente la lista
            stage.setOnHidden(e -> loadMinersFromXML());

            stage.show();

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void handleRenameMiner(MinerInstance miner) {
        TextInputDialog dialog = new TextInputDialog(miner.getName());
        dialog.setTitle("Rinomina Miner");
        dialog.setHeaderText("Cambia il nome per identificare questo miner:");
        dialog.setContentText("Nuovo Nome:");

        dialog.showAndWait().ifPresent(newName -> {
            if (!newName.isBlank()) {
                try {
                    miner.setName(newName.trim());
                    // Aggiorna l'XML richiamando l'addMiner che si occupa della sovrascrittura/aggiornamento
                    MinerXmlManager.addMiner(miner);
                    minerListView.refresh();
                    System.out.println("Nome modificato con successo nell'XML.");
                } catch (Exception ex) {
                    System.err.println("Errore durante l'aggiornamento del nome nel file XML: " + ex.getMessage());
                }
            }
        });
    }

    private void handleOpenMinerConsole(MinerInstance miner) {
        System.out.println("Apertura dashboard di mining per l'UUID: " + miner.getUuid());

        try {
            // 1. Ricostruiamo i percorsi locali standard legandoli all'UUID del miner
            if (miner.getPlotPath() == null || miner.getPlotPath().isBlank()) {
                miner.setPlotPath("miner_storage/data/" + miner.getUuid() + ".plot");
            }
            if (miner.getPemPath() == null || miner.getPemPath().isBlank()) {
                miner.setPemPath("miner_storage/identities/" + miner.getUuid() + ".pem");
            }

            // 2. Carichiamo l'identità crittografica dal file .pem
            Path correctPemPath = Paths.get(miner.getPemPath());
            var entropy = Entropies.load(correctPemPath);

            // 3. Recuperiamo l'algoritmo di firma letto in precedenza dall'XML
            String sigForDeadlinesStr = miner.getSignatureForDeadlines();
            var signatureForDeadlines = io.hotmoka.crypto.SignatureAlgorithms.of(sigForDeadlinesStr);

            // 4. Rigeneriamo la KeyPair in memoria e passiamola al modello
            KeyPair userKeys = entropy.keys("", signatureForDeadlines);
            miner.setKeyPair(userKeys);

            // 5. Creiamo il wrapper di servizio finto
            MinerService serviceWrapper = new MinerService();

            // 6. Carichiamo l'interfaccia grafica del terminale di mining
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/layout/mining.fxml"));
            Parent root = loader.load();

            // Passiamo i parametri configurati: adesso miner ha i percorsi validi!
            MiningController miningController = loader.getController();
            miningController.setMiningData(serviceWrapper, userKeys, null, miner);

            // 7. Visualizziamo la finestra della console
            Stage consoleStage = new Stage();
            consoleStage.setTitle("Console di Mining - " + miner.getName());

            // Stessa identica modalità delle finestre che funzionano (principale, wizard):
            // dimensione ESPLICITA nel costruttore della Scene + owner, SENZA
            // setWidth/setHeight/centerOnScreen (che su questo window manager non vengono
            // rispettati sulle finestre secondarie).
            // Stesso identico schema di connessione/login (che funzionano): su questo window
            // manager la dimensione della finestra coincide col minimo, quindi impostiamo
            // minimo = dimensione voluta. La console si apre a 1200x780, è ingrandibile e
            // (come tutte le altre finestre) non riducibile sotto tale soglia.
            // NIENTE initOwner: altrimenti la console resterebbe sempre davanti alla principale.
            consoleStage.setScene(new Scene(root, 1200, 780));
            consoleStage.setMinWidth(1200);
            consoleStage.setMinHeight(780);
            // Alla chiusura: sgancia il listener dal servizio e aggiorna i LED
            consoleStage.setOnHidden(e -> {
                miningController.onWindowClosed();
                minerListView.refresh();
            });
            consoleStage.show();

        } catch (Exception e) {
            e.printStackTrace();
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle("Errore");
            alert.setContentText("Impossibile aprire la console: " + e.getMessage());
            alert.showAndWait();
        }
    }

    private void handleDeleteMiner(MinerInstance miner) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("Elimina Miner");
        alert.setHeaderText("Stai per rimuovere il miner: " + miner.getName());
        alert.setContentText("Il file di plot (.plot) verrà rimosso per liberare spazio. Vuoi conservare il file delle chiavi (.pem) per i tuoi fondi?");

        ButtonType yesKeepPem = new ButtonType("Conserva Identità (.pem)");
        ButtonType deleteAll = new ButtonType("Elimina Tutto");
        ButtonType cancel = new ButtonType("Annulla", ButtonBar.ButtonData.CANCEL_CLOSE);

        alert.getButtonTypes().setAll(yesKeepPem, deleteAll, cancel);

        alert.showAndWait().ifPresent(type -> {
            try {
                if (type == yesKeepPem) {
                    System.out.println("Elimino plot ed XML, ma salvo il .pem per l'UUID: " + miner.getUuid());

                    // Eliminiamo fisicamente il file di plot se esiste
                    File plotFile = new File(miner.getPlotPath());
                    if (plotFile.exists()) plotFile.delete();

                    // Rimuoviamo il miner dall'XML
                    MinerXmlManager.removeMiner(miner.getUuid());
                    minersList.remove(miner);

                } else if (type == deleteAll) {
                    System.out.println("Rado al suolo file e identità per l'UUID: " + miner.getUuid());

                    // Eliminiamo il file di plot
                    File plotFile = new File(miner.getPlotPath());
                    if (plotFile.exists()) plotFile.delete();

                    // Eliminiamo il file PEM dell'identità
                    File pemFile = new File(miner.getPemPath());
                    if (pemFile.exists()) pemFile.delete();

                    // Rimuoviamo il miner dall'XML
                    MinerXmlManager.removeMiner(miner.getUuid());
                    minersList.remove(miner);
                }
            } catch (Exception ex) {
                System.err.println("Errore durante la cancellazione del miner: " + ex.getMessage());
            }
        });
    }
}