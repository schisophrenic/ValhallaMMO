# Apothic-inspired combat extensions: multi-crits, overheal, ghost health

**Branch:** `feature/apotheosis-attributes-integration`. No merge to `master`.  
**Current status:** Source code and configuration committed; the standalone Java multi-crit calculation test passed 12 cases. **Full project compilation and Paper/Purpur runtime validation are still required.** The existing armor calculation is **unchanged**.

## 1. Multi-crits

Valhalla's existing `CRIT_CHANCE`, `CRIT_DAMAGE`, defender crit-resistance stats, luck handling, one forced-crit flag, custom `EntityCriticallyHitEvent`, and visual indicators are reused. Multiple crits are **one damage event**, not several new damage events.

- 150% chance: first crit guaranteed, 50% chance of a second crit (subject to existing luck).
- 250% chance: two crits guaranteed, 50% chance of a third.
- Each extra crit adds diminished **bonus** damage rather than multiplying previous crits together.
- The existing critical event fires **once** for the total multiplier.
- Sweeping/bleed exclusions, and region restrictions on crits, remain in place.

Formula with `n` procs and first critical damage multiplier `M`:
```
total_multiplier = 1 + (M-1) * (1 + d + d² + ... + d^(n-1))
```
where `d` is the diminishing factor. With `M=1.5`, `d=.85`, and 3 crit procs, the hit deals **2.28625x** damage before other mitigation.

```yaml
apothic_multicrit_enabled: true
apothic_multicrit_max_hits: 5
apothic_multicrit_diminishing: 0.85
```

## 2. Overheal and life steal

The new offensive equipment/potion stat `OVERHEAL` creates vanilla **golden absorption hearts** from the amount of the target's **real HP actually lost**, after defenses, golden absorption, and ghost health.

Example: A target loses 40 real HP, an attacker has 25% `OVERHEAL`, and sufficient shield room remains. The attacker gains 10 absorption points.

Apothic's model caps total overheal absorption at half the attacker's max health; Valhalla's adaptation caps the final **total golden absorption** accordingly to avoid two overlapping untracked shield pools.

```yaml
apothic_overheal_enabled: true
apothic_overheal_max_health_fraction: 0.5
```

Valhalla's existing `LIFE_STEAL` stat now also uses post-defense lost HP. The old raw-damage life-steal code was removed so that a single hit cannot heal twice. The new code fires `EntityRegainHealthEvent` and respects the event's *modified healing amount*, including plugin cancellation. Only damage types already allowed to life-steal by Valhalla's `CustomDamageType.canLifeSteal()` participate in this shared recovery. Stacked elemental hits are recorded separately and reconciled at the queued health-commit step.

Do not confuse `OVERHEAL` with ordinary healing above max HP: it is a distinct offensive stat that creates absorption when damaging an enemy.

## 3. Ghost Health (new Valhalla implementation)

Apothic Attributes 26.1 declares `GHOST_HEALTH` but its English localization says **"Not Yet Implemented"**. Valhalla therefore implements the intended behavior on its own. It is **not** an attempt to load Apothic's NeoForge code on Paper/Purpur.

The defensive equipment/potion stat `GHOST_HEALTH` defines the capacity of a **virtual regenerating shield**, separate from max health and golden absorption:

1. Normal resistance, armor, other defenses apply.
2. Golden absorption hearts are used first.
3. The remaining damage is deducted from Ghost Health.
4. Only damage left over reaches actual HP.

A player at 100/100 real HP with 25/25 Ghost Health can receive 20 post-mitigation damage and remain at 100/100 real HP, with 5/25 Ghost Health remaining. If struck again for 20 (and no golden absorption), 5 goes to Ghost Health and 15 reaches real HP. Ghost Health can recharge after a no-hit delay.

```yaml
apothic_ghost_health_enabled: true
apothic_ghost_health_regen_delay_seconds: 5
apothic_ghost_health_regen_fraction_per_second: 0.1
```

Defaults: after 5 seconds without a hit, restore **10% of max Ghost Health per second**, up to full. The ghost pool starts full when initialized and is cleared on quit/death. It is **invisible on the vanilla HUD**, preserving the previously added fixed ten-red-heart display. A separate plugin UI/placeholder may be desirable for showing the ghost pool to players later.

Ghost Health does not absorb Valhalla's designated true-damage sources, and does not negate PvP one-shot or other existing invulnerability logic. Changing item and potion attributes alters capacity, without modifying `GENERIC_MAX_HEALTH`.

## Combat integration / test checklist

