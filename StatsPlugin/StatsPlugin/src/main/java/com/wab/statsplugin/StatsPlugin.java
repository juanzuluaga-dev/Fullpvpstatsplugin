package com.wab.statsplugin;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

public class StatsPlugin extends JavaPlugin {

    // mcUser en minúsculas -> [kills, muertes]
    private final ConcurrentHashMap<String, int[]> stats = new ConcurrentHashMap<>();
    private final Set<String> pendientes = ConcurrentHashMap.newKeySet();

    private File archivoStats;
    private FirebaseAuthClient authClient;
    private FirestoreClient firestoreClient;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        reloadConfig();

        archivoStats = new File(getDataFolder(), "stats.yml");
        cargarStatsLocal();

        String apiKey = getConfig().getString("firebase.api-key");
        String projectId = getConfig().getString("firebase.project-id");
        String email = getConfig().getString("firebase.email");
        String password = getConfig().getString("firebase.password");
        String coleccion = getConfig().getString("firebase.coleccion", "estadisticas_fullpvp");

        if (password == null || password.equals("CAMBIA_ESTO")) {
            getLogger().warning("=======================================================");
            getLogger().warning("¡Todavía no configuraste la contraseña de Firebase!");
            getLogger().warning("Edita plugins/StatsPlugin/config.yml y reinicia el server.");
            getLogger().warning("=======================================================");
            return;
        }

        authClient = new FirebaseAuthClient(this, apiKey, email, password);
        firestoreClient = new FirestoreClient(this, projectId, coleccion, authClient);

        authClient.autenticar();

        getServer().getPluginManager().registerEvents(new DeathListener(this), this);

        int intervaloMinutos = getConfig().getInt("intervalo-minutos", 5);
        long ticks = intervaloMinutos * 60L * 20L;

        getServer().getScheduler().runTaskTimerAsynchronously(this, this::sincronizar, ticks, ticks);

        getLogger().info("StatsPlugin activado. Sincronizando cada " + intervaloMinutos + " minuto(s).");
    }

    @Override
    public void onDisable() {
        guardarStatsLocal();
        if (firestoreClient != null) {
            sincronizar();
        }
    }

    public void registrarKill(String mcUser) {
        stats.compute(mcUser, (k, v) -> {
            if (v == null) v = new int[]{0, 0};
            v[0]++;
            return v;
        });
        pendientes.add(mcUser);
    }

    public void registrarMuerte(String mcUser) {
        stats.compute(mcUser, (k, v) -> {
            if (v == null) v = new int[]{0, 0};
            v[1]++;
            return v;
        });
        pendientes.add(mcUser);
    }

    private void sincronizar() {
        if (pendientes.isEmpty() || firestoreClient == null) return;

        Set<String> aEnviar = new HashSet<>(pendientes);
        pendientes.clear();

        for (String mcUser : aEnviar) {
            int[] valores = stats.get(mcUser);
            if (valores == null) continue;
            firestoreClient.actualizarStats(mcUser, valores[0], valores[1]);
        }

        guardarStatsLocal();
    }

    private void cargarStatsLocal() {
        if (!archivoStats.exists()) return;
        FileConfiguration yml = YamlConfiguration.loadConfiguration(archivoStats);
        for (String key : yml.getKeys(false)) {
            int kills = yml.getInt(key + ".kills", 0);
            int muertes = yml.getInt(key + ".muertes", 0);
            stats.put(key, new int[]{kills, muertes});
        }
        getLogger().info("Cargadas estadísticas locales de " + stats.size() + " jugador(es).");
    }

    private void guardarStatsLocal() {
        YamlConfiguration yml = new YamlConfiguration();
        for (var entry : stats.entrySet()) {
            yml.set(entry.getKey() + ".kills", entry.getValue()[0]);
            yml.set(entry.getKey() + ".muertes", entry.getValue()[1]);
        }
        try {
            yml.save(archivoStats);
        } catch (IOException e) {
            getLogger().log(Level.WARNING, "No se pudo guardar stats.yml", e);
        }
    }
}
