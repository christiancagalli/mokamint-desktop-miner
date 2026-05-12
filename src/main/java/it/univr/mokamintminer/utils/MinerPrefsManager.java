package it.univr.mokamintminer.utils;

import java.util.prefs.Preferences;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class MinerPrefsManager {
    private static final Preferences prefs = Preferences.userNodeForPackage(MinerPrefsManager.class);
    private static final String URIS_KEY = "visited_uris";

    public static void saveUri(String uri) {
        List<String> uris = new ArrayList<>(getVisitedUris());
        if (!uris.contains(uri)) {
            uris.add(uri);
            prefs.put(URIS_KEY, String.join(",", uris));
        }
    }

    public static List<String> getVisitedUris() {
        String saved = prefs.get(URIS_KEY, "http://localhost:8080"); // Default
        return Arrays.asList(saved.split(","));
    }

    public static void removeUri(String uriToRemove) {
        List<String> uris = new ArrayList<>(getVisitedUris());
        // Rimuoviamo l'URI dalla lista (se presente)
        if (uris.remove(uriToRemove)) {
            // Salviamo la nuova lista aggiornata
            if (uris.isEmpty()) {
                prefs.remove(URIS_KEY);
            } else {
                prefs.put(URIS_KEY, String.join(",", uris));
            }
        }
    }
}