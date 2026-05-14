package test;

import it.univr.mokamintminer.services.MinerService;

import java.nio.file.Path;
import java.security.KeyPair;

public class TestIdentity {
    public static void main(String[] args) {
        try {
            MinerService service = new MinerService();

            // --- 1. GENERAZIONE ---
            String paroleOriginarie = service.generateNewMnemonic();
            System.out.println("1. Parole generate: " + paroleOriginarie);

            // --- 2. VALIDAZIONE ---
            boolean isValida = service.isValidMnemonic(paroleOriginarie);
            System.out.println("2. La mnemonica generata è valida? " + (isValida ? "SÌ" : "NO"));

            // Test con una mnemonica errata per prova
            boolean isErrata = service.isValidMnemonic("parola inventata non valida");
            System.out.println("3. Test mnemonica errata (deve essere false): " + isErrata);

            // --- 3. DERIVAZIONE E SALVATAGGIO ---
            KeyPair chiaviOriginarie = service.deriveKeyPairFromMnemonic(paroleOriginarie);
            Path path = Path.of("test_chiave.json");
            service.saveIDFile(paroleOriginarie, chiaviOriginarie, path);
            System.out.println("4. File JSON creato con successo.");

            // --- 4. CARICAMENTO DAL JSON ---
            String paroleDalJson = service.loadMnemonicFromFile(path);
            KeyPair chiaviDalJson = service.deriveKeyPairFromMnemonic(paroleDalJson);
            System.out.println("5. Parole ricaricate dal file: " + paroleDalJson);

            // --- 5. CONFRONTO FINALE ---
            // Confrontiamo le stringhe delle parole
            boolean paroleUguali = paroleOriginarie.equals(paroleDalJson);

            // Confrontiamo le Public Key (trasformandole in Hex per sicurezza)
            String pubOriginarie = bytesToHex(chiaviOriginarie.getPublic().getEncoded());
            String pubDalJson = bytesToHex(chiaviDalJson.getPublic().getEncoded());
            boolean chiaviUguali = pubOriginarie.equals(pubDalJson);


            System.out.println("\n--- RISULTATI TEST ---");
            System.out.println("Parole identiche: " + (paroleUguali ? "OK" : "ERRORE"));
            System.out.println("Chiavi identiche: " + (chiaviUguali ? "OK" : "ERRORE"));
            System.out.println("Public Key (Hex): " + pubDalJson);

            if (paroleUguali && chiaviUguali) {
                System.out.println("\nTEST SUPERATO: L'identità è stata generata, salvata e ricaricata correttamente!");
            }

            System.out.println("--- TEST CREAZIONE PLOT ---");
            Path pathPlot = Path.of("mio_test.plot");
            long nonces = 10; // Molto piccolo per il test veloce

            /*service.createPlot(pathPlot, chiaviDalJson, 0, nonces, progress -> {
                System.out.println("Progresso: " + progress + "%");
            });*/

        } catch (Exception e) {
            System.err.println("ERRORE DURANTE IL TEST:");
            e.printStackTrace();
        }
    }

    // Metodo di utilità per convertire byte in una stringa leggibile (Esadecimale)
    private static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
}


