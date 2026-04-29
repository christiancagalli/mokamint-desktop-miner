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
    }

    public DesktopMinerService(URI endpoint, Path plotPath, KeyPair keys, MinerListener listener) throws Exception {
        super(
                Optional.of(
                        LocalMiners.of(
                                "Desktop Miner",
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

    @Override
    protected void onConnected() {
        super.onConnected();

        if (listener != null)
            listener.onConnected();
    }

    @Override
    protected void onDisconnected() {
        super.onDisconnected();

        if (listener != null)
            listener.onDisconnected();
    }

    @Override
    protected void onDeadlineComputed(Deadline deadline) {
        super.onDeadlineComputed(deadline);
        int total = deadlines.incrementAndGet();

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
