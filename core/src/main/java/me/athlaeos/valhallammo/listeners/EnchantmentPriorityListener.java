package me.athlaeos.valhallammo.listeners;

import me.athlaeos.valhallammo.ValhallaMMO;
import org.bukkit.Tag;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Standalone fallback enchantment priority handling in ValhallaMMO.
 * EnchantsSquared owns the same calculation when it is installed; never stack both.
 */
public final class EnchantmentPriorityListener implements Listener {
    private static final List<String> DEFENSE = List.of("protection", "magic_protection",
            "projectile_protection", "blast_protection", "fire_protection");
    private static final List<String> OFFENSE = List.of("sharpness", "smite", "bane_of_arthropods");
    private final boolean enabled;
    private final List<String> defenseOrder;
    private final List<String> offenseOrder;
    private final Map<Integer, double[]> defense;
    private final Map<Integer, double[]> offense;

    public EnchantmentPriorityListener() {
        ConfigurationSection root = ValhallaMMO.getPluginConfig().getConfigurationSection("enchantment_priority");
        enabled = root == null || root.getBoolean("enabled", true);
        defenseOrder = order(root, "defense_order", DEFENSE);
        offenseOrder = order(root, "offense_order", OFFENSE);
        defense = read(root == null ? null : root.getConfigurationSection("defense"),
                new int[][] {{10}, {5,5}, {5,3,2}, {4,2,2,2}, {3,2,2,2,1}});
        offense = read(root == null ? null : root.getConfigurationSection("offense"),
                new int[][] {{10}, {5,5}, {5,3,2}});
    }

    private static List<String> order(ConfigurationSection root, String key, List<String> fallback) {
        if (root == null) return fallback;
        List<String> entries = root.getStringList(key);
        if (entries.size() != fallback.size() || !entries.containsAll(fallback)
                || entries.stream().distinct().count() != fallback.size()) return fallback;
        return List.copyOf(entries);
    }

    private static Map<Integer, double[]> read(ConfigurationSection section, int[][] fallback) {
        Map<Integer, double[]> result = new HashMap<>();
        for (int i = 0; i < fallback.length; i++) {
            List<Integer> split = section == null ? List.of()
                    : section.getIntegerList(Integer.toString(i + 1));
            if (split.size() != i + 1 || split.stream().anyMatch(n -> n < 0)
                    || split.stream().mapToInt(Integer::intValue).sum() != 10) {
                split = new ArrayList<>();
                for (int n : fallback[i]) split.add(n);
            }
            double[] weights = new double[split.size()];
            for (int j = 0; j < weights.length; j++) weights[j] = split.get(j) / 10.0;
            result.put(i + 1, weights);
        }
        return result;
    }

    private static int level(ItemStack item, String family) {
        if (item == null || item.getType().isAir()) return 0;
        int result = 0;
        for (Map.Entry<Enchantment, Integer> enchantment : item.getEnchantments().entrySet()) {
            String id = enchantment.getKey().getKey().getKey();
            String ns = enchantment.getKey().getKey().getNamespace();
            if ((ns.equals("minecraft") && id.equals(family))
                    || (ns.equals("enchantssquared") && (id.equals(family)
                        || id.equals("lesser_" + family) || id.equals("advanced_" + family)
                        || id.equals("supreme_" + family)))) {
                result = Math.max(result, enchantment.getValue());
            }
        }
        return result;
    }

    private Map<String, Double> weights(ItemStack item, boolean armor) {
        List<String> present = new ArrayList<>();
        for (String family : armor ? defenseOrder : offenseOrder) {
            if (level(item, family) > 0) present.add(family);
        }
        if (present.isEmpty()) return Map.of();
        double[] split = (armor ? defense : offense).get(present.size());
        Map<String, Double> result = new HashMap<>();
        for (int i = 0; i < present.size(); i++) {
            result.put(present.get(i), enabled ? split[i] : 1.0);
        }
        return result;
    }

