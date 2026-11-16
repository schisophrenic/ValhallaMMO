package me.athlaeos.valhallammo.utility;

/**
 * Standalone regression checks: run with Java, no Bukkit or test framework.
 * Expected results are post-armor, before DAMAGE_ABSORPTION and the 10% floor.
 */
public final class HybridArmorFormulaTest {
    private HybridArmorFormulaTest() {}

    private static void expect(double actual, double expected, String label) {
        if (Math.abs(actual - expected) > 0.000001) {
            throw new AssertionError(label + ": expected " + expected + ", got " + actual);
        }
    }

    private static double hybrid(double damage, double armor, double toughness) {
        return HybridArmorFormula.calculate(damage, armor, toughness, 0.25, 0.015);
    }

    public static void main(String[] args) {
        expect(hybrid(0, 50, 20), 0, "zero damage");
        expect(hybrid(10, 10, 0), 6, "ordinary hit");
        expect(hybrid(30, 20, 10), 12.1739130435, "moderate hit");
        expect(hybrid(50, 20, 10), 23.0179028133, "medium armor");
        expect(hybrid(50, 50, 10), 13.4932533733, "heavy armor");
        expect(hybrid(100, 20, 20), 48.951048951, "heavy hit medium armor");
        expect(hybrid(100, 50, 20), 31.6742081448, "heavy hit high armor");
        expect(hybrid(100, 50, 0), 41.1764705882, "no toughness");
        expect(hybrid(100, 0, 20), 76.9230769231, "toughness without armor");
        expect(hybrid(100, 0, 0), 100, "no mitigation");
        expect(HybridArmorFormula.calculate(100, 50, 20, 0, 0), 23.0769230769, "no curve growth");

        if (hybrid(100, 50, 20) >= hybrid(100, 50, 0))
            throw new AssertionError("toughness must mitigate");
        if (hybrid(100, 50, 20) <= hybrid(50, 50, 20))
            throw new AssertionError("a heavy hit must remain more dangerous");
        if (hybrid(100, 1000, 1000) < 0)
            throw new AssertionError("mitigation cannot make damage negative");
        System.out.println("HybridArmorFormulaTest passed (14 cases)");
    }
}
