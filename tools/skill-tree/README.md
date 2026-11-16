# ValhallaMMO Skill Tree Layout

This directory contains the **latest shared skill-tree planner snapshot** (2026-11-16) on the `feature/skill-tree-config-overhaul` branch.

- `Skill_Tree_Planner.html`: original interactive layout editor uploaded by the maintainer, including manual/automatic connector routing.
- `skill-tree-layout.json`: exact embedded planner data for all 16 skills and 1154 perks. This is a **layout/design snapshot**, not a complete progression configuration.

**Important:** The branch snapshot does not yet include the repaired YAML ZIP. Do not substitute the layout JSON for plugin configuration files: it doesn't contain complete perk rewards or XP configuration. Use the corrected `valhallammo_skills_connectors_fixed.zip` for game testing until the actual YAML files are committed.

The more recent standalone HTML editor includes direct YAML import/export and portable project save/load. The repository's planner snapshot currently represents the original uploaded HTML; the standalone enhanced copy is supplied separately.

## Apotheosis enchanting stats in the overhauled tree

This **same branch** now contains the three independent ValhallaMMO
enchanting stats `ENCHANTING_ETERNA`, `ENCHANTING_QUANTA`, and
`ENCHANTING_ARCANA`. The redesigned planner has **53** Enchanting nodes,
including three new visible perks in a connected horizontal path:

```text
Mental Focus (2,10)
    → Eterna Attunement (5,10), level 35: +8 Eterna
        → Quanta Attunement (7,10), level 55: +12 Quanta
            → Arcana Attunement (9,10), level 75: +15 Arcana
```

The **HTML editor's embedded data and JSON snapshot are synchronized**.
Perk rewards, names and translations are also present in the bundled
`core/src/main/resources/skills/enchanting_progression.yml` and
`core/src/main/resources/languages/en-us.json`.

**Source-layout caveat:** The skill-tree planner still represents the newest
redesigned layout, while the *bundled* `enchanting_progression.yml` is the
older layout with the new perks merged safely in free positions. Export or
merge those three IDs/reward definitions into the final overhaul progression
YAML before deploying your corrected ZIP. Do not overwrite a server's
existing custom progression with the bundled file.

Run the repeatable static check locally with PyYAML installed:

```bash
python tools/skill-tree/validate_apothic_tree.py
```

[Detailed integration and migration note](../../docs/APOTHIC_ENCHANTING_STATS_OVERHAULED_TREE.md).
The checks validate source/planner configuration; they do not compile a JAR,
run Paper, or enable Apothic enchanting mechanics.