    private static boolean delegated() {
        // Check dynamically: /reload or load order must not result in duplicate damage changes.
        return org.bukkit.Bukkit.getPluginManager().isPluginEnabled("EnchantsSquared");
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDamaged(EntityDamageEvent event) {
        if (!enabled || delegated() || !(event.getEntity() instanceof LivingEntity victim)
                || victim.getEquipment() == null) return;
        var cause = event.getCause();
        boolean fire = switch (cause) {
            case FIRE, FIRE_TICK, LAVA, HOT_FLOOR, CAMPFIRE, MELTING -> true;
            default -> false;
        };
        boolean blast = cause == EntityDamageEvent.DamageCause.BLOCK_EXPLOSION
                || cause == EntityDamageEvent.DamageCause.ENTITY_EXPLOSION;
        boolean projectile = cause == EntityDamageEvent.DamageCause.PROJECTILE;
        boolean general = switch (cause) {
            case STARVATION, VOID, KILL, SONIC_BOOM, WORLD_BORDER -> false;
            default -> true;
        };
        double raw = 0;
        double scaled = 0;
        boolean mixed = false;
        for (ItemStack armor : victim.getEquipment().getArmorContents()) {
            Map<String, Double> w = weights(armor, true);
            mixed |= w.size() > 1;
            if (general) {
                int lvl = vanilla(armor, "protection");
                raw += lvl;
                scaled += lvl * w.getOrDefault("protection", 0.0);
            }
            if (fire) {
                int lvl = vanilla(armor, "fire_protection");
                raw += 2.0 * lvl;
                scaled += 2.0 * lvl * w.getOrDefault("fire_protection", 0.0);
            }
            if (blast) {
                int lvl = vanilla(armor, "blast_protection");
                raw += 2.0 * lvl;
                scaled += 2.0 * lvl * w.getOrDefault("blast_protection", 0.0);
            }
            if (projectile) {
                int lvl = vanilla(armor, "projectile_protection");
                raw += 2.0 * lvl;
                scaled += 2.0 * lvl * w.getOrDefault("projectile_protection", 0.0);
            }
            if (cause == EntityDamageEvent.DamageCause.FALL) {
                double feather = 3.0 * vanilla(armor, "feather_falling");
                raw += feather;
                scaled += feather;
            }
        }
        if (!mixed) return;
        double before = Math.min(0.80, 0.04 * raw);
        double after = Math.min(0.80, 0.04 * scaled);
        event.setDamage(Math.max(0, event.getDamage() * (1.0 - after) / (1.0 - before)));
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onAttack(EntityDamageByEntityEvent event) {
        if (!enabled || delegated()) return;
        if (event.getCause() != EntityDamageEvent.DamageCause.ENTITY_ATTACK
                && event.getCause() != EntityDamageEvent.DamageCause.ENTITY_SWEEP_ATTACK) return;
        if (!(event.getDamager() instanceof LivingEntity attacker)
                || attacker.getEquipment() == null) return;
        ItemStack weapon = attacker.getEquipment().getItemInMainHand();
        Map<String, Double> w = weights(weapon, false);
        if (w.size() < 2) return;
        double correction = 0;
        for (String family : OFFENSE) {
            int level = vanilla(weapon, family);
            if (level <= 0) continue;
            double bonus = switch (family) {
                case "sharpness" -> 0.5 * level + 0.5;
                case "smite" -> Tag.ENTITY_TYPES_SENSITIVE_TO_SMITE.isTagged(event.getEntityType())
                        ? 2.5 * level : 0.0;
                case "bane_of_arthropods" -> Tag.ENTITY_TYPES_SENSITIVE_TO_BANE_OF_ARTHROPODS.isTagged(event.getEntityType())
                        ? 2.5 * level : 0.0;
                default -> 0.0;
            };
            correction += bonus * (1.0 - w.getOrDefault(family, 0.0));
        }
        if (correction > 0) event.setDamage(Math.max(0, event.getDamage() - correction));
    }

    private static int vanilla(ItemStack item, String family) {
        if (item == null || item.getType().isAir()) return 0;
        for (Map.Entry<Enchantment, Integer> entry : item.getEnchantments().entrySet()) {
            if (entry.getKey().getKey().getNamespace().equals("minecraft")
                    && entry.getKey().getKey().getKey().equals(family)) return entry.getValue();
        }
        return 0;
    }
}
