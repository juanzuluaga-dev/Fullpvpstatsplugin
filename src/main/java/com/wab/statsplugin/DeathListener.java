package com.wab.statsplugin;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;

public class DeathListener implements Listener {

    private final StatsPlugin plugin;

    public DeathListener(StatsPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onMuerte(PlayerDeathEvent event) {
        Player victima = event.getEntity();
        plugin.registrarMuerte(victima.getName());

        Player asesino = victima.getKiller();
        if (asesino != null && !asesino.equals(victima)) {
            plugin.registrarKill(asesino.getName());
        }
    }
}
