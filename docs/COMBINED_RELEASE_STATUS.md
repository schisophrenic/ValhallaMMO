# Combined Purpur 26 beta release staging

Target: one distributable Purpur 26 ValhallaMMO JAR, not one JAR per module.

## Consolidated in this branch
- Base: feature/apotheosis-attributes-integration (combat/hybrid armor, Apothic attributes, Purpur 26 distribution).
- Ported: enchantment-priority-compat-26-3 runtime priority handler, listener registration, configurable split tables.
- Ported: crop-and-kettle-food-support FoodPropertyManager changes.
- Ported: premium alchemy_progression.yml changes.
- Ported: refactor/ponytail-resourcepack-version-map and refactor/ponytail-zip-helper.
- Ported: generated-POM ignore rules from chore/ponytail-drop-generated-poms.
- Ported: skill-tree documentation and validator (not the whole overlapping skill-tree implementation).
- Preserved latest master root Maven ordering, README instructions, and SkillTreeMenu language fix.

## Not blindly merged
- lapi is a legacy branch hundreds of commits behind master; its interfaces require a separate port.
- feature/skill-tree-config-overhaul includes overlapping changes to enchanting stats, ItemAttributesRegistry and player profiles. Runtime sources need manual reconciliation with newer combat logic; large server-config/planner JSON files were not promoted.
- Build-generated POM deletion files were not automatically propagated. Ignore rules were.
- Older standalone feature branches may be contained in the base or superseded by newer code; duplicating them would be unsafe.
- Other repositories (including EnchantsSquared) require separate plugin JARs; they are not shaded wholesale into ValhallaMMO.

## Packaging
Workflow: .github/workflows/build-combined-purpur26-beta.yml
Builds Spigot 26.1.2's nonpublished server dependency, compiles the version-specific module, shades dist-purpur26, verifies main/NMS/priority classes and plugin.yml, uploads the resulting JAR when successful.

## Release gate
NOT PRODUCTION READY. At this stage no successful GitHub Actions compilation or running server test has been verified.
Required: successful artifact build, startup on Purpur 26.3, combat protection tests with and without EnchantsSquared, skill GUI translations, recipe/loot plugin checks, and fallback compatibility.
Master is not updated by this staging branch or its draft PR.
