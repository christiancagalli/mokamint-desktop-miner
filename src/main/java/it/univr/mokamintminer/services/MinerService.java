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
import java.nio.file.Files;
import java.io.IOException;
import java.util.Base64;

import java.nio.file.Path;
import java.security.KeyPair;

public class MinerService {

    public interface ProgressListener {
        void onProgress(int percent);
    }

    // Valore di default se l'utente non specifica nulla
    public static final long DEFAULT_PLOT_SIZE = 1000;

    public void createPlot(Path plotPath,
                           KeyPair myKeys,
                           long startNonce,
                           long plotSize,
                           ProgressListener listener) throws Exception {

        // 1. Definiamo gli algoritmi
        SignatureAlgorithm signature = SignatureAlgorithms.ed25519();
        HashingAlgorithm hashing = HashingAlgorithms.shabal256();

        // 2. Integrazione: Usiamo le TUE chiavi per entrambi i ruoli (o puoi differenziarle)
        Prolog prolog = Prologs.of(
                "desktop-miner",
                signature,
                myKeys.getPublic(), // Chiave per i blocchi
                signature,
                myKeys.getPublic(), // Chiave per le transazioni
                new byte[0]
        );

        // 3. Calcolo metadati per i log
        long sizeInMB = (plotSize * 262144) / (1024 * 1024);
        System.out.println("[PLOT] Avvio creazione plot deterministico:");
        System.out.println("[PLOT] Percorso: " + plotPath.toAbsolutePath());
        System.out.println("[PLOT] Dimensione stimata: " + sizeInMB + " MB");

        // 4. Esecuzione effettiva tramite la libreria Plots
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
        Entropy entropy = Entropies.random();       //genero l'entropia
        byte[] bytes = entropy.getEntropyAsBytes();
        BIP39Mnemonic mnemonicObject = BIP39Mnemonics.of(bytes);        // uniamo le parole con uno spazio
        return mnemonicObject.stream().collect(java.util.stream.Collectors.joining(" "));  //genera un flusso di parole e poi le cattura e le separa con uno spazio
    }

