package com.wab.statsplugin;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.logging.Level;

public class FirestoreClient {

    private final StatsPlugin plugin;
    private final String projectId;
    private final String coleccion;
    private final FirebaseAuthClient authClient;
    private final HttpClient http = HttpClient.newHttpClient();

    public FirestoreClient(StatsPlugin plugin, String projectId, String coleccion, FirebaseAuthClient authClient) {
        this.plugin = plugin;
        this.projectId = projectId;
        this.coleccion = coleccion;
        this.authClient = authClient;
    }

    public void actualizarStats(String mcUser, int kills, int muertes) {
        try {
            String token = authClient.obtenerTokenValido();
            if (token == null) {
                plugin.getLogger().warning("Sin token de Firebase válido, se omite la sincronización de " + mcUser);
                return;
            }

            String docId = URLEncoder.encode(mcUser, StandardCharsets.UTF_8);

            String url = "https://firestore.googleapis.com/v1/projects/" + projectId
                    + "/databases/(default)/documents/" + coleccion + "/" + docId
                    + "?updateMask.fieldPaths=mcUser&updateMask.fieldPaths=kills&updateMask.fieldPaths=muertes";

            String body = "{\"fields\":{"
                    + "\"mcUser\":{\"stringValue\":\"" + mcUser + "\"},"
                    + "\"kills\":{\"integerValue\":\"" + kills + "\"},"
                    + "\"muertes\":{\"integerValue\":\"" + muertes + "\"}"
                    + "}}";

            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + token)
                    .method("PATCH", HttpRequest.BodyPublishers.ofString(body))
                    .build();

            HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString());

            if (res.statusCode() != 200) {
                plugin.getLogger().warning("Error subiendo stats de " + mcUser + ": " + res.body());
            }
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Error sincronizando stats de " + mcUser, e);
        }
    }
}
