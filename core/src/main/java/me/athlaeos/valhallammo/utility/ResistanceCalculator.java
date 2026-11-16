package me.athlaeos.valhallammo.utility;

/**
 * Calculates damage taken after Valhalla's general and damage-type resistance.
 * Positive resistance mitigates; negative resistance amplifies incoming damage.
 * Resistance shred and pierce are attacker-side percentages (1.0 = 100%).
 */
public final class ResistanceCalculator {
    private ResistanceCalculator() {}

    public static double damageMultiplier(double typeResistance, double generalResistance,
                                          double resistanceShred, double resistancePierce) {
        // Keep Valhalla's original independent resistance layers when no
        // offensive penetration is present, including negative resistances.
        double multiplier = Math.max(0, 1 - typeResistance) * Math.max(0, 1 - generalResistance);
        if (resistanceShred <= 0 && resistancePierce <= 0) return multiplier;

        // Preserve resistance above 100% as an overcap buffer against
        // penetration instead of treating 150% the same as 100%.
        double overcap = Math.max(0, typeResistance - 1) + Math.max(0, generalResistance - 1);
        double resistance = 1 - multiplier + overcap;

        // Shred affects defensive (positive) resistance, not existing weaknesses.
        if (resistance > 0) {
            double shred = Math.min(1, Math.max(0, resistanceShred));
            resistance *= 1 - shred;
        }
        // Pierce subtracts flat percentage points and can create vulnerability.
        return Math.max(0, 1 - resistance + Math.max(0, resistancePierce));
    }
}
