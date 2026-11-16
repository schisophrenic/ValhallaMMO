# Apotheosis Enchanting Attributes in the Overhauled Skill Tree

**Development branch:** `feature/skill-tree-config-overhaul`  
**Date:** 2026-11-16  
**Status:** Three registered ValhallaMMO stats, item attributes, configured
perk rewards and planner nodes. Static consistency check **passed**.
**No runtime Apotheosis enchanting behavior or tested plugin binary yet.**

## Confirmed user design

Eterna, Quanta and Arcana must be three **different, configurable**
enchanting stats. Their player sources and perks should belong in ValhallaMMO,
and EnchantsSquared should consume them only when its optional
Apotheosis-style enchanting integration is enabled.

**The NG tier system is independent.** Unlocking Lesser/Advanced/Supreme
enchantments still requires the appropriate NG/perk and Enchanting Quality.
No Eterna, Quanta or Arcana values are checked by the tier prerequisites,
and these values are not added to `ENCHANTING_QUALITY`.

## Implemented registrations

| ValhallaMMO collector | Player Enchanting-profile stat | Item attribute | Enabled by default |
|---|---|---|---|
| `ENCHANTING_ETERNA` | `apothicEterna` | `ENCHANTING_ETERNA` | Stat available; base zero |
| `ENCHANTING_QUANTA` | `apothicQuanta` | `ENCHANTING_QUANTA` | Stat available; base zero |
| `ENCHANTING_ARCANA` | `apothicArcana` | `ENCHANTING_ARCANA` | Stat available; base zero |

These collectors use the standard `ProfileStatSource` plus
`AttributeSource`, so the future EnchantsSquared integration can query
the effective accumulated stat in one place. Gear attributes can be assigned
by other equipment/trinket configuration systems; they are **not**
automatically added to the default randomized equipment pool.

The Enchanting profile registers each float with `perkReward()`, so
ValhallaMMO's existing `Profile.registerPerkRewards()` generates the
corresponding `enchanting_apothic*_add` and `_set` perk modifiers.

## Connected path in the redesigned Enchanting planner

| Perk ID | Display name | Position | Player level | Perk reward |
|---|---|---|---|---|
| `enchanting_perk_b1` | Mental Focus | (2,10) | Existing | Existing |
| `enchanting_perk_apothic_eterna` | Eterna Attunement | (5,10) | 35 | `enchanting_apothiceterna_add: 8` |
| `enchanting_perk_apothic_quanta` | Quanta Attunement | (7,10) | 55 | `enchanting_apothicquanta_add: 12` |
| `enchanting_perk_apothic_arcana` | Arcana Attunement | (9,10) | 75 | `enchanting_apothicarcana_add: 15` |

Each new perk costs one perk point. Requirements form one chain:
**Mental Focus → Eterna → Quanta → Arcana**. All new nodes are
non-hidden and use the planner's auto-connected relationship flag.
Coordinates were checked not to overlap other visible nodes.

Both `tools/skill-tree/skill-tree-layout.json` and the
`Skill_Tree_Planner.html` embedded data contain the **same 53 Enchanting
nodes**; the other 15 skills were left unchanged.

## Bundled YAML and actual server configuration

The branch also includes matching perk IDs, prerequisite chains, level
requirements, rewards and localization in:
- `core/src/main/resources/skills/enchanting_progression.yml`
- `core/src/main/resources/languages/en-us.json`

However, the repository's bundled progression YAML predates the uploaded
*overhauled layout*. Its original perk coordinates differ from the
skill-tree planner's redesigned layout. The three new nodes were added
to **unused positions in that legacy YAML**, while the planner has the
coordinates shown above.

**Deployment note:** The planner's JSON is a layout snapshot, not a full
plugin progression config. Before deploying the user's repaired overhauled
YAML ZIP, import the three new perk IDs, their rewards and localization
into that final exported YAML and preserve its existing connectors.
Do not replace or reset player skill data or an installed customized
`enchanting_progression.yml`. The plugin only calls
`saveResource(..., false)` for that file, so updating the JAR alone
does not replace an already-installed custom progression file.

## What this implementation intentionally does NOT do

- No Netherite, NG or `ENCHANTING_QUALITY` rule changes.
- No Eterna-controlled enchant levels, Quanta roll randomness, Arcana
  rarity weighting, shelf scanning, XP cost changes or enhanced enchanting UI.
- No direct use or loading of the NeoForge Apothic Enchanting mod.
- No automatic effects for the three stats until the EnchantsSquared
  Apotheosis integration consumes them.
- No claims that the older independent
  `feature/apotheosis-attributes-integration` branch is the deployment
  base. The **overhauled branch** is now the intended ValhallaMMO base.

## Verification

The automated static validator checks:
- All three ValhallaMMO collector names, item attributes, profile properties;
- All three perk reward keys, values, prerequisites and levels;
- Non-overlapping visible nodes and internal connections;
- Localization keys and readable unlock messages;
- Exact equality of planner JSON and HTML-embedded skill data;
- Unchanged presence of Enchanting Quality and NG perk tree nodes.

Run `python tools/skill-tree/validate_apothic_tree.py` with PyYAML
installed. GitHub Actions static validation:
https://github.com/schisophrenic/ValhallaMMO/actions/runs/43807233482
— **passed**.

**Maven compilation not verified for this branch.** The earlier CI
compilation on the Apotheosis Attributes branch failed during dependency
resolution (including `dev.lone:api-itemsadder:4.0.10` via an inaccessible
third-party Maven repository), *before* a meaningful code compilation.
See https://github.com/schisophrenic/ValhallaMMO/actions/runs/43805711858 .
This does not establish a Java error, and no usable release JAR was
created by the new static validation run.

## Next integration boundary

The separate EnchantsSquared branch
`feature/apotheosis-apothic-enchanting-integration`
should read `AccumulativeStatManager.getStats("ENCHANTING_ETERNA", player, false)`
(and `QUANTA`, `ARCANA`) on an enchanting snapshot. Combine these
player values with the table's *separate* shelf stats; use transparent
capping and deterministic rolls, without double-applying ValhallaMMO
skill perks or altering NG tier eligibility.
