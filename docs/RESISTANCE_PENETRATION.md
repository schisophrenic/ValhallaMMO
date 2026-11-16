# Resistance and vulnerability: Apothic-inspired integration

**Branch:** `feature/apotheosis-attributes-integration`  
**Status:** Implementation committed; standalone Java calculation test passed. **Full ValhallaMMO build and Paper/Purpur runtime tests have not been run.**

## Existing resistance behavior preserved

ValhallaMMO already applies both typed and generic resistance. It is intentionally possible to use **negative resistance** to make a target vulnerable:

```
0%    => 1.00x incoming damage
+50%  => 0.50x incoming damage
+100% => 0.00x incoming damage (immune at this layer)
-50%  => 1.50x incoming damage
-100% => 2.00x incoming damage
-200% => 3.00x incoming damage
```

These are multipliers **before armor and further defenses**. Armor, toughness, `PVP_RESISTANCE`, shields, one-shot protection, world restrictions, damage cancellation, and immunity frames remain separate; -100% resistance does not guarantee exactly double final *HP lost* once those layers apply.

When no penetration is present, the original two-layer stacking is preserved:

```text
baseDamage * max(0, 1 - typedResistance) * max(0, 1 - genericResistance)
```

## New attacker item/potion attributes

Both new stats use Valhalla's existing `AccumulativeStatManager` offensive stat sources, equipment attribute registration, custom potion registration, and `en-us.json` translations. They start at **zero** unless an item, potion, or other registered source grants them.

- `RESISTANCE_SHRED`: removes a fraction of the target's **positive combined resistance**. Example: 50% total resistance and 50% shred leaves 25% resistance.
- `RESISTANCE_PIERCE`: subtracts **flat percentage points** of combined resistance. Example: 50% resistance minus 100 percentage points = -50% resistance, causing 1.5x damage before armor.

Shred runs **before** pierce. Both apply at the same existing resistance stage, once per damage instance, not again for each separate resistance source. The calculation preserves overcapped resistance (e.g., 150%) when penetration is used, so 50% pierce against 150% resistance still leaves full immunity. A negative resistance is not incorrectly converted into positive resistance by shred.

## Worked examples

One incoming 100-damage attack, assuming zero armor, no additional mitigation, and zero resistance in any layer not specified:

| Defender combined resistance | Weapon shred | Weapon pierce | Damage after resistance |
| --- | --- | --- | --- |
| 100% | 0% | 0% | 0 (immune) |
| 100% | 50% | 0% | 50 |
| 100% | 0% | 50% | 50 |
| 100% | 0% | 100% | 100 |
| 50% | 0% | 100% | 150 |
| 0% | 0% | 100% | 200 |
| -100% | 100% | 0% | 200 |
| 150% | 0% | 50% | 0 |
| 150% | 50% | 0% | 25 |

Note: A target with 50% **general** and 50% **elemental** resistance takes 25% of damage (the resistances multiply), not 0%. Penetration uses the effective combined reduction, so it cannot be counted twice.

## Different meanings of immunity

- **Resistance immunity:** `DAMAGE_RESISTANCE` or matching `*_RESISTANCE` reaches 100%, blocking that damage through resistance. **The new weapon penetration can reduce this immunity.**
- **Hurt immunity frames:** The existing `IMMUNITY_REDUCTION` item stat is used in `IMMUNITY_FRAME_MULTIPLIER`; it changes invulnerability time, **not** damage resistance.
- **True damage:** Valhalla's `trueDamage` types retain their established general-resistance bypass; the new weapon penetration does not increase true or environmental damage.
- **Other defenses:** `PVP_RESISTANCE`, frontal/back damage multipliers, armor, shields, other plugins' cancellation, or one-shot protection can still make a target effectively immune. `RESISTANCE_PIERCE` is **not** an automatic bypass for those systems.

## Where the implementation lives

- `utility/ResistanceCalculator.java`: deterministic combined resistance, shred, pierce, weakness, overcap math.
- `listeners/EntityDamagedListener.java`: reads typed resistance, general resistance, attacker penetration, and applies result **once**.
- `playerstats/AccumulativeStatManager.java`: offensive source registration using equipment and potion stats.
- `item/ItemAttributesRegistry.java`: item attribute registration/lore.
- `potioneffects/PotionEffectRegistry.java`: optional potion effect registration.
- `languages/en-us.json`: item and potion text.
- `src/test/.../ResistanceCalculatorTest.java`: 16 dependency-free regression checks for zero, negative, immunity, overcap, mixed resistance, and shred/pierce.

## Testing / balance safeguards

1. Build the full plugin for supported Paper and Purpur versions, then test on 1.21.11 and 26.3.
2. Try +100% and -100% typed, general, and mixed resistances without offensive stats (must match prior results).
3. Apply `RESISTANCE_PIERCE` and `RESISTANCE_SHRED` to actual weapon item data and potions. Confirm the stat appears in item lore and contributes exactly once in melee, projectile, and elemental attacks.
4. Verify 100% `PVP_RESISTANCE` and one-shot protection remain independent. Confirm `IMMUNITY_REDUCTION` still affects invulnerability frames only.
5. Test over-100% resistance, attacker gear changes, cache invalidation, on-hit healing, high-health targets (100+ HP), and extra elemental damage.
6. Coordinate the default item-roll caps separately with ValhallaEquipment; no automatic new random-roll chance has been introduced by this change.

Source references:
- [Valhalla damage calculation](https://github.com/schisophrenic/ValhallaMMO/blob/master/core/src/main/java/me/athlaeos/valhallammo/listeners/EntityDamagedListener.java)
- [Apothic combat rules](https://github.com/Shadows-of-Fire/Apothic-Attributes/blob/26.1/src/main/java/dev/shadowsoffire/apothic_attributes/api/ALCombatRules.java)
