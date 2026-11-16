# One-row health display for high-HP players

**Status:** Implemented on `feature/apotheosis-attributes-integration`, not merged into `master`. Runtime testing still required.

## Why

ValhallaMMO can give players 100+ max HP through skills, armor, potion effects, and other modifiers. Standard Minecraft heart rendering expands to multiple rows. This feature keeps **ten visible red hearts in a single row** at full HP without changing gameplay health.

The implementation follows the client-display technique used by [AuraSkills' `HpTrait`](https://github.com/Archy-X/AuraSkills/blob/master/bukkit/src/main/java/dev/aurelium/auraskills/bukkit/trait/HpTrait.java): Bukkit/Paper's `Player#setHealthScale`. AuraSkills normally permits configurable heart thresholds; this adaptation **always uses 20 displayed health units (10 hearts)** for a single row.

`displayedHealth = actualHealth / actualMaxHealth * 20`

For example, a player with 100/100 actual HP sees 10 red hearts; with 50/100 sees 5 red hearts. The 100 max HP, 50 current HP, damage taken, healing received, armor, toughness, and death handling remain unchanged. A 500/500 player also sees 10 red hearts.

## Configuration

In `plugins/ValhallaMMO/config.yml`:

```yaml
health_bar_scaling_enabled: true
```

Enabled in the integration branch for testing. Set it to `false` before restarting to opt out. This does not change any character stats or give extra health. No AuraSkills dependency is needed.

## Lifecycle

- Apply to players joining, respawning, switching worlds, and already online when ValhallaMMO is enabled.
- In worlds listed under `world_blacklist`, restore the player's earlier display settings.
- On quit or plugin disable, restore the player's earlier scale when ValhallaMMO still owns the displayed scale. Do not overwrite a different scale another plugin applied afterward.
- Because the display scale is constant, no polling of current/max HP is needed; the Bukkit API automatically scales the visible bar as health changes.

## Compatibility notes

- **Golden absorption hearts are separate** and can still extend the HUD. This feature only limits the normal red-heart scale.
- Do not simultaneously enable **AuraSkills health scaling** or another heart-scaling plugin; if running them together, choose which plugin owns the display.
- Players looking at 10 hearts cannot determine precise HP numerically from the icons. Use an existing scoreboard/PlaceholderAPI display if exact HP numbers are required; no action-bar takeover is introduced.
- Paper/Purpur 1.21.11 and 26.3 should be tested in-game; API presence is not proof of the entire plugin's runtime compatibility.

## Manual checks

1. Set max health to 100 or above through a supported source; verify there are exactly 10 normal red hearts at full HP.
2. At half health (for example 50/100), verify 5 red hearts; verify actual HP and damage dealt remain correct.
3. Change max health by equipping/removing gear; verify no display rows are added and damage/HP values stay correct.
4. Test death, respawn, relog, world switch, `world_blacklist`, plugin reload/disable, and third-party HUD conflicts.
5. Test absorption effects separately and document any extra golden hearts.
