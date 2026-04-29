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

    public static String bytesToHex(byte[] hash) {
        StringBuilder hexString = new StringBuilder(2 * hash.length);
        for (byte b : hash) {
            String hex = Integer.toHexString(0xff & b);
            if (hex.length() == 1) hexString.append('0');
            hexString.append(hex);
        }
        return hexString.toString();
    }



    /*public KeyPair importKeyFromHex(String privateKeyHex) throws Exception {
        // 1. Converti la stringa hex in byte[]
        byte[] keyBytes = Hex.decode(privateKeyHex);

        // 2. Ricostruisci la chiave privata (esempio per algoritmi standard come EdDSA o ECDSA)
        PKCS8EncodedKeySpec spec = new PKCS8EncodedKeySpec(keyBytes);
        KeyFactory kf = KeyFactory.getInstance("Ed25519"); // O l'algoritmo usato da Mokamint
        PrivateKey privKey = kf.generatePrivate(spec);

        // 3. Deriva la pubblica dalla privata per creare il KeyPair
        // Spesso Mokamint ha utility interne per farlo, altrimenti serve la specifica
        PublicKey pubKey = derivePublicKey(privKey);

        return new KeyPair(pubKey, privKey);
    }
    */

}