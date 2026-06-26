package it.univr.mokamintminer.services;

import io.mokamint.miner.api.MiningSpecification;

public class MinerInstance {
    private final String uuid;
    private String name;
    private String nodeUri;

    // campi per mappare l'XML
    private long creationTimeUtc;
    private String chainId;
    private String hashingForDeadlines;
    private String signatureForBlocks;
    private String signatureForDeadlines;
    private String publicKeyBlocksBase58;
    private String publicKeyDeadlinesBase58;

    private MiningSpecification miningSpecification;

    // true = il miner deve partire automaticamente all'avvio dell'app.
    // Diventa false quando l'utente lo ferma esplicitamente, così resta fermo
    // anche dopo un riavvio (la chiusura dell'app NON modifica questo flag).
    private boolean active = true;

    // Costruttore
    public MinerInstance(String uuid, String name, String nodeUri) {
        this.uuid = uuid;
        this.name = name;
        this.nodeUri = nodeUri;
        this.creationTimeUtc = System.currentTimeMillis(); // Imposta il timestamp di creazione attuale
    }

    // GETTER E SETTER STANDARD
    public String getUuid() { return uuid; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getNodeUri() { return nodeUri; }
    public void setNodeUri(String nodeUri) { this.nodeUri = nodeUri; }

    public MiningSpecification getMiningSpecification() { return miningSpecification; }
    public void setMiningSpecification(MiningSpecification miningSpecification) {
        this.miningSpecification = miningSpecification;
    }

    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }

    // --- GETTER E SETTER PER I CAMPI XML ---
    public long getCreationTimeUtc() { return creationTimeUtc; }
    public void setCreationTimeUtc(long creationTimeUtc) { this.creationTimeUtc = creationTimeUtc; }

    public String getChainId() { return chainId; }
    public void setChainId(String chainId) { this.chainId = chainId; }

    public String getHashingForDeadlines() { return hashingForDeadlines; }
    public void setHashingForDeadlines(String hashingForDeadlines) { this.hashingForDeadlines = hashingForDeadlines; }

    public String getSignatureForBlocks() { return signatureForBlocks; }
    public void setSignatureForBlocks(String signatureForBlocks) { this.signatureForBlocks = signatureForBlocks; }

    public String getSignatureForDeadlines() { return signatureForDeadlines; }
    public void setSignatureForDeadlines(String signatureForDeadlines) { this.signatureForDeadlines = signatureForDeadlines; }

    public String getPublicKeyBlocksBase58() { return publicKeyBlocksBase58; }
    public void setPublicKeyBlocksBase58(String publicKeyBlocksBase58) { this.publicKeyBlocksBase58 = publicKeyBlocksBase58; }

    public String getPublicKeyDeadlinesBase58() { return publicKeyDeadlinesBase58; }
    public void setPublicKeyDeadlinesBase58(String publicKeyDeadlinesBase58) { this.publicKeyDeadlinesBase58 = publicKeyDeadlinesBase58; }

    // --- Utility per ricavare i path centralizzati basati sull'UUID ---

    // Restituisce il percorso del file .pem: "miner_storage/identities/uuid.pem"
    public String getPemPath() {
        return System.getProperty("user.dir") + java.io.File.separator +
                "miner_storage" + java.io.File.separator +
                "identities" + java.io.File.separator + uuid + ".pem";
    }

    // Restituisce il percorso del file di plot: "miner_storage/data/uuid.plot"
    public String getPlotPath() {
        return System.getProperty("user.dir") + java.io.File.separator +
                "miner_storage" + java.io.File.separator +
                "data" + java.io.File.separator + uuid + ".plot";
    }
}
