package me.athlaeos.valhallammo.utility;

/**
 * Physical-damage middle ground: incoming heavy hits gradually counter armor,
 * while toughness provides a separate, diminishing damage-reduction layer.
 *
 * Resistance and armor penetration must be resolved before calling this.
 * Damage absorption and the minimum damage floor are applied by the caller.
 */
public final class HybridArmorFormula {
    private HybridArmorFormula() {}

    public static double calculate(double damage, double armor, double toughness,
                                   double armorGrowth, double toughnessFactor) {
        if (damage <= 0) return 0;

        double armorCurve = 15 + Math.max(0, damage - 20) * Math.max(0, armorGrowth);
        double effectiveArmor = Math.max(0, armor);
        double effectiveToughness = Math.max(0, toughness);
        return damage * (armorCurve / (armorCurve + effectiveArmor))
                / (1 + effectiveToughness * Math.max(0, toughnessFactor));
    }
}
