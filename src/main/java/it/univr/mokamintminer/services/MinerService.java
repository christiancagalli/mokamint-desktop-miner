package it.univr.mokamintminer.services;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.hotmoka.crypto.HashingAlgorithms;
import io.hotmoka.crypto.SignatureAlgorithms;
import io.hotmoka.crypto.api.BIP39Mnemonic;
import io.hotmoka.crypto.api.HashingAlgorithm;
import io.hotmoka.crypto.api.SignatureAlgorithm;
import io.mokamint.nonce.Prologs;
import io.mokamint.nonce.api.Prolog;
import io.mokamint.plotter.Plots;

import io.hotmoka.crypto.Entropies;
import io.hotmoka.crypto.api.Entropy;
import io.hotmoka.crypto.BIP39Mnemonics;
import org.bouncycastle.util.encoders.Hex;

import java.nio.file.Files;
import java.io.IOException;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Base64;

import java.nio.file.Path;
import java.security.KeyPair;
import java.util.function.Consumer;

public class MinerService {
    private String nodeUri;
    private String plotPath;
    private SignatureAlgorithm signatureAlgorithm;
    private String chainID;


    public void configure(String uri, String path, SignatureAlgorithm signatureAlg, String chainID) {
        this.nodeUri = uri;
        this.plotPath = path;
        this.signatureAlgorithm = signatureAlg;
        this.chainID = chainID;

        System.out.println("MinerService configurato correttamente:");
        System.out.println(" - URI: " + nodeUri);
        System.out.println(" - Algoritmo: " + signatureAlgorithm.getName());
        System.out.println(" - ChainID: " + chainID);
    }

    public SignatureAlgorithm getSignatureAlgorithm() { return signatureAlgorithm; }

    public String getPlotPath() { return plotPath; }

    public String getNodeUri() { return nodeUri; }

    public String getChainID(){ return chainID; }

    public interface ProgressListener {
        void onProgress(int percent);
    }

    public void createPlot(Path plotPath,
                           KeyPair myKeys,
                           long startNonce,
                           long plotSize,
                           ProgressListener listener,
                           Consumer<String> logger) throws Exception {

        // Algoritmo di hashing
        HashingAlgorithm hashing = HashingAlgorithms.shabal256();

        // Creazione prolog
        Prolog prolog = Prologs.of(
                this.chainID,
                signatureAlgorithm,
                myKeys.getPublic(), // Chiave per i blocchi
                signatureAlgorithm,
                myKeys.getPublic(), // Chiave per le transazioni
                new byte[0]
        );

        long sizeInMB = (plotSize * 262144) / (1024 * 1024);
        logger.accept("[PLOT] Avvio creazione plot deterministico:");
        logger.accept("[PLOT] Percorso: " + plotPath.toAbsolutePath());
        logger.accept("[PLOT] Dimensione stimata: " + sizeInMB + " MB");

        // Esecuzione effettiva tramite la libreria Plots
        Plots.create(
                plotPath,
                prolog,
                startNonce,
                plotSize,
                hashing,
                progress -> {
                    if (listener != null) {
                        listener.onProgress(progress);
                    }
                }
        );
    }


    // 1. Genera 12 parole casuali
    public String generateNewMnemonic() {
        Entropy entropy = Entropies.random();              //genero l'entropia
        byte[] bytes = entropy.getEntropyAsBytes();
        BIP39Mnemonic mnemonicObject = BIP39Mnemonics.of(bytes);     // uniamo le parole con uno spazio
        return mnemonicObject.stream().collect(java.util.stream.Collectors.joining(" "));   //genera un flusso di parole e poi le cattura e le separa con uno spazio
    }

    // 2. Ricava la coppia di chiavi dalle 12 parole
    public KeyPair deriveKeyPairFromMnemonic(String mnemonic) throws Exception {
        if (signatureAlgorithm == null) throw new Exception("Algoritmo non configurato!");
        String[] words = mnemonic.split(" ");       // Dividiamo la mnemonica in array di parole
        BIP39Mnemonic b39 = BIP39Mnemonics.of(words);
        // Trasformiamo le parole in byte di entropia
        byte[] entropyBytes = b39.getBytes();
        // Generiamo le chiavi dai byte
        return signatureAlgorithm.getKeyPair(entropyBytes, "");
    }

    public boolean isValidMnemonic(String mnemonic) {
        if (mnemonic == null || mnemonic.isBlank()) return false;

        try {
            String[] words = mnemonic.trim().split("[,\\s]+");
            BIP39Mnemonics.of(words);
            return words.length >= 12 && words.length % 3 == 0;
        } catch (Exception e) {
            return false;       // Se il dizionario non riconosce una parola
        }
    }

    // Salva l'identità su file
    public void saveIDFile(String mnemonic, KeyPair keys, Path destination) throws IOException, java.security.InvalidKeyException {
        JsonObject json = new JsonObject();

        json.addProperty("mnemonic", mnemonic);

        // Salviamo le chiavi convertendole in stringa (Base64)                                         TODO: NON VA BENE
        SignatureAlgorithm algorithm = getSignatureAlgorithm();
        String publicKeyEncoded = Base64.getEncoder().encodeToString(keys.getPublic().getEncoded());
        String privateKeyEncoded = Base64.getEncoder().encodeToString(keys.getPrivate().getEncoded());

        json.addProperty("publicKey", publicKeyEncoded);
        json.addProperty("privateKey", privateKeyEncoded);
        json.addProperty("algorithm", algorithm.toString());
        json.addProperty("createdAt", java.time.Instant.now().toString());

        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        String formattedJson = gson.toJson(json);

        Files.writeString(destination, formattedJson);
    }

    public String loadMnemonicFromFile(Path source) throws Exception {
        String content = Files.readString(source);
        JsonObject json = JsonParser.parseString(content).getAsJsonObject();

        if (!json.has("mnemonic")) {
            throw new Exception("Il file JSON non è un file di identità valido (manca la mnemonica).");
        }

        String mnemonic = json.get("mnemonic").getAsString();

        if (!isValidMnemonic(mnemonic)) {
            throw new Exception("Il file JSON contiene una mnemonica non valida!");
        }
        return mnemonic;
    }

    public static String bytesToHex(byte[] hash) {
        StringBuilder hexString = new StringBuilder(2 * hash.length);
        for (byte b : hash) {
            String hex = Integer.toHexString(0xff & b);
            if (hex.length() == 1) hexString.append('0');
            hexString.append(hex);
        }
        return hexString.toString();
    }

}