Relevant files:
- `utility/MultiCritCalculator.java`
- `listeners/EntityAttackListener.java`
- `listeners/CombatShields.java`
- `listeners/EntityDamagedListener.java`
- `playerstats/AccumulativeStatManager.java`
- `item/ItemAttributesRegistry.java`
- `potioneffects/PotionEffectRegistry.java`
- `languages/en-us.json`
- `src/test/java/me/athlaeos/valhallammo/utility/MultiCritCalculatorTest.java`

Integration checks required before deployment:
- Crit chances 0%, 50%, 100%, 150%, 250%, forced crit, luck, negative crit resistance, max-hits cap, and WorldGuard restrictions.
- Multi-crits with all custom multiplier modes, Bleed exclusion, PvP and elemental bonus damage.
- Overheal and life steal when target has full absorption, Ghost Health, 100% resistance, or overcapped defenses.
- Overheal clamping at 50% of actual max HP, including 100+ max-HP characters; test the server's maximum-absorption behavior.
- Mixed damage instances and a killing blow; verify healing from **lost HP**, not displayed damage, and no double healing.
- Ghost health item switching, world blacklist, respawn, death, quit, partial depletion and timer reset.
- Server/plugin restart, other damage listeners, and BetterDamage visual compatibility.

## Approved hybrid physical armor formula (new default)

**Implemented in** `utility/HybridArmorFormula.java`, selected with `damage_formula_physical: HYBRID` in `config.yml`. This is a balanced middle ground for high-HP players and ValhallaEquipment. It does not replace Valhalla's resistance, penetration, damage-type armor effectiveness, or protection enchantment sources.

```text
D = post-resistance incoming damage (after penetration / damage-type multipliers)
A = 15 + hybrid_armor_growth * max(0, D - 20)
damage_after_armor = D * A / (A + effectiveArmor)
                     / (1 + effectiveToughness * hybrid_toughness_factor)
```

```yaml
damage_formula_physical: HYBRID
hybrid_armor_growth: 0.25
hybrid_toughness_factor: 0.015
damage_reduction_cap: 0.1
```

The attacker's existing armor penetration is resolved before this formula. The per-damage-type `armor_effective_types` scales effective armor and toughness as before. Higher damage gradually weakens the *relative effectiveness of armor*, but toughness has an independent, consistent mitigation factor rather than scaling its effect directly with each extra damage point. Its **absolute** damage prevented grows for larger hits. The 10% minimum damage floor remains in place, applied to post-resistance incoming damage. The existing `DAMAGE_ABSORPTION` stat is subtracted after formula evaluation; golden absorption and Ghost Health are handled afterwards by the damage listener.

When `damage_formula_physical` is a custom expression instead of `HYBRID`, existing `damage_formula_mode: SET|MULTIPLY` remains supported. The old default expression included `-%absorption%`, so the listener now avoids subtracting that stat twice when the custom expression explicitly includes `%absorption%`.

| Incoming | Armor | Toughness | Valhalla classic | Apothic-style | New hybrid |
| ---: | ---: | ---: | ---: | ---: | ---: |
| 10 | 10 | 0 | 6.00 | 5.00 | 6.00 |
| 30 | 20 | 10 | 9.89 | 12.86 | 12.17 |
| 50 | 20 | 10 | 14.29 | 27.78 | 23.02 |
| 50 | 50 | 10 | 7.69 | 16.67 | 13.49 |
| 100 | 20 | 20 | 14.29 | 71.43 | 48.95 |
| 100 | 50 | 20 | 10.00 | 50.00 | 31.67 |

The table is before resistance, `DAMAGE_ABSORPTION`, actual absorption hearts, Ghost Health, and other effects, with zero armor penetration.

**Migration:** Existing servers that already have a `plugins/ValhallaMMO/config.yml` may retain the old `damage_formula_physical` line. Set it explicitly to `HYBRID` and add the two `hybrid_*` tuning keys (the built-in defaults also work without the keys) to enable the new behavior on an existing deployment. Do not assume changing a bundled resource overwrites a previously saved server configuration.

**Validation:** `HybridArmorFormulaTest.java` contains 14 dependency-free checks. They passed with Java 21. The complete plugin still requires a Maven build and Paper/Purpur runtime testing. The same preset is shared by mobs and players using Valhalla's custom physical-damage system.

**Upstream references**:
- [Apothic critical strike and post-damage lifesteal](https://github.com/Shadows-of-Fire/Apothic-Attributes/blob/26.1/src/main/java/dev/shadowsoffire/apothic_attributes/impl/AttributeEvents.java)
- [Apothic armor and protection rules](https://github.com/Shadows-of-Fire/Apothic-Attributes/blob/26.1/src/main/java/dev/shadowsoffire/apothic_attributes/api/ALCombatRules.java)
- [Apothic ghost health status](https://github.com/Shadows-of-Fire/Apothic-Attributes/blob/26.1/src/main/resources/assets/apothic_attributes/lang/en_us.json)
