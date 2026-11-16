package me.athlaeos.valhallammo.listeners;

import me.athlaeos.valhallammo.ValhallaMMO;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Keeps the normal health display on one row, regardless of the player's real
 * maximum health. This is visual only: damage, healing, and max HP are unchanged.
 */
public final class HealthBarScalingListener implements Listener {
    private static final double DISPLAY_HEALTH = 20.0; // 10 hearts
    private final ValhallaMMO plugin;
    private final Map<UUID, PreviousScale> originalScales = new HashMap<>();

    public HealthBarScalingListener(ValhallaMMO plugin) {
        this.plugin = plugin;
    }

    public void applyToOnlinePlayers() {
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            apply(player);
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        applyNextTick(event.getPlayer());
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent event) {
        applyNextTick(event.getPlayer());
    }

    @EventHandler
    public void onWorldChange(PlayerChangedWorldEvent event) {
        applyNextTick(event.getPlayer());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        restore(event.getPlayer());
    }

    private void applyNextTick(Player player) {
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (player.isOnline()) apply(player);
        });
    }

    private void apply(Player player) {
        if (ValhallaMMO.isWorldBlacklisted(player.getWorld().getName())) {
            restore(player);
            return;
        }

        originalScales.putIfAbsent(
                player.getUniqueId(),
                new PreviousScale(player.isHealthScaled(), player.getHealthScale()));

        // setHealthScale also calls setHealthScaled(true). Bukkit scales the
        // display as actualHealth / maxHealth * DISPLAY_HEALTH.
        if (!player.isHealthScaled() || player.getHealthScale() != DISPLAY_HEALTH) {
            player.setHealthScale(DISPLAY_HEALTH);
        }
    }

    private void restore(Player player) {
        PreviousScale original = originalScales.remove(player.getUniqueId());
        if (original == null) return;

        // If another plugin has since changed the health scale, leave it alone.
        if (!player.isHealthScaled() || player.getHealthScale() != DISPLAY_HEALTH) return;
        if (original.scaled()) {
            player.setHealthScale(original.value());
        } else {
            player.setHealthScaled(false);
        }
    }

    public void restoreOnlinePlayers() {
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            restore(player);
        }
        originalScales.clear();
    }

    private record PreviousScale(boolean scaled, double value) {}
}
