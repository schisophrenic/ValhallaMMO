package me.athlaeos.valhallammo.utility;

/**
 * Small dependency-free regression check; run this main method with assertions
 * enabled, or invoke it from any build test runner.
 */
public final class ResistanceCalculatorTest {
    private ResistanceCalculatorTest() {}

    private static void expect(double actual, double expected, String label) {
        if (Math.abs(actual - expected) > 0.000001) {
            throw new AssertionError(label + ": expected " + expected + ", got " + actual);
        }
    }

    public static void main(String[] args) {
        expect(ResistanceCalculator.damageMultiplier(0, 1, 0, 0), 0, "100% general resistance");
        expect(ResistanceCalculator.damageMultiplier(1, 0, 0, 0), 0, "100% typed resistance");
        expect(ResistanceCalculator.damageMultiplier(0, 0.5, 0, 0), 0.5, "50% resistance");
        expect(ResistanceCalculator.damageMultiplier(0, -1, 0, 0), 2, "-100% general resistance");
        expect(ResistanceCalculator.damageMultiplier(-1, 0, 0, 0), 2, "-100% typed resistance");
        expect(ResistanceCalculator.damageMultiplier(0, -2, 0, 0), 3, "-200% resistance");
        expect(ResistanceCalculator.damageMultiplier(0.5, 0.5, 0, 0), 0.25, "two independent 50% layers");

        expect(ResistanceCalculator.damageMultiplier(0, 1, 0, 0.5), 0.5, "pierce immunity by 50 percentage points");
        expect(ResistanceCalculator.damageMultiplier(1, 0, 0, 1), 1, "pierce immunity completely");
        expect(ResistanceCalculator.damageMultiplier(0, 0, 0, 1), 2, "pierce zero resistance into -100% weakness");
        expect(ResistanceCalculator.damageMultiplier(0, 0.5, 0, 1), 1.5, "pierce into negative resistance");
        expect(ResistanceCalculator.damageMultiplier(0, 0.5, 0.5, 0), 0.75, "shred half of positive resistance");
        expect(ResistanceCalculator.damageMultiplier(0, -1, 1, 0), 2, "shred does not remove existing weakness");
        expect(ResistanceCalculator.damageMultiplier(0, 1.5, 0, 0.5), 0, "overcap preserves 100% immunity");
        expect(ResistanceCalculator.damageMultiplier(0, 1.5, 0.5, 0), 0.25, "shred penetrates overcap");
        expect(ResistanceCalculator.damageMultiplier(0.5, 0.5, 0.5, 0.25), 0.875, "stacked shred and pierce");

        System.out.println("ResistanceCalculatorTest passed");
    }
}
