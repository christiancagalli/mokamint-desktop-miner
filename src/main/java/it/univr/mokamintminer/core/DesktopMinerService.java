package it.univr.mokamintminer.core;

import io.mokamint.miner.local.LocalMiners;
import io.mokamint.miner.service.AbstractReconnectingMinerService;
import io.mokamint.nonce.api.Deadline;
import io.mokamint.plotter.Plots;

import java.net.URI;
import java.nio.file.Path;
import java.security.KeyPair;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

public class DesktopMinerService extends AbstractReconnectingMinerService {

    // Listener verso la UI: può essere sostituito a runtime quando si apre/chiude
    // la console di un miner, così i log finiscono nella logArea della finestra giusta.
    private volatile MinerListener listener;
    private final AtomicInteger deadlines = new AtomicInteger(0);

    // Storico recente dei messaggi: viene "riascoltato" quando si apre/riapre una
    // console, così la finestra mostra subito le ultime righe invece di partire vuota.
    private static final int MAX_HISTORY = 300;
    private final java.util.concurrent.ConcurrentLinkedDeque<String> history = new java.util.concurrent.ConcurrentLinkedDeque<>();

    // Heartbeat passivo: l'ultima volta che è arrivata attività dal nodo (challenge/deadline).
    // Serve per accorgersi di una caduta di rete anche quando la libreria non aggiorna
    // subito il proprio stato di connessione. Soglia ampia perché tra un blocco e l'altro
    // possono passare normalmente fino a ~90s senza challenge.
    private volatile long lastActivityMillis = System.currentTimeMillis();
    private static final long STALL_THRESHOLD_MS = 120_000;

    /** True se c'è stata attività dal nodo entro la soglia (heartbeat). */
    public boolean isActive() {
        return System.currentTimeMillis() - lastActivityMillis < STALL_THRESHOLD_MS;
    }

    // Watchdog: rileva il blocco (assenza di attività) e lo segnala in console DURANTE
    // l'outage, senza aspettare che la libreria se ne accorga al ritorno della rete.
    private volatile boolean watchdogRunning = true;

    private void startWatchdog() {
        Thread wd = new Thread(() -> {
            boolean wasStalled = false;
            while (watchdogRunning) {
                try {
                    Thread.sleep(5000);
                } catch (InterruptedException e) {
                    break;
                }
                boolean stalled = !isActive();
                if (stalled && !wasStalled) {
                    internalLog("Nessuna risposta dal nodo da oltre 2 minuti: possibile caduta di rete, in attesa di riconnessione automatica...");
                }
                wasStalled = stalled;
            }
        }, "miner-watchdog");
        wd.setDaemon(true);
        wd.start();
    }

    @Override
    public void close() {
        watchdogRunning = false;
        super.close();
    }

    public interface MinerListener {
        void onConnected();
        void onDisconnected();
        void onDeadline(int totalDeadlines);
        void onMessage(String msg);
    }

    /**
     * Aggancia/sostituisce il listener della UI (null per sganciare la console).
     * Appena agganciato gli vengono "riascoltati" i messaggi recenti dallo storico,
     * così la console non parte vuota.
     */
    public void setListener(MinerListener listener) {
        this.listener = listener;
        if (listener != null) {
            for (String past : history) {
                listener.onMessage(past);
            }
        }
    }

    // NB: isConnected() NON va sovrascritto: ereditiamo quello della libreria, che
    // legge lo stato reale della connessione (l'AtomicBoolean del reconnector).

    public DesktopMinerService(URI endpoint, String chainId, Path plotPath, KeyPair keys, MinerListener listener) throws Exception {
        super(
                Optional.of(
                        LocalMiners.of(
                                chainId,
                                "Miner GUI",
                                (signature, publicKey) -> {
                                    try {
                                        byte[] rawPriv = keys.getPrivate().getEncoded();

                                        return Optional.of(new java.math.BigInteger(1, rawPriv));
                                    } catch (Exception e) {
                                        System.out.println("[DesktopMiner] Errore estrazione chiave privata: " + e.getMessage());
                                        return Optional.empty();
                                    }
                                },
                                Plots.load(plotPath)
                        )
                ),
                endpoint,
                30000,  // timeout connessione (ms)
                5000    // retryInterval: ogni 5s ricontrolla/ritenta -> feedback più rapido
        );

        this.listener = listener;
        startWatchdog();
    }

    // Metodo helper per loggare ovunque
    private void internalLog(String msg) {
        System.out.println("[DesktopMiner] " + msg); // Terminale
        history.addLast(msg);
        while (history.size() > MAX_HISTORY) history.pollFirst();
        MinerListener l = listener;
        if (l != null) l.onMessage(msg); // GUI
    }

    @Override
    protected void onConnected() {
        super.onConnected();
        lastActivityMillis = System.currentTimeMillis();
        internalLog("Connessione stabilita con il nodo.");

        MinerListener l = listener;
        if (l != null) l.onConnected();
    }

    @Override
    protected void onDeadlineRequested(io.mokamint.nonce.api.Challenge challenge) {
        super.onDeadlineRequested(challenge);
        lastActivityMillis = System.currentTimeMillis(); // heartbeat: challenge ricevuto
    }

    @Override
    protected void onDisconnected() {
        super.onDisconnected();
        internalLog("Connessione interrotta. Tentativo di riconnessione automatica in corso...");

        MinerListener l = listener;
        if (l != null) l.onDisconnected();
    }

    @Override
    protected void onConnectionFailed(io.hotmoka.websockets.api.FailedDeploymentException e) {
        super.onConnectionFailed(e);
        internalLog("Riconnessione fallita (" + e.getMessage() + "): nuovo tentativo a breve...");

        MinerListener l = listener;
        if (l != null) l.onDisconnected();
    }

    @Override
    protected void onDeadlineComputed(Deadline deadline) {
        super.onDeadlineComputed(deadline);
        lastActivityMillis = System.currentTimeMillis(); // heartbeat: deadline calcolata
        int total = deadlines.incrementAndGet();
        String deadlineValue = io.hotmoka.crypto.Hex.toHexString(deadline.getValue());
        internalLog("Nuova deadline calcolata: " + deadlineValue);

        if (listener != null)
            listener.onDeadline(total);
    }

    public int getDeadlines() {
        return deadlines.get();
    }

    public void resetDeadlines() {
        deadlines.set(0);
    }

}
