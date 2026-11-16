package me.athlaeos.valhallammo.utility;

/** Runnable standalone regression checks (no test framework required). */
public final class MultiCritCalculatorTest {
    private MultiCritCalculatorTest() {}

    private static void eq(double got, double expected, String label) {
        if (Math.abs(got - expected) > 0.000001) {
            throw new AssertionError(label + " expected " + expected + " got " + got);
        }
    }

    public static void main(String[] args) {
        eq(MultiCritCalculator.rollCount(0, false, 5, x -> true), 0, "zero chance");
        eq(MultiCritCalculator.rollCount(1, false, 5, x -> true), 1, "100% chance");
        eq(MultiCritCalculator.rollCount(2.6, false, 5, x -> x >= 0.5), 3, "extra fractional crit");
        eq(MultiCritCalculator.rollCount(2.4, false, 5, x -> x >= 0.5), 2, "last roll fails");
        eq(MultiCritCalculator.rollCount(10, false, 4, x -> true), 4, "cap");
        eq(MultiCritCalculator.rollCount(0, true, 5, x -> true), 1, "forced crit");
        eq(MultiCritCalculator.rollCount(2.4, true, 5, x -> x >= 0.3), 3, "forced multi-crit");
        eq(MultiCritCalculator.multiplier(1.5, 0, .85), 1, "no crit multiplier");
        eq(MultiCritCalculator.multiplier(1.5, 1, .85), 1.5, "one crit multiplier");
        eq(MultiCritCalculator.multiplier(1.5, 3, .85), 2.28625, "diminishing additive multiplier");
        eq(MultiCritCalculator.multiplier(.8, 5, .85), 1, "no negative bonus");
        eq(MultiCritCalculator.multiplier(2, 3, 0), 2, "zero decay");
        System.out.println("MultiCritCalculatorTest passed (12 cases)");
    }
}
