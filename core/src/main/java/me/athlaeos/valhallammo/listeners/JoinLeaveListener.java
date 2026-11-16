package me.athlaeos.valhallammo.listeners;

import me.athlaeos.valhallammo.ValhallaMMO;
import me.athlaeos.valhallammo.entities.EntityAttributeStats;
import me.athlaeos.valhallammo.gui.PlayerMenuUtilManager;
import me.athlaeos.valhallammo.playerstats.EntityCache;
import me.athlaeos.valhallammo.playerstats.profiles.ProfileCache;
import me.athlaeos.valhallammo.playerstats.profiles.ProfileRegistry;
import me.athlaeos.valhallammo.playerstats.profiles.implementations.PowerProfile;
import me.athlaeos.valhallammo.potioneffects.PotionEffectRegistry;
import me.athlaeos.valhallammo.skills.perk_rewards.implementations.FlightReward;
import me.athlaeos.valhallammo.utility.GlobalEffect;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.persistence.PersistentDataType;

import java.util.Collection;
import java.util.HashSet;
import java.util.UUID;

public class JoinLeaveListener implements Listener {
    private final NamespacedKey HEALTH = ValhallaMMO.key("cached_health");
    private static final Collection<UUID> loadedProfiles = new HashSet<>();

    public static Collection<UUID> getLoadedProfiles() {
        return loadedProfiles;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerJoin(PlayerJoinEvent e) {
        Player player = e.getPlayer();
        applyHealthBarScaling(player);
        EntityCache.getAndCacheProperties(player);
        PotionEffectRegistry.updatePlayerAffectedStatus(player);
        ProfileRegistry.getPersistence().requestProfile(player.getUniqueId());
        GlobalEffect.temporarilyRevealBossBar(player);
        PlayerMenuUtilManager.removePlayerMenuUtility(player.getUniqueId());

        ValhallaMMO.getInstance().getServer().getScheduler().runTaskLater(ValhallaMMO.getInstance(), () -> {
            if (!player.isOnline()) return;
            EntityAttributeStats.updateStats(player);
            applyHealthBarScaling(player);
            FlightReward.setFlight(player, true);

            double health = player.getPersistentDataContainer().getOrDefault(HEALTH, PersistentDataType.DOUBLE, -1D);
            if (health > 0){
                AttributeInstance maxHealth = player.getAttribute(Attribute.GENERIC_MAX_HEALTH);
                if (maxHealth != null){
                    if (maxHealth.getValue() < health) health = maxHealth.getValue();
                    player.setHealth(health);
                }
                player.getPersistentDataContainer().remove(HEALTH);
            }
        }, 40L);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerQuit(PlayerQuitEvent e){
        // the following code is to remove valhallammo's attribute modifiers off players when they log off
        // this is to prevent, in the case valhallammo is being uninstalled, no unintended attributes remain
        // stuck on the player.
        Player player = e.getPlayer();
        player.getPersistentDataContainer().set(HEALTH, PersistentDataType.DOUBLE, player.getHealth());
        EntityAttributeStats.removeStats(player);
        PotionEffectRegistry.markAsUnaffected(player);
        ProfileRegistry.getPersistence().saveProfile(player.getUniqueId(), true);
        ProfileRegistry.getPersistence().saveConfigurableProfile(player.getUniqueId(), true);
        FlightReward.setFlight(player, false);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDimensionChange(PlayerChangedWorldEvent e){
        FlightReward.setFlight(e.getPlayer(), true);
        applyHealthBarScaling(e.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerRespawn(PlayerRespawnEvent e) {
        Player player = e.getPlayer();
        ValhallaMMO.getInstance().getServer().getScheduler().runTask(ValhallaMMO.getInstance(), () -> {
            if (player.isOnline()) applyHealthBarScaling(player);
        });
    }

    /** Keeps 100+ actual health points in a single 10-heart client HUD row. */
    private static void applyHealthBarScaling(Player player) {
        if (!ValhallaMMO.getPluginConfig().getBoolean("health_bar_scaling_enabled", true)) return;
        player.setHealthScaled(true);
        player.setHealthScale(20.0);
    }
}
