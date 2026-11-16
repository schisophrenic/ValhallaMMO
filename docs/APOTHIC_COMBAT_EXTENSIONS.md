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

## Armor model: review only; do not enable automatically

Current default Valhalla expression (`damage_formula_physical`) stays unchanged, including its 10%-of-raw-damage minimum and separate toughness damage reduction.

If the default Apothic armor formula were selected instead, with no penetration, enchantments, or other mitigation:

| Incoming | Armor | Toughness | Valhalla damage | Apothic damage |
| ---: | ---: | ---: | ---: | ---: |
| 30 | 20 | 10 | 9.89 | 12.86 |
| 50 | 20 | 10 | 14.29 | 27.78 |
| 50 | 50 | 10 | 7.69 | 16.67 |
| 100 | 20 | 20 | 14.29 | 71.43 |
| 100 | 50 | 20 | 10.00 | 50.00 |

A 100-HP character wearing 50 armor and 20 toughness would take **10** damage from a 100-point raw hit under Valhalla's default formula, compared with **50** under Apothic's. That is a **5x damage difference**, not merely a cosmetic change, so any armor formula adoption requires balancing against custom ValhallaEquipment weapon damage and the new high HP pools.

No armor profile selector or Apothic armor formula has been added in this change.

**Upstream references**:
- [Apothic critical strike and post-damage lifesteal](https://github.com/Shadows-of-Fire/Apothic-Attributes/blob/26.1/src/main/java/dev/shadowsoffire/apothic_attributes/impl/AttributeEvents.java)
- [Apothic armor and protection rules](https://github.com/Shadows-of-Fire/Apothic-Attributes/blob/26.1/src/main/java/dev/shadowsoffire/apothic_attributes/api/ALCombatRules.java)
- [Apothic ghost health status](https://github.com/Shadows-of-Fire/Apothic-Attributes/blob/26.1/src/main/resources/assets/apothic_attributes/lang/en_us.json)
