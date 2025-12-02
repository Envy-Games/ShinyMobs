package com.styenvy.egshiny.config;

import net.neoforged.neoforge.common.ModConfigSpec;

public class ShinyConfig {
    public static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();
    public static final ModConfigSpec SPEC;
    
    // Spawn Settings
    public static final ModConfigSpec.IntValue MIN_SPAWN_TIME;
    public static final ModConfigSpec.IntValue MAX_SPAWN_TIME;
    public static final ModConfigSpec.IntValue SPAWN_DISTANCE;
    public static final ModConfigSpec.BooleanValue SHOW_SPAWN_MESSAGE;
    public static final ModConfigSpec.BooleanValue SHOW_COORDINATES;
    
    // Mob Settings
    public static final ModConfigSpec.DoubleValue MIN_HEALTH_MULTIPLIER;
    public static final ModConfigSpec.DoubleValue MAX_HEALTH_MULTIPLIER;
    public static final ModConfigSpec.DoubleValue DAMAGE_MULTIPLIER;
    public static final ModConfigSpec.BooleanValue USE_RANDOM_HEALTH;
    public static final ModConfigSpec.DoubleValue FIXED_HEALTH_MULTIPLIER;
    
    // Equipment Settings
    public static final ModConfigSpec.BooleanValue EQUIP_NETHERITE;
    public static final ModConfigSpec.BooleanValue MAX_ENCHANTMENTS;
    public static final ModConfigSpec.DoubleValue DROP_CHANCE_PER_ITEM;
    
    // Visual Settings
    public static final ModConfigSpec.BooleanValue USE_GLOW_EFFECT;
    public static final ModConfigSpec.BooleanValue RANDOM_TEAM_COLOR;
    public static final ModConfigSpec.ConfigValue<String> FIXED_TEAM_COLOR;
    
    // General Settings
    public static final ModConfigSpec.BooleanValue ENABLE_MOD;
    public static final ModConfigSpec.BooleanValue ONE_SHINY_PER_PLAYER;
    public static final ModConfigSpec.BooleanValue NATURAL_DESPAWN;
    
    static {
        BUILDER.push("spawn_settings");
        MIN_SPAWN_TIME = BUILDER
                .comment("Minimum time in minutes between shiny spawns")
                .defineInRange("min_spawn_time", 15, 1, 1440);
        MAX_SPAWN_TIME = BUILDER
                .comment("Maximum time in minutes between shiny spawns")
                .defineInRange("max_spawn_time", 60, 1, 1440);
        SPAWN_DISTANCE = BUILDER
                .comment("Distance from player to spawn shiny mob")
                .defineInRange("spawn_distance", 50, 10, 200);
        SHOW_SPAWN_MESSAGE = BUILDER
                .comment("Show chat message when shiny mob spawns")
                .define("show_spawn_message", true);
        SHOW_COORDINATES = BUILDER
                .comment("Include coordinates in spawn message")
                .define("show_coordinates", true);
        BUILDER.pop();
        
        BUILDER.push("mob_settings");
        MIN_HEALTH_MULTIPLIER = BUILDER
                .comment("Minimum health multiplier for shiny mobs")
                .defineInRange("min_health_multiplier", 2.0, 1.0, 100.0);
        MAX_HEALTH_MULTIPLIER = BUILDER
                .comment("Maximum health multiplier for shiny mobs")
                .defineInRange("max_health_multiplier", 10.0, 1.0, 100.0);
        DAMAGE_MULTIPLIER = BUILDER
                .comment("Damage multiplier for shiny mobs")
                .defineInRange("damage_multiplier", 3.0, 1.0, 100.0);
        USE_RANDOM_HEALTH = BUILDER
                .comment("Use random health multiplier between min and max")
                .define("use_random_health", true);
        FIXED_HEALTH_MULTIPLIER = BUILDER
                .comment("Fixed health multiplier if random is disabled")
                .defineInRange("fixed_health_multiplier", 5.0, 1.0, 100.0);
        BUILDER.pop();
        
        BUILDER.push("equipment_settings");
        EQUIP_NETHERITE = BUILDER
                .comment("Equip shiny mobs with Netherite gear")
                .define("equip_netherite", true);
        MAX_ENCHANTMENTS = BUILDER
                .comment("Apply max level enchantments to gear")
                .define("max_enchantments", true);
        DROP_CHANCE_PER_ITEM = BUILDER
                .comment("Chance for each equipment piece to drop (0.0 - 1.0)")
                .defineInRange("drop_chance_per_item", 0.2, 0.0, 1.0);
        BUILDER.pop();
        
        BUILDER.push("visual_settings");
        USE_GLOW_EFFECT = BUILDER
                .comment("Apply glowing effect to shiny mobs")
                .define("use_glow_effect", true);
        RANDOM_TEAM_COLOR = BUILDER
                .comment("Use random team color for glow effect")
                .define("random_team_color", true);
        FIXED_TEAM_COLOR = BUILDER
                .comment("Fixed team color if random is disabled (dark_blue, dark_green, dark_aqua, dark_red, dark_purple, gold, gray, dark_gray, blue, green, aqua, red, light_purple, yellow, white, black)")
                .define("fixed_team_color", "gold");
        BUILDER.pop();
        
        BUILDER.push("general_settings");
        ENABLE_MOD = BUILDER
                .comment("Enable the EG Shiny Mobs mod")
                .define("enable_mod", true);
        ONE_SHINY_PER_PLAYER = BUILDER
                .comment("Limit to one shiny mob per player at a time")
                .define("one_shiny_per_player", true);
        NATURAL_DESPAWN = BUILDER
                .comment("Allow shiny mobs to despawn naturally")
                .define("natural_despawn", true);
        BUILDER.pop();
        
        SPEC = BUILDER.build();
    }
}
