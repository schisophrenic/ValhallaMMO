# Apothic combat beta: build, deploy and in-game smoke test

Target server: **Purpur 26.3**, Java version appropriate for the server. Branch: `feature/apotheosis-attributes-integration`.

## Before you install

1. **Use a copy of the server**, not the production world. Back up the original ValhallaMMO JAR, `plugins/ValhallaMMO/` and player data.
2. Use the GitHub Actions workflow **Verify Apothic Combat and Package Test JAR**. Its first job validates pure Java damage calculations and config. Its second job attempts the full multi-module Maven build. **Only use an artifact if the packaging job succeeds**.
3. To build locally from repository root with existing NMS libraries and Maven: `mvn -B -ntp -DskipTests package`. The shaded distribution JAR comes from `target/ValhallaMMO*.jar`, **not** the core-only module JAR. Check for `me/athlaeos/valhallammo/nms/NMS_v26_R1.class` using `jar tf <file.jar>`.
4. Stop the server. Replace the old ValhallaMMO JAR, ensuring only one ValhallaMMO version is loaded. Restart the server; do **not** use hot plugin reload.
5. Update the existing `plugins/ValhallaMMO/config.yml` yourself; a plugin update might not overwrite previously saved settings. Preserve any unrelated custom settings.

## Required beta config

```yaml
custom_damage_system: true
damage_formula_physical: HYBRID
hybrid_armor_growth: 0.25
hybrid_toughness_factor: 0.015
damage_reduction_cap: 0.1

apothic_multicrit_enabled: true
apothic_multicrit_max_hits: 5
apothic_multicrit_diminishing: 0.85
apothic_overheal_enabled: true
apothic_overheal_max_health_fraction: 0.5
apothic_ghost_health_enabled: true
apothic_ghost_health_regen_delay_seconds: 5
apothic_ghost_health_regen_fraction_per_second: 0.1

health_bar_scaling_enabled: true
armor_effective_types:
  - MELEE:1
  - BLUDGEONING:0.8
  - LIGHTNING:0.25
  - FREEZING:0.25
  - FIRE:0.25
  - BLEED:0.25
  - POISON:0.1
  - NECROTIC:0.1
  - EXPLOSION:0.1
  - RADIANT:0.1
  - MAGIC:0.05
  - PROJECTILE:0.05
  - FALLING:1
```

## In-game setup

ValhallaMMO registers `/valhalla attribute [attribute] [default] <actual>`. This command requires the permission `valhalla.attribute` (normally available to operators). **Hold the target item in your main hand** when running it, then equip the item afterwards if it is armor.

Examples for disposable test items:

```
/valhalla attribute CRIT_CHANCE 1.5 1.5
/valhalla attribute CRIT_DAMAGE 0.5 0.5
/valhalla attribute OVERHEAL 0.25 0.25
/valhalla attribute GHOST_HEALTH 25 25
/valhalla attribute RESISTANCE_PIERCE 0.5 0.5
```

Run the first three on a test attacker item, and `GHOST_HEALTH` on a separate chestplate worn by the target. Run a resistance stat on the victim's chestplate to check Pierce. To avoid confusing results, use one test modifier at a time and record the item's displayed lore after applying.

## Acceptance scenarios

1. **Baseline:** verify plain hits and the new armor curve with the same attacker/target at 0 and then 50 armor, 20 toughness. The expected 100-damage no-resistance value is approximately 31.67 real damage before any other defenses, shield, damage absorption or other plugin effects.
2. **Damage types:** verify Lightning, Freezing, Fire, Bleed, Poison, Necrotic, Explosion, Radiant, Magic, Projectile, Melee and Bludgeoning each use their configured effective armor/toughness values. Confirm typed resistance remains active.
3. **Multi-crits:** try 0%, 100%, 150%, 250% crit chances; check diminishing bonus and one critical event/indicator per hit.
4. **Resistance penetration:** check positive resistance, 100% resistance, and negative resistance; penetration should reduce and then invert mitigation without affecting true damage.
5. **Ghost Health:** test with 25 Ghost Health. First hits should consume the virtual pool before real HP. Continue hitting until exhausted, then stop for five seconds and observe health loss across subsequent hits as regeneration takes effect. Ghost Health has no dedicated HUD value in this beta.
6. **Overheal / Life Steal:** test with a healthy target, a nearly defeated target, and a target protected by absorption/Ghost Health. Healing/shield generation should be limited to *actual real HP lost*, never uncapped raw damage. A killing blow and a multi-element attack must not duplicate the gains.
7. **Survival, shields and events:** test blocking, reflect, one-shot protection, no-damage ticks, death/respawn, quitting, and equipment swaps.
8. **Compatibility:** test alongside ValhallaEquipment, EnchantsSquared, Slimefun and BetterDamage if installed. Watch for doubled damage numbers and repeated healing/indicators.
9. **High-health UI:** verify a player with 100+ max HP still displays only the fixed ten-red-heart bar, and that absorption hearts remain distinct.

### Bug reports

Include the full relevant `logs/latest.log` excerpt, server version, all related plugin versions, actual armor/toughness/resistance/HP attributes, applied item lore, and whether the same issue reproduces with ValhallaEquipment/BetterDamage temporarily removed on a **test copy** of the server.

## Build notes

Recent GitHub Actions failed during Maven dependency resolution (not Java compilation). The 2026 ItemsAdder API is now published on Maven Central as `beer.devs:itemsadder-api:4.0.17`; the branch's `core/pom.xml` was updated accordingly. If Maven still fails on other dependencies, see the job logs. Some older modules reference `customstructures.jar` and `betterstructures.jar` through `systemPath`. The repository does not contain those JAR files, so their required APIs may need to be supplied by their rightful source or migrated to available public dependencies before a complete shaded distribution can be built.

A successful standalone test does **not** substitute for a successful plugin build and in-game behavior checks.
