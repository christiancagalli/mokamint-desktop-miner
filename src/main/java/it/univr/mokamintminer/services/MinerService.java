package it.univr.mokamintminer.services;

import io.hotmoka.crypto.BIP39Mnemonics;
import io.hotmoka.crypto.Entropies;
import io.hotmoka.crypto.api.BIP39Mnemonic;
import io.hotmoka.crypto.api.Entropy;
import io.hotmoka.crypto.api.SignatureAlgorithm;

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
    }

    public SignatureAlgorithm getSignatureAlgorithm() { return signatureAlgorithm; }

    public String getPlotPath() { return plotPath; }

    public String getNodeUri() { return nodeUri; }

    public String getChainID(){ return chainID; }

    // 1. Genera 12 parole casuali
    public String generateNewMnemonic() {
        Entropy entropy = Entropies.random();              //genero l'entropia
        byte[] bytes = entropy.getEntropyAsBytes();
        BIP39Mnemonic mnemonicObject = BIP39Mnemonics.of(bytes);     // unisco le parole con uno spazio
        return mnemonicObject.stream().collect(java.util.stream.Collectors.joining(" "));   //genera un flusso di parole e poi le cattura e le separa con uno spazio
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

