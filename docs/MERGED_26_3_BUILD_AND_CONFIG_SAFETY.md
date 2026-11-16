# ValhallaMMO Purpur 26.3 combined build

The integrated build combines:
- `feature/apotheosis-attributes-integration` (Apothic table attributes, armor and resistance calculations, Purpur 26 NMS fallback)
- `feature/enchantment-priority-compat-26-3` (enchantment priority fallback when EnchantsSquared is not enabled)
- `feature/skill-tree-config-overhaul` (Apothic perk data, skill tree planner, and server-config recipe/item templates)
- Existing `master` build and skill tree menu changes.

## Existing server data is preserved

**Before upgrading, back up `plugins/ValhallaMMO/` and all server data.**
When ValhallaMMO starts it now only copies bundled defaults when the corresponding file does not already exist. Startup deliberately does **not** run automatic config.yml/gui_details.yml migration or rewrite existing YAML via saveConfig. Existing YAML, custom skill layouts, and recipe files are left in place. The `server-config/` files in the repository are **manual templates** and are **not copied into the plugin data folder by the JAR**.

As a result, new bundled config keys are not appended automatically to pre-existing files. Administrators who want the new keys can compare their files against the bundled defaults and add them manually. This is intentional to prevent losing existing configurations.

### Mob levels

For **new installations**, `plugins/ValhallaMMO/mob_stats.yml` now starts with:
```yaml
enabled: false
```
For **existing installations**, your previous `mob_stats.yml` is deliberately retained. If its `enabled` setting is `true`, manually set it to `false` to disable ValhallaMMO mob levels/stat scaling. This does not configure mob scaling supplied by other installed plugins.

### Enchantments and skills

The enchantment-priority fallback runs only when enabled and EnchantsSquared is not enabled. Skill-tree planner assets and `server-config/` presets are separate from the compiled JAR and do not overwrite server customizations. Existing configured progression and recipe files remain authoritative until you manually adopt a template.

## Packaging

GitHub Actions workflow: `Verify Apothic Combat and Package Test JAR`.
It builds `dist-purpur26/target/ValhallaMMO_Purpur26_Apothic_Beta.jar` and uploads the tested package as artifact `valhallammo-purpur26-merged-runnable` after compilation and class checks succeed.

Use only the assembled shaded JAR from the artifact, **not** the unshaded core or NMS module JARs. Install it as the single ValhallaMMO plugin JAR on a backed-up Purpur 26.3 test server, with Java matching the server's requirements. Do not run two ValhallaMMO JARs concurrently. In-game compatibility has not been verified by CI alone.
