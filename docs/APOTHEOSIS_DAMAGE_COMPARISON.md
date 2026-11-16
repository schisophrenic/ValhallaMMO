# Apothic Attributes vs ValhallaMMO: damage pipeline review

**References:** Apothic Attributes `26.1` branch and ValhallaMMO `master`, inspected 2026-11-16.  
**Scope:** Code review and proposed integration; no combat source changes made.  
**Goal:** Reuse useful Apothic mechanics without duplicate damage calculations or breaking ValhallaMMO skill/equipment balance.

## Source files

- [Apothic ALCombatRules](https://github.com/Shadows-of-Fire/Apothic-Attributes/blob/26.1/src/main/java/dev/shadowsoffire/apothic_attributes/api/ALCombatRules.java)
- [Apothic AttributeEvents](https://github.com/Shadows-of-Fire/Apothic-Attributes/blob/26.1/src/main/java/dev/shadowsoffire/apothic_attributes/impl/AttributeEvents.java)
- [Apothic AuxDmgTracker](https://github.com/Shadows-of-Fire/Apothic-Attributes/blob/26.1/src/main/java/dev/shadowsoffire/apothic_attributes/util/AuxDmgTracker.java)
- [Valhalla EntityAttackListener](https://github.com/schisophrenic/ValhallaMMO/blob/master/core/src/main/java/me/athlaeos/valhallammo/listeners/EntityAttackListener.java)
- [Valhalla EntityDamagedListener](https://github.com/schisophrenic/ValhallaMMO/blob/master/core/src/main/java/me/athlaeos/valhallammo/listeners/EntityDamagedListener.java)
- [Valhalla config defaults](https://github.com/schisophrenic/ValhallaMMO/blob/master/core/src/main/resources/config.yml)

## Overview

| System | Apothic Attributes 26.1 | ValhallaMMO master |
| --- | --- | --- |
| Engine integration | NeoForge hooks + Minecraft mixins; not usable as-is on Paper/Purpur | Bukkit/Paper event listeners, NMS adapters, custom damage |
| Armor reduction | `damage * A / (A + armor)`, where `A = 10` if damage < 20; otherwise `10 + (damage - 20) / 2` | Default config: `damage * 15/(15+armor) * 1/(1+0.001*toughness*damage) - absorptionStat`, then minimum 10% of resisted incoming damage |
| Armor toughness | Reduces armor pierce/shred by 2% per point, capped at 60%; no direct reduction without penetration | Reduces damage directly, more on larger hits; also supports weight classes and sources |
| Penetration | Flat `armor_pierce` and fractional `armor_shred`, applied *before* armor formula | Existing `ARMOR_PENETRATION_FLAT` and `ARMOR_PENETRATION_FRACTION`, built from skills, gear, buffs, potions |
| Enchantment protection | Separate protection-point calculation, 2.5% per point up to 85%, with `prot_pierce` and `prot_shred` | No analogous **explicit protection-point penetration stage** found in `calculateCustomDamage`; test vanilla enchantment interoperability |
| Critical hits | Incoming-damage crit rolls; chance > 100% supports additive follow-up 'overcrits' with crit multiplier dropping 15% each successive crit; vanilla crit multiplier also adjusted | Crit chance/damage with defender resistance and luck; custom crit event and indicators; no equivalent overcrit loop in reviewed attack handler |
| Elemental attacks | Auxiliary typed attacks via `AuxDmgTracker`, keeping per-type immunity frames; fire/cold effects | Extra custom damage instances via `EntityUtils.damage`, prepared events, immunity handling, elemental sources, icons |
| Life steal | On `LivingDamageEvent.Post`, uses actual health damage capped by pre-hit health | Scheduled healing based on `e.getDamage()` and configured elemental base damage, potentially different from actual health lost |
| Dodge | Melee and projectile events; same-target-per-tick deterministic dodge roll to prevent partially dodged multi-hit attacks | Early dodge with optional facing requirement and Bukkit `EntityDodgeAttackEvent`; separate custom-damage instances |
| Extensibility | Built-in configurable math expressions for armor/protection/toughness | Configurable damage expression, broad stats, separate custom damage types, permissions/regions/cooldowns |

## Example — armor only

Assumes no protective enchantments, penetration, general/elemental resistance, absorption, extra damage, or custom expression overrides; no toughness unless specified. Valhalla's `damage_reduction_cap: 0.1` does not affect these examples. Numbers are **raw damage points**, not hearts.

| Incoming | Armor | Toughness | Valhalla damage | Apothic damage |
| ---: | ---: | ---: | ---: | ---: |
| 10 | 10 | 0 | 6.00 | 5.00 |
| 30 | 20 | 0 | 12.86 | 12.86 |
| 50 | 20 | 0 | 21.43 | 27.78 |
| 50 | 20 | 10 | 14.29 | 27.78 |
| 50 | 50 | 10 | 7.69 | 16.67 |

These are *formula comparisons*, not gameplay benchmarks. The difference can be large for the custom weapon damage values and armor/toughness attainable in this server.

## Review findings / potential corrections

### 1. Valhalla default configuration subtracts `DAMAGE_ABSORPTION` twice (high-confidence)

- `config.yml` default `damage_formula_physical` already ends in `- %absorption%`.
- `EntityDamagedListener.calculateCustomDamage` evaluates that expression and, in `SET` mode, returns `Math.max(damageResult - damageAbsorption, minimumFraction * resistedDamage)`.
- Example with 10 incoming, zero armor/toughness and absorptionStat=2: the formula produces 8, then the method returns 6. If absorption stat is intended to subtract only 2 once, expected damage is 8.
- Keep the config backwards-compatible when fixing. Decide which layer owns the deduction; test saved custom formulas that don't include `%absorption%`.

### 2. Valhalla life steal ignores modified `EntityRegainHealthEvent` amount (high-confidence)

- `EntityAttackListener` fires `EntityRegainHealthEvent(td, lifeStealValue, CUSTOM)`, but heals with the original `lifeStealValue` after the event.
- A listener changing `healEvent.setAmount(...)` cannot change the applied amount.
- Correct by honoring `healEvent.getAmount()` and consider moving calculation to an actual resolved-damage stage.
- `e.getDamage()` and the base amounts of elemental sub-attacks can differ from real HP lost after defenses, immunity, and other plugins.

### 3. Formula evaluation and cache hot path (performance review)

- `calculateCustomDamage` formats numeric values into a new formula string per hit; `Utils.eval` uses `MathEval` and caches by the *whole substituted expression* in a static `HashMap`.
- Most distinct damage/armor/toughness combinations generate new keys; caching these results may grow without bound and may add parser overhead to busy combat.
- Prefer compiled formula with supplied variables *or* a safe deterministic fast path for the standard expression. Preserve configuration syntax and semantics. Measure first.

### 4. Valhalla delayed health updates (compatibility review)

- `EntityDamagedListener` queues predicted absorption and health writes for the following server tick, coordinating multiple custom damage instances.
- This is intentional behavior but can conflict with other plugins' healing/death changes and Bukkit's current-event `getFinalDamage`. Test deaths, shield blocking, one-shot protection, regeneration, and multi-type hits before changing it.

### 5. Apothic source should **not** be copied wholesale

- NeoForge modded damage containers, engine mixins, client packets, and registries are not directly available on Paper/Purpur.
- Valhalla has direct gameplay integrations that would be lost by replacing damage listeners with Apothic's system.
- In Apothic, protection points and piercing are distinct from armor. Do not merge them into `ARMOR_PENETRATION_*` or apply armor reduction twice.

## Recommended sequence

1. **Fix and test existing correctness issues** (absorption stat, life-steal event amount, multi-hit ordering) in a separate PR; do not mix fixes with new mechanics.
2. Add a **pure calculation helper and small Java tests** comparing existing, Apothic-like, and optional hybrid formulas for values across 1.21.11–26.3. Enable none by default.
3. If desired, add `damage_formula_profile: valhalla | apothic | hybrid`, retaining `valhalla` as default and preserving custom formula overrides; no invasive combat rewrite.
4. Consider Apothic-style **toughness resisting penetration** and independent **protection-point piercing**, after inspecting Bukkit protection enchantment semantics.
5. Adapt **post-resolved damage life steal** and a single per-attack multi-damage context, integrating Valhalla's custom damage indicators, PvP caps, equipment, potions, skill perks, and BetterDamage hooks.
6. Only then experiment with **overcrits**, overheal, or current-HP bonus damage as opt-in features.

## Tests required before shipping

- Damage values for low, mid, and high hits across armor/toughness 0–50, including negative armor.
- Flat and fractional armor piercing in combination and on armorless targets.
- Protection enchantment levels, blast/fire/feather protection, shields, and armor-bypassing damage.
- Low/normal/>100% crit chance, vanilla jump crits, crit resistance, and projectile attacks.
- PvE/PvP with one-shot protection and invulnerability frames.
- Mixed elemental and physical damage with bleed, reflection, lifesteal and heal modifications from other plugins.
- Both supported servers (Paper and Purpur) on 1.21.11 and 26.3, including ValhallaEquipment, BetterDamage, and the optional resource pack.

**Conclusion:** Keep ValhallaMMO as the engine. Borrow Apothic's cleaner separation between armor vs. protection, piercing vs. toughness, true post-damage healing, and potentially diminishing multi-crits. Its default armor formula is a *different balance model*, not an objectively superior one.
