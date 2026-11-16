# Apotheosis / Apothic Attributes integration plan

**Branch:** `feature/apotheosis-attributes-integration`  
**Target:** ValhallaMMO on Paper/Purpur 1.21.11 through 26.3  
**Reference:** [Shadows-of-Fire/Apothic-Attributes](https://github.com/Shadows-of-Fire/Apothic-Attributes) (reviewed upstream branch `26.1`).  
**Status:** Architecture and gap analysis only. No Apothic gameplay has been added or enabled by this document.

## Scope and compatibility

Apothic Attributes is a **NeoForge mod** used by Apotheosis. Paper/Purpur cannot load its NeoForge registries, mixins, native attribute types, packets, or client screens directly. This branch is for adapting *behavior and design ideas* into ValhallaMMO's existing server-side plugin systems. It is **not** a runtime plugin-to-mod bridge, a way to read arbitrary modded items, or a way to load the full Apotheosis mod on Paper.

Use ValhallaMMO's own `ItemAttributesRegistry`, `AccumulativeStatManager`, stat sources, `EntityAttackListener`, `EntityDamagedListener`, and `PotionEffectRegistry`. Keep all normal behavior unchanged until a specifically tested feature is enabled. Avoid extra external dependencies and duplicate attack/damage handlers.

## Existing ValhallaMMO equivalents

The following names exist in ValhallaMMO today. **Name overlap does not imply identical default values, caps, stacking operations, or damage order**; verify those before any automatic conversion.

| Apothic attribute | Existing ValhallaMMO counterpart | Proposed action |
| --- | --- | --- |
| `crit_chance` | `CRIT_CHANCE` | Reuse; compare default chance and vanilla critical interactions |
| `crit_damage` | `CRIT_DAMAGE` | Reuse; compare base damage multipliers |
| `dodge_chance` | `DODGE_CHANCE` | Reuse; preserve Valhalla dodge event and facing restrictions |
| `life_steal` | `LIFE_STEAL` | Reuse; avoid healing twice |
| `arrow_damage` | `ARROW_DAMAGE` | Reuse; check projectile ownership and stat snapshots |
| `arrow_velocity` | `ARROW_VELOCITY` | Reuse; check launch-time application |
| `cooldown_reduction` | `COOLDOWN_REDUCTION` | Reuse; audit maximum reduction and eligible abilities |
| `armor_pierce` | `ARMOR_PENETRATION_FLAT` / `ARMOR_FLAT_IGNORED` | Adapt concept; existing Valhalla sources may already include skill perks |
| `armor_shred` | `ARMOR_PENETRATION_FRACTION` / `ARMOR_FRACTION_IGNORED` | Adapt concept; don't apply a second armor reduction pass |
| `fire_damage` | `EXTRA_FIRE_DAMAGE` / fire damage sources | Adapt to existing custom damage type handling |
| `cold_damage` | `EXTRA_FREEZING_DAMAGE` / freezing damage sources | Adapt to existing custom damage type handling |
| `experience_gained` | `VANILLA_EXP_GAIN` / `SKILL_EXP_GAIN` | Keep Minecraft XP and skill XP distinct |
| `healing_received` | `HEALING_BONUS` | Check source/victim semantics before mapping |
| `projectile_damage` | `DAMAGE_RANGED` / `ARROW_DAMAGE` | Check support for non-arrow projectiles before mapping |

## Apothic behaviors not currently established as equivalents

The following need a focused design and tests before implementation:

| Mechanic | Apothic concept | Server-side implementation direction |
| --- | --- | --- |
| `current_hp_damage` | Bonus damage proportional to the target's current health | Add a named damage source in the existing pipeline; apply caps and PvP rules |
| `draw_speed` | Modifies ranged charging speed | Check whether Paper can express it without a client mod for each weapon |
| `ghost_health` | Regenerating extra health | Use a separate tracked temporary-health pool; do not confuse it with vanilla absorption |
| `overheal` | Converts a portion of physical damage into absorption | Integrate once after final resolved damage and after existing life steal |
| `prot_pierce` | Flat protection-point bypass | Requires protection-specific reduction integration, not armor penetration |
| `prot_shred` | Percentage protection-point bypass | Requires protection-specific reduction integration, not armor penetration |
| `elytra_flight` | Attribute enabling flight behavior | Assess server API and permissions first; do not enable flight globally |

Apothic also supplies mob effects such as Bleeding, Detonation, Grievous, Knowledge, Sundering, Vitality, and Flying. Compare each against ValhallaMMO's **existing** potion and custom-damage effects before adding an implementation.

## Combat compatibility requirements

Upstream `ALCombatRules` provides configurable armor/protection calculations and toughness resistance to armor bypass. ValhallaMMO already has its own custom damage pipeline; replacing that globally would risk changing skill, weapon, and enchantment balance.

Implement any optional Apothic-like calculations **at one shared point** in the existing combat pipeline. Document the precise stage in which each value applies (dodge, attacker buffs, target defenses, final damage, healing/on-hit effects). Prevent double-counting from equipment, potions, perks, trinkets, and custom enchantments.

Source inspiration:

- [Attribute definitions and defaults](https://github.com/Shadows-of-Fire/Apothic-Attributes/blob/26.1/src/main/java/dev/shadowsoffire/apothic_attributes/api/ALObjects.java)
- [Armor and protection calculations](https://github.com/Shadows-of-Fire/Apothic-Attributes/blob/26.1/src/main/java/dev/shadowsoffire/apothic_attributes/api/ALCombatRules.java)
- [Mod configuration](https://github.com/Shadows-of-Fire/Apothic-Attributes/blob/26.1/src/main/java/dev/shadowsoffire/apothic_attributes/ALConfig.java)

## Implementation sequence

1. **Audit value semantics** — record every overlapping Valhalla attribute's defaults, caps, operation, equipment slots, potions, and combat listeners. Keep existing public names and saved item data intact.
2. **Attribute compatibility mapping** — optionally expose Apothic-style aliases that resolve to existing Valhalla stats; do not register duplicate effects or attribute IDs.
3. **New server-side mechanics** — implement genuinely missing attributes in small, independently testable units; default new behavior **off** until validated.
4. **GUI and descriptions** — provide a Paper inventory-based attribute inspection screen or chat display if desired. The NeoForge Attributes GUI itself requires client mod code and cannot simply be shown by a plugin. Respect custom Valhalla `en-us.json` translations and optional resource packs.
5. **Full Apotheosis features** — consider affixes, gems, rarity, bosses, and loot *separately*. Those belong to Apotheosis, not necessarily to Apothic Attributes, and should coordinate with `valhallammo-Equipment` rather than compete with its quality/rarity rolls.

## Regression matrix

Use real Paper **and** Purpur servers, with compatible Java runtimes:

- Minecraft 1.21.11: attribute display, stat stacking, PvE/PvP damage, equipment change, death/relog.
- Minecraft 26.3: same tests, plus version-adapter and newer damage API checks.
- Run with custom damage enabled and disabled; confirm no doubled crit, life steal, elemental damage, armor penetration, or potion attributes.
- Test compatibility with ValhallaEquipment and any BetterDamage integration, custom `en-us.json`, netherite upgrades, armor/shield rolls, and ranged weapons.
- Run a full multi-module Maven build before merging and record runtime results. This branch is **not yet certified** compatible.

## Licensing

The upstream [Apothic Attributes LICENSE](https://github.com/Shadows-of-Fire/Apothic-Attributes/blob/26.1/LICENSE) is MIT. If actual upstream implementation code is copied or adapted, retain the required copyright and permission notice. This document summarizes concepts and does not copy implementation code.
