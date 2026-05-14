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

    private final MinerListener listener;
    private final AtomicInteger deadlines = new AtomicInteger(0);

    public interface MinerListener {
        void onConnected();
        void onDisconnected();
        void onDeadline(int totalDeadlines);
        void onMessage(String msg);
    }

    public DesktopMinerService(URI endpoint, /*String chainId,*/ Path plotPath, KeyPair keys, MinerListener listener) throws Exception {
        super(
                Optional.of(
                        LocalMiners.of(
                                "mokamint",
                                "Miner GUI",
                                (signature, publicKey) -> Optional.of(new java.math.BigInteger(1, keys.getPrivate().getEncoded())),
                                Plots.load(plotPath)
                        )
                ),
                endpoint,
                30000,
                30000
        );

        this.listener = listener;
    }

    // Metodo helper per loggare ovunque
    private void internalLog(String msg) {
        System.out.println("[DesktopMiner] " + msg); // Terminale
        if (listener != null) listener.onMessage(msg); // GUI
    }

    @Override
    protected void onConnected() {
        super.onConnected();
        internalLog("Connessione stabilita con il nodo.");

        if (listener != null)
            listener.onConnected();
    }

    @Override
    protected void onDisconnected() {
        super.onDisconnected();
        internalLog("Connessione interrotta.");

        if (listener != null)
            listener.onDisconnected();
    }

    @Override
    protected void onDeadlineComputed(Deadline deadline) {
        super.onDeadlineComputed(deadline);
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
