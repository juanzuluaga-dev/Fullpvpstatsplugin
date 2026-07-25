package com.wab.statsplugin;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.util.logging.Level;

public class FirebaseAuthClient {

    private final StatsPlugin plugin;
    private final String apiKey;
    private final String email;
    private final String password;
    private final HttpClient http = HttpClient.newHttpClient();

    private volatile String idToken;
    private volatile String refreshToken;
    private volatile Instant expira = Instant.EPOCH;

    public FirebaseAuthClient(StatsPlugin plugin, String apiKey, String email, String password) {
        this.plugin = plugin;
        this.apiKey = apiKey;
        this.email = email;
        this.password = password;
    }

    public void autenticar() {
        try {
            String body = "{\"email\":\"" + email + "\",\"password\":\"" + password + "\",\"returnSecureToken\":true}";
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create("https://identitytoolkit.googleapis.com/v1/accounts:signInWithPassword?key=" + apiKey))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();

            HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString());

            if (res.statusCode() != 200) {
                plugin.getLogger().severe("Error autenticando con Firebase: " + res.body());
                return;
            }

            idToken = extraerCampo(res.body(), "idToken");
            refreshToken = extraerCampo(res.body(), "refreshToken");
            String expiresIn = extraerCampo(res.body(), "expiresIn");
            long segundos = expiresIn != null ? Long.parseLong(expiresIn) : 3600;
            expira = Instant.now().plusSeconds(segundos - 120); // renovar 2 min antes

            plugin.getLogger().info("Autenticado correctamente con Firebase.");
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "No se pudo autenticar con Firebase", e);
        }
    }

    public String obtenerTokenValido() {
        if (idToken == null || Instant.now().isAfter(expira)) {
            renovar();
        }
        return idToken;
    }

    private void renovar() {
        if (refreshToken == null) {
            autenticar();
            return;
        }
        try {
            String body = "grant_type=refresh_token&refresh_token=" + refreshToken;
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create("https://securetoken.googleapis.com/v1/token?key=" + apiKey))
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();

            HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString());

            if (res.statusCode() != 200) {
                plugin.getLogger().warning("Error renovando token, reautenticando: " + res.body());
                autenticar();
                return;
            }

            idToken = extraerCampo(res.body(), "id_token");
            refreshToken = extraerCampo(res.body(), "refresh_token");
            String expiresIn = extraerCampo(res.body(), "expires_in");
            long segundos = expiresIn != null ? Long.parseLong(expiresIn) : 3600;
            expira = Instant.now().plusSeconds(segundos - 120);
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Error renovando token de Firebase", e);
        }
    }

    // Extractor simple de campos JSON planos (evita depender de librerías externas)
    private String extraerCampo(String json, String campo) {
        String patron = "\"" + campo + "\"";
        int idx = json.indexOf(patron);
        if (idx == -1) return null;
        int inicioValor = json.indexOf(':', idx) + 1;
        while (inicioValor < json.length() && (json.charAt(inicioValor) == ' ' || json.charAt(inicioValor) == '"')) inicioValor++;
        int finValor = inicioValor;
        while (finValor < json.length() && json.charAt(finValor) != '"' && json.charAt(finValor) != ',' && json.charAt(finValor) != '}') finValor++;
        return json.substring(inicioValor, finValor);
    }
}
