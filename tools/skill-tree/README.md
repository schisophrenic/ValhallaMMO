# ValhallaMMO Skill Tree Layout

This directory contains the **latest shared skill-tree planner snapshot** (2026-11-16) on the `feature/skill-tree-config-overhaul` branch.

- `Skill_Tree_Planner.html`: original interactive layout editor uploaded by the maintainer, including manual/automatic connector routing.
- `skill-tree-layout.json`: exact embedded planner data for all 16 skills and 1154 perks. This is a **layout/design snapshot**, not a complete progression configuration.

**Important:** The branch snapshot does not yet include the repaired YAML ZIP. Do not substitute the layout JSON for plugin configuration files: it doesn't contain complete perk rewards or XP configuration. Use the corrected `valhallammo_skills_connectors_fixed.zip` for game testing until the actual YAML files are committed.

The more recent standalone HTML editor includes direct YAML import/export and portable project save/load. The repository's planner snapshot currently represents the original uploaded HTML; the standalone enhanced copy is supplied separately.
