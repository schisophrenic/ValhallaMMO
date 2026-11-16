package me.athlaeos.valhallammo.listeners;

import me.athlaeos.valhallammo.ValhallaMMO;
import me.athlaeos.valhallammo.playerstats.AccumulativeStatManager;
import org.bukkit.Bukkit;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityRegainHealthEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/**
 * RPG shields. Ghost health is a virtual, regenerating buffer AFTER vanilla
 * absorption and BEFORE real health. Overheal grants vanilla absorption, capped
 * against the attacker's max HP. Neither changes the max-health attribute.
 *
 * Apothic's GHOST_HEALTH is only defined, not implemented upstream, so this
 * is a server-side Valhalla-specific implementation.
 */
public final class CombatShields implements Listener {
    private final ValhallaMMO plugin;
    private final Map<UUID, Ghost> ghosts = new HashMap<>();
    private final Map<UUID, Map<UUID, Double>> pendingHealthDamage = new HashMap<>();

    private static final class Ghost {
        double cap;
        double current;
        long lastHitAt;

        Ghost(double cap) {
            this.cap = cap;
            this.current = cap;
        }
    }

    public CombatShields(ValhallaMMO plugin) {
        this.plugin = plugin;
        plugin.getServer().getScheduler().runTaskTimer(plugin, this::regenerate, 20L, 20L);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        // Max-health and equipment stats may finish updating later on join.
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (e.getPlayer().isOnline()) getGhost(e.getPlayer());
        }, 45L);
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent e) {
        ghosts.remove(e.getPlayer().getUniqueId());
        pendingHealthDamage.remove(e.getPlayer().getUniqueId());
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (e.getPlayer().isOnline()) getGhost(e.getPlayer());
        });
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        clear(e.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onDeath(EntityDeathEvent e) {
        UUID uuid = e.getEntity().getUniqueId();
        ghosts.remove(uuid);
        // Pending HP-damage credit must survive death until Valhalla's queued
        // damage commit runs; setHealth(0) can fire this event synchronously.
        for (Map<UUID, Double> credits : pendingHealthDamage.values()) credits.remove(uuid);
    }

    private void clear(UUID uuid) {
        ghosts.remove(uuid);
        pendingHealthDamage.remove(uuid);
        for (Map<UUID, Double> credits : pendingHealthDamage.values()) credits.remove(uuid);
    }

    public void shutdown() {
        ghosts.clear();
        pendingHealthDamage.clear();
    }

    private Ghost getGhost(LivingEntity entity) {
        if (!ValhallaMMO.getPluginConfig().getBoolean("apothic_ghost_health_enabled", true)
                || ValhallaMMO.isWorldBlacklisted(entity.getWorld().getName())) return null;
        double cap = Math.max(0, AccumulativeStatManager.getCachedStats("GHOST_HEALTH", entity, 10000, true));
        if (cap <= 0) {
            ghosts.remove(entity.getUniqueId());
            return null;
        }
        Ghost ghost = ghosts.get(entity.getUniqueId());
        if (ghost == null) {
            ghost = new Ghost(cap);
            ghosts.put(entity.getUniqueId(), ghost);
        } else if (cap != ghost.cap) {
            boolean full = ghost.current >= ghost.cap - 0.000001;
            ghost.current = full ? cap : Math.min(ghost.current, cap);
            ghost.cap = cap;
        }
        return ghost;
    }

    /**
     * Incoming damage is already reduced by resistances, armor, PvP and other
     * defense calculations. Pass ONLY the amount left after vanilla absorption.
     */
    public double absorbGhost(LivingEntity target, double remainingDamage) {
        if (remainingDamage <= 0) return 0;
        Ghost ghost = getGhost(target);
        if (ghost == null) return remainingDamage;
        ghost.lastHitAt = System.currentTimeMillis();
        double absorbed = Math.min(remainingDamage, ghost.current);
        ghost.current -= absorbed;
        return remainingDamage - absorbed;
    }

    public void markHit(LivingEntity target) {
        Ghost ghost = ghosts.get(target.getUniqueId());
        if (ghost != null) ghost.lastHitAt = System.currentTimeMillis();
    }

    private void regenerate() {
        // Initialize player shields even before their first hit; active mobs
        // are initialized lazily on their first incoming attack.
        for (Player player : Bukkit.getOnlinePlayers()) getGhost(player);
        long now = System.currentTimeMillis();
        int delay = Math.max(0, ValhallaMMO.getPluginConfig().getInt("apothic_ghost_health_regen_delay_seconds", 5));
        double fraction = Math.max(0, ValhallaMMO.getPluginConfig().getDouble("apothic_ghost_health_regen_fraction_per_second", 0.10));
        Iterator<Map.Entry<UUID, Ghost>> iterator = ghosts.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, Ghost> entry = iterator.next();
            Entity entity = Bukkit.getEntity(entry.getKey());
            if (!(entity instanceof LivingEntity target) || !target.isValid() || target.isDead()
                    || ValhallaMMO.isWorldBlacklisted(target.getWorld().getName())) {
                iterator.remove();
                continue;
            }
            double cap = Math.max(0, AccumulativeStatManager.getCachedStats("GHOST_HEALTH", target, 10000, true));
            if (cap <= 0) {
                iterator.remove();
                continue;
            }
            Ghost ghost = entry.getValue();
            if (cap != ghost.cap) {
                boolean full = ghost.current >= ghost.cap - 0.000001;
                ghost.current = full ? cap : Math.min(ghost.current, cap);
                ghost.cap = cap;
            }
            if (now - ghost.lastHitAt >= delay * 1000L) {
                ghost.current = Math.min(ghost.cap, ghost.current + ghost.cap * fraction);
            }
        }
    }

    /**
     * Add effective health damage for one hit; multiple elemental hits in the
     * same tick are combined and credited only after Valhalla commits HP.
     */
    public void recordDamage(LivingEntity target, LivingEntity attacker, double amount) {
        if (amount <= 0 || attacker.equals(target)) return;
        pendingHealthDamage.computeIfAbsent(target.getUniqueId(), ignored -> new HashMap<>())
                .merge(attacker.getUniqueId(), amount, Double::sum);
    }

    /** Called once after Valhalla's queued health update actually completes. */
    public void finishDamage(LivingEntity target, double actualHealthLost) {
        Map<UUID, Double> credits = pendingHealthDamage.remove(target.getUniqueId());
        if (credits == null || actualHealthLost <= 0) return;
        double total = credits.values().stream().mapToDouble(Double::doubleValue).sum();
        double fraction = total <= 0 ? 0 : Math.min(1, actualHealthLost / total);
        for (Map.Entry<UUID, Double> credit : credits.entrySet()) {
            Entity attacker = Bukkit.getEntity(credit.getKey());
            if (attacker instanceof LivingEntity living && !living.isDead()) {
                recover(living, target, credit.getValue() * fraction);
            }
        }
    }

    /** Also used for a lethal hit processed directly by vanilla. */
    public void recover(LivingEntity attacker, LivingEntity victim, double actualHealthLost) {
        if (actualHealthLost <= 0 || attacker.equals(victim)
                || ValhallaMMO.isWorldBlacklisted(attacker.getWorld().getName())) return;

        double lifeSteal = Math.max(0, AccumulativeStatManager.getCachedRelationalStats(
                "LIFE_STEAL", victim, attacker, 10000, true));
        if (lifeSteal > 0) {
            double rawHeal = actualHealthLost * lifeSteal;
            EntityRegainHealthEvent event = new EntityRegainHealthEvent(
                    attacker, rawHeal, EntityRegainHealthEvent.RegainReason.CUSTOM);
            plugin.getServer().getPluginManager().callEvent(event);
            if (!event.isCancelled() && event.getAmount() > 0) {
                AttributeInstance max = attacker.getAttribute(Attribute.GENERIC_MAX_HEALTH);
                if (max != null) attacker.setHealth(Math.min(max.getValue(), attacker.getHealth() + event.getAmount()));
            }
        }

        if (!ValhallaMMO.getPluginConfig().getBoolean("apothic_overheal_enabled", true)) return;
        double overheal = Math.max(0, AccumulativeStatManager.getCachedRelationalStats(
                "OVERHEAL", victim, attacker, 10000, true));
        AttributeInstance max = attacker.getAttribute(Attribute.GENERIC_MAX_HEALTH);
        if (overheal <= 0 || max == null) return;
        double capFraction = Math.max(0, ValhallaMMO.getPluginConfig().getDouble("apothic_overheal_max_health_fraction", 0.5));
        double cap = max.getValue() * capFraction;
        double existing = attacker.getAbsorptionAmount();
        double gain = Math.min(Math.max(0, cap - existing), actualHealthLost * overheal);
        if (gain > 0) attacker.setAbsorptionAmount(existing + gain);
    }
}
