package com.styenvy.egshiny.util;

import com.styenvy.egshiny.config.ShinyConfig;
import net.minecraft.world.entity.EntityType;

import java.util.HashMap;
import java.util.Map;

/**
 * Registry that maps entity types to their ShinyProfile.
 *
 * - Each EntityType can have at most one ShinyProfile.
 * - Only entity types explicitly registered here (or via register(...)) are eligible
 *   for shiny behavior.
 * - Profiles are typically based on a "base" config-driven profile created from ShinyConfig.
 */
public final class ShinyProfileRegistry {

    private static final Map<EntityType<?>, ShinyProfile> PROFILES = new HashMap<>();
    private static boolean initialized = false;

    private ShinyProfileRegistry() {
    }

    private static void ensureInitialized() {
        if (initialized) {
            return;
        }
        initialized = true;

        registerDefaultProfiles();
    }

    /**
     * Build a base profile from ShinyConfig. This acts as a factory:
     * you can derive per-entity profiles by tweaking these values.
     */
    private static ShinyProfile createBaseProfileFromConfig() {
        return new ShinyProfile(
                ShinyConfig.MIN_HEALTH_MULTIPLIER.get(),
                ShinyConfig.MAX_HEALTH_MULTIPLIER.get(),
                ShinyConfig.FIXED_HEALTH_MULTIPLIER.get(),
                ShinyConfig.USE_RANDOM_HEALTH.get(),
                ShinyConfig.DAMAGE_MULTIPLIER.get(),
                false, // hardShiny (global/default: not hard)
                ShinyConfig.EQUIP_NETHERITE.get(),
                ShinyConfig.MAX_ENCHANTMENTS.get(),
                ShinyConfig.DROP_CHANCE_PER_ITEM.get(),
                ShinyConfig.USE_GLOW_EFFECT.get(),
                ShinyConfig.RANDOM_TEAM_COLOR.get(),
                ShinyConfig.FIXED_TEAM_COLOR.get(),
                null // shinyLootTableId
        );
    }

    /**
     * Register built-in per-entity profiles here.
     * Right now: Zombies get a slightly "juiced" profile.
     */
    private static void registerDefaultProfiles() {
        ShinyProfile base = createBaseProfileFromConfig();

        // Zombies: tankier and harder hitting, fixed dark_green glow, always netherite
        ShinyProfile zombieProfile = new ShinyProfile(
                base.minHealthMultiplier() * 1.5,
                base.maxHealthMultiplier() * 1.5,
                base.fixedHealthMultiplier() * 1.5,
                base.useRandomHealth(),
                base.damageMultiplier() * 1.25,
                true,                  // hardShiny – zombies are tougher by default
                true,                  // equip netherite even if global is off
                base.maxEnchantments(),
                base.dropChancePerItem(),
                base.useGlow(),
                false,                 // not random color
                "dark_green",          // fixed color for zombies
                "egshiny:shiny/zombie" // shinyLootTableId – custom loot table for shiny zombies
        );

        PROFILES.put(EntityType.ZOMBIE, zombieProfile);

        // As you expand, just add more:
        // PROFILES.put(EntityType.SKELETON, skeletonProfile);
        // PROFILES.put(EntityType.CREEPER, creeperProfile);
        // etc.
    }

    /**
     * Get the profile for a given entity type.
     *
     * @return the ShinyProfile for this type, or null if the type
     *         is not configured for shiny behavior.
     */
    public static ShinyProfile getProfileFor(EntityType<?> type) {
        ensureInitialized();
        return PROFILES.get(type);
    }

    /**
     * Allow other code (or future data-driven loading) to register/override profiles.
     */
    public static void register(EntityType<?> type, ShinyProfile profile) {
        ensureInitialized();
        PROFILES.put(type, profile);
    }
}
