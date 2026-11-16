package me.athlaeos.valhallammo.utility;

import java.util.function.DoublePredicate;

/**
 * Apothic-style additive critical hits with diminishing bonus damage.
 * All crits use the same base hit, so damage modifiers are never multiplied
 * by themselves for each additional critical strike.
 */
public final class MultiCritCalculator {
    private MultiCritCalculator() {}

    public static int rollCount(double chance, boolean forcedFirst, int maxHits, DoublePredicate roll) {
        int limit = Math.max(1, maxHits);
        int hits = forcedFirst ? 1 : 0;
        double remaining = Math.max(0, chance - hits);
        while (hits < limit && remaining > 0 && roll.test(Math.min(1, remaining))) {
            hits++;
            remaining--;
        }
        return hits;
    }

    public static double multiplier(double firstMultiplier, int hits, double diminishing) {
        double bonus = Math.max(0, firstMultiplier - 1);
        double decay = Math.max(0, Math.min(1, diminishing));
        double result = 1;
        for (int i = 0; i < hits; i++) {
            result += bonus;
            bonus *= decay;
        }
        return result;
    }
}
