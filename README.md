# Shiny Mobs

Adds timed shiny mob spawns with boosted stats, glow styling, optional equipment, custom loot tables, and per-player toggles.

## Commands

- `/shiny on`
- `/shiny off`
- `/shiny status`
- `/shiny hard on`
- `/shiny hard off`

Admin commands:

- `/shiny profilelist`
- `/shiny spawntest [entity]`
- `/shiny clear`
- `/shiny killall`

Commands are registered through NeoForge's server command event under lowercase Brigadier literals.

## Data-Driven Profiles

Shiny profiles load from datapack JSON files at:

```text
data/<namespace>/shiny_profiles/<profile_name>.json
```

Example:

```json
{
  "entity_type": "minecraft:zombie",
  "min_health_multiplier_scale": 1.5,
  "max_health_multiplier_scale": 1.5,
  "fixed_health_multiplier_scale": 1.5,
  "damage_multiplier_scale": 1.25,
  "equip_netherite": true,
  "random_team_color": false,
  "fixed_team_color": "dark_green",
  "shiny_loot_table": "egshiny:shiny/zombie",
  "allowed_dimensions": ["minecraft:overworld"],
  "allowed_biomes": ["#minecraft:is_overworld"]
}
```

Supported keys:

- `entity_type`
- `enabled`
- `min_health_multiplier`, `max_health_multiplier`, `fixed_health_multiplier`, `damage_multiplier`
- `min_health_multiplier_scale`, `max_health_multiplier_scale`, `fixed_health_multiplier_scale`, `damage_multiplier_scale`
- `use_random_health`, `hard_shiny`, `equip_netherite`, `max_enchantments`, `drop_chance_per_item`
- `use_glow`, `random_team_color`, `fixed_team_color`
- `shiny_loot_table`
- `allowed_dimensions`
- `allowed_biomes`

Use direct multiplier values for exact tuning, or `*_scale` keys to scale the server's common config values. `allowed_biomes` accepts biome IDs and biome tags prefixed with `#`. Empty or omitted allowlists mean all dimensions/biomes are allowed.

## Config Notes

`disable_removes_active_shiny` controls whether `/shiny off` removes a player's current active shiny. It defaults to `true`, preserving the original behavior.
