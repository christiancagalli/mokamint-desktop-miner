package it.univr.mokamintminer.services;

import it.univr.mokamintminer.core.DesktopMinerService;
import java.io.File;
import java.math.BigInteger;
import java.net.URI;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.KeyPair;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class MinerManager {

    private static MinerManager instance;
    // Mappa che tiene in memoria i processi di mining attivi in background contemporaneamente
    private final Map<String, DesktopMinerService> activeMiners = new ConcurrentHashMap<>();
    // Ultimo saldo noto per miner: evita di richiamare il nodo a ogni apertura della console.
    private final Map<String, BigInteger> lastBalances = new ConcurrentHashMap<>();

    private MinerManager() {}

    public static synchronized MinerManager getInstance() {
        if (instance == null) {
            instance = new MinerManager();
        }
        return instance;
    }

    /**
     * Cicla su tutti i miner salvati nell'XML e li avvia in background se il plot esiste.
     * L'intero ciclo gira su un thread dedicato perché la costruzione di un
     * {@link DesktopMinerService} carica il plot e apre il WebSocket verso il nodo:
     * operazioni bloccanti che NON devono mai girare sul JavaFX Application Thread.
     *
     * @param onComplete callback opzionale eseguito al termine del ciclo (es. refresh della UI).
     */
    public void autoStartAllMiners(Runnable onComplete) {
        Thread worker = new Thread(() -> {
            try {
                List<MinerInstance> savedMiners = MinerXmlManager.loadMiners();
                if (savedMiners == null) return;

                for (MinerInstance minerData : savedMiners) {
                    String uuid = minerData.getUuid();

                    // Rispetta lo stop esplicito dell'utente: i miner fermati restano fermi.
                    if (!minerData.isActive()) {
                        System.out.println("[MANAGER] Miner " + uuid + " fermato dall'utente: non avviato.");
                        continue;
                    }

                    File plotFile = new File(minerData.getPlotPath());
                    // Avvia in background solo se il file .plot esiste fisicamente sul disco!
                    if (plotFile.exists() && !activeMiners.containsKey(uuid)) {
                        System.out.println("[MANAGER] Rilevato miner pronto all'avvio automatico: " + uuid);

                        KeyPair keys = loadKeyPairForUuid(minerData);

                        if (keys != null) {
                            startMinerInBackground(minerData, keys);
                        } else {
                            System.err.println("[MANAGER] Chiavi non caricate: miner saltato " + uuid);
                        }
                    }
                }
            } catch (Exception e) {
                System.err.println("[MANAGER] Errore durante l'auto-start dei miner: " + e.getMessage());
            } finally {
                if (onComplete != null) onComplete.run();
            }
        }, "auto-start-miners");
        worker.setDaemon(true);
        worker.start();
    }

    /**
     * Avvia un singolo miner in background inserendolo nella mappa globale.
     * ATTENZIONE: è bloccante (carica il plot e apre la connessione di rete);
     * va invocato fuori dal JavaFX Application Thread.
     *
     * @return il servizio avviato (o quello già attivo), oppure {@code null} in caso di errore.
     */
    public DesktopMinerService startMinerInBackground(MinerInstance minerData, KeyPair keys) {
        String uuid = minerData.getUuid();
        DesktopMinerService existing = activeMiners.get(uuid);
        if (existing != null) return existing; // Già attivo

        try {
            URI uri = URI.create(minerData.getNodeUri());
            Path path = Paths.get(minerData.getPlotPath());

            DesktopMinerService backendService = new DesktopMinerService(
                    uri,
                    minerData.getChainId(),
                    path,
                    keys,
                    new DesktopMinerService.MinerListener() {
                        @Override public void onConnected() { System.out.println("[" + uuid + "] Connesso."); }
                        @Override public void onDisconnected() { System.out.println("[" + uuid + "] Disconnesso."); }
                        @Override public void onDeadline(int total) { System.out.println("[" + uuid + "] Deadline trovata: " + total); }
                        @Override public void onMessage(String msg) { System.out.println("[" + uuid + "] Log: " + msg); }
                    }
            );

            activeMiners.put(uuid, backendService);
            System.out.println("[MANAGER] Miner " + uuid + " avviato con successo in background.");
            return backendService;
        } catch (Exception e) {
            System.err.println("[MANAGER] Impossibile avviare il miner " + uuid + ": " + e.getMessage());
            return null;
        }
    }

    /**
     * Ferma un miner per azione ESPLICITA dell'utente: oltre a liberare le risorse,
     * persiste {@code active=false} così resta fermo anche dopo un riavvio dell'app.
     */
    public void stopMiner(String uuid) {
        stopService(uuid);
        MinerXmlManager.setActive(uuid, false);
    }

    /**
     * Ferma il servizio liberando le risorse, SENZA toccare il flag di avvio automatico.
     * Usato dalla chiusura dell'app, dove non vogliamo "disattivare" i miner.
     */
    private void stopService(String uuid) {
        DesktopMinerService service = activeMiners.remove(uuid);
        if (service != null) {
            service.close();
            System.out.println("[MANAGER] Miner " + uuid + " fermato e rimosso.");
        }
    }

    /**
     * Ferma TUTTI i miner attivi. Va invocato alla chiusura dell'applicazione:
     * le connessioni di mining girano su thread non-daemon, quindi senza questo
     * arresto la JVM resterebbe viva e i miner continuerebbero a girare.
     */
    public void stopAllMiners() {
        for (String uuid : new java.util.ArrayList<>(activeMiners.keySet())) {
            stopService(uuid); // chiusura app: non disattiva i miner
        }
    }

    public boolean isMinerRunning(String uuid) {
        return activeMiners.containsKey(uuid);
    }

    /**
     * True se il miner è "in salute": connesso secondo la libreria E con attività
     * recente dal nodo (heartbeat). Il secondo controllo intercetta le cadute di rete
     * brusche, che la libreria non segnala subito.
     */
    public boolean isMinerConnected(String uuid) {
        DesktopMinerService service = activeMiners.get(uuid);
        return service != null && service.isConnected() && service.isActive();
    }

    public DesktopMinerService getActiveService(String uuid) {
        return activeMiners.get(uuid);
    }

    /** Ultimo saldo letto per questo miner (null se non è mai stato interrogato). */
    public BigInteger getCachedBalance(String uuid) {
        return lastBalances.get(uuid);
    }

    public void setCachedBalance(String uuid, BigInteger balance) {
        if (balance != null) lastBalances.put(uuid, balance);
    }

    /**
     * Ricostruisce la KeyPair di un miner dal suo file .pem, usando lo STESSO metodo
     * del percorso "Apri Console" che funziona ({@code Entropies.load(...).keys(...)})
     * e leggendo l'algoritmo di firma dall'XML invece di assumerlo ed25519.
     */
    private KeyPair loadKeyPairForUuid(MinerInstance minerData) {
        try {
            Path pemPath = Paths.get(minerData.getPemPath());

            if (!java.nio.file.Files.exists(pemPath)) {
                System.err.println("[MANAGER] File .pem non trovato per il miner: " + minerData.getUuid());
                return null;
            }

            var entropy = io.hotmoka.crypto.Entropies.load(pemPath);

            // L'algoritmo è quello salvato nell'XML; fallback su ed25519 per XML legacy.
            String sigStr = minerData.getSignatureForDeadlines();
            if (sigStr == null || sigStr.isBlank()) sigStr = "ed25519";
            var signatureForDeadlines = io.hotmoka.crypto.SignatureAlgorithms.of(sigStr);

            // Password vuota: stessa scelta del resto dell'applicazione.
            return entropy.keys("", signatureForDeadlines);

        } catch (Exception e) {
            System.err.println("[MANAGER] Errore nel caricamento delle chiavi dal file .pem: " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }
}