    // 2. Ricava la coppia di chiavi dalle 12 parole
    public KeyPair deriveKeyPairFromMnemonic(String mnemonic) throws Exception {
        SignatureAlgorithm signature = SignatureAlgorithms.ed25519();
        // Dividiamo la mnemonica in array di parole
        String[] words = mnemonic.split(" ");
        BIP39Mnemonic b39 = BIP39Mnemonics.of(words);
        // Trasformiamo le parole in byte di entropia
        byte[] entropyBytes = b39.getBytes();
        // Generiamo le chiavi dai byte
        return signature.getKeyPair(entropyBytes, "");
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

    public void saveIDFile(String mnemonic, KeyPair keys, Path destination) throws IOException {
        JsonObject json = new JsonObject();

        // Salviamo le parole
        json.addProperty("mnemonic", mnemonic);

        // Salviamo le chiavi convertendole in stringa (Base64)
        String publicKeyEncoded = Base64.getEncoder().encodeToString(keys.getPublic().getEncoded());
        String privateKeyEncoded = Base64.getEncoder().encodeToString(keys.getPrivate().getEncoded());

        json.addProperty("publicKey", publicKeyEncoded);
        json.addProperty("privateKey", privateKeyEncoded);
        json.addProperty("algorithm", "Ed25519");
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

}





// ---TODO:   GENERARE UNA NUOVA CHIAVE OPPURE USARNE UNA NOTA

/*
public void createPlot(Path plotPath,
                       long startNonce,
                       long plotSize,
                       String mnemonic, // Aggiungiamo questo!
                       ProgressListener listener) throws Exception {

    SignatureAlgorithm signature = SignatureAlgorithms.ed25519();
    HashingAlgorithm hashing = HashingAlgorithms.shabal256();

    KeyPair blockKeys;

    if (mnemonic == null || mnemonic.isEmpty()) {
        // CASO A: Non ho una chiave, ne creo una nuova
        blockKeys = signature.getKeyPair();
        System.out.println("Generata nuova chiave.");
        // Qui dovresti mostrare all'utente le 12 parole generate per permettergli di salvarle!
    } else {
        // CASO B: L'utente ha inserito le sue 12 parole
        // Esiste una funzione nelle librerie Hotmoka per rigenerare la chiave dalle parole
        byte[] entropy = deriveEntropyFromMnemonic(mnemonic);
        blockKeys = signature.keyPairFrom(entropy);
        System.out.println("Chiave caricata correttamente.");
    }

    // Il resto rimane uguale, ma il plot ora è "firmato" con la chiave scelta
    Prolog prolog = Prologs.of("desktop-miner", signature, blockKeys.getPublic(), ...);
    Plots.create(plotPath, prolog, startNonce, plotSize, hashing, listener::onProgress);
}*
/


 */
//---TODO:
/*
public void saveKeyToFile(String mnemonic, Path destination) throws IOException {
    // Crea la stringa JSON (o semplice testo)
    String content = "Mnemonic: " + mnemonic;
    Files.writeString(destination, content);
}

public String loadKeyFromFile(Path source) throws IOException {
    // Legge il file e restituisce la mnemonica
    return Files.readString(source).replace("Mnemonic: ", "").trim();
}*/


//---TODO:
/*

public void saveKeyFile(String mnemonic, Path filePath) throws IOException {
    String json = "{\n" +
            "  \"version\": \"1.0\",\n" +
            "  \"description\": \"Mokamint Miner Key File\",\n" +
            "  \"mnemonic\": \"" + mnemonic + "\",\n" +
            "  \"warning\": \"NON CONDIVIDERE QUESTO FILE. Chiunque lo possiede può rubare i tuoi fondi.\"\n" +
            "}";

    Files.writeString(filePath, json);
}

loadKeys(Path path): La logica per leggere il file e recuperare la mnemonica.
public String loadMnemonicFromFile(Path filePath) throws IOException {
    String content = Files.readString(filePath);
    // Logica semplice per estrarre il valore tra le virgolette dopo "mnemonic"
    // In un progetto reale useresti una libreria JSON come Jackson o Gson
    int start = content.indexOf("\"mnemonic\": \"") + 13;
    int end = content.indexOf("\"", start);
    return content.substring(start, end);
}

------------------------------------------------SENZA IMPORT MNEMONIC----------------------------------------------------

import io.hotmoka.crypto.Entropies;
import io.hotmoka.crypto.SignatureAlgorithms;
import io.hotmoka.crypto.api.Entropy;
import io.hotmoka.crypto.api.SignatureAlgorithm;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.io.IOException;

public class MinerService {

    // 1. Genera una nuova entropia casuale
    public Entropy generateNewKey() {
        return Entropies.random();
    }

    // 2. Salva l'entropia in un file JSON
    public void saveKeyFile(Entropy entropy, Path filePath) throws IOException {
        // Trasformiamo i byte dell'entropia in una stringa esadecimale per salvarla
        String hexEntropy = bytesToHex(entropy.getResponse());

        String json = "{\n" +
                "  \"version\": \"1.0\",\n" +
                "  \"type\": \"Mokamint Key File\",\n" +
                "  \"entropy\": \"" + hexEntropy + "\",\n" +
                "  \"warning\": \"Keep this file secret!\"\n" +
                "}";

        Files.writeString(filePath, json);
    }

    // 3. Carica l'entropia dal file JSON
    public byte[] loadEntropyBytes(Path filePath) throws IOException {
        String content = Files.readString(filePath);
        int start = content.indexOf("\"entropy\": \"") + 12;
        int end = content.indexOf("\"", start);
        String hex = content.substring(start, end);
        return hexToBytes(hex);
    }

    // Funzioni di supporto per trasformare i byte in testo (Hex)
    private String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) sb.append(String.format("%02x", b));
        return sb.toString();
    }

    private byte[] hexToBytes(String s) {
        int len = s.length();
        byte[] data = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            data[i / 2] = (byte) ((Character.digit(s.charAt(i), 16) << 4)
                                 + Character.digit(s.charAt(i+1), 16));
        }
        return data;
    }
}
*/



//#################################################################################################################################################################################
// --------VECCHIO CODICE---------------------------
/*public void createPlot(Path plotPath,
                           long startNonce,
                           long plotSize,
                           String endpoint,
                           ProgressListener listener
    ) throws Exception {

            // Algoritmi crypto
            SignatureAlgorithm signature = SignatureAlgorithms.ed25519();
            HashingAlgorithm hashing = HashingAlgorithms.shabal256();

            // Chiavi locali del miner
            KeyPair blockKeys = signature.getKeyPair();
            KeyPair txKeys = signature.getKeyPair();

            // prolog valido
            Prolog prolog = Prologs.of(
                    "desktop-miner",
                    signature,
                    blockKeys.getPublic(),
                    signature,
                    txKeys.getPublic(),
                    new byte[0]
            );

            System.out.println("[PLOT] Creating plot:");
            System.out.println("[PLOT] Path: " + plotPath.toAbsolutePath());

            System.out.println("[PLOT] Nonces: " + plotSize);
            System.out.println("[PLOT] Estimated size: " + (plotSize * 262144 / (1024 * 1024)) + " MB");

            // creazione plot
            Plots.create(
                    plotPath,
                    prolog,
                    startNonce,
                    plotSize,
                    hashing,
                    progress -> {
                        if (listener != null)
                            listener.onProgress(progress);
                    }
            );

    }*/