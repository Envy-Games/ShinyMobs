package com.styenvy.egshiny.util;

import com.styenvy.egshiny.config.ShinyConfig;
import net.minecraft.world.entity.EntityType;

import java.util.HashMap;
import java.util.Map;

/**
 * Registry that maps entity types to their ShinyProfile.
 * - DEFAULT_PROFILE is built from ShinyConfig.
 * - You can override per-entity behavior here.
 */
public final class ShinyProfileRegistry {

    private static final Map<EntityType<?>, ShinyProfile> PROFILES = new HashMap<>();
    private static ShinyProfile DEFAULT_PROFILE;
    private static boolean initialized = false;

    private ShinyProfileRegistry() {
    }

    private static void ensureInitialized() {
        if (initialized) {
            return;
        }
        initialized = true;

        // Build default profile straight from ShinyConfig so current behavior is preserved.
        DEFAULT_PROFILE = new ShinyProfile(
                ShinyConfig.MIN_HEALTH_MULTIPLIER.get(),
                ShinyConfig.MAX_HEALTH_MULTIPLIER.get(),
                ShinyConfig.FIXED_HEALTH_MULTIPLIER.get(),
                ShinyConfig.USE_RANDOM_HEALTH.get(),
                ShinyConfig.DAMAGE_MULTIPLIER.get(),
                ShinyConfig.EQUIP_NETHERITE.get(),
                ShinyConfig.MAX_ENCHANTMENTS.get(),
                ShinyConfig.DROP_CHANCE_PER_ITEM.get(),
                ShinyConfig.USE_GLOW_EFFECT.get(),
                ShinyConfig.RANDOM_TEAM_COLOR.get(),
                ShinyConfig.FIXED_TEAM_COLOR.get()
        );

        registerDefaultProfiles();
    }

    /**
     * Register built-in per-entity profiles here.
     * Right now: Zombies get a slightly "juiced" profile.
     */
    private static void registerDefaultProfiles() {
        // Zombies: tankier and harder hitting, fixed dark_green glow, always netherite
        ShinyProfile zombieProfile = new ShinyProfile(
                DEFAULT_PROFILE.minHealthMultiplier() * 1.5,
                DEFAULT_PROFILE.maxHealthMultiplier() * 1.5,
                DEFAULT_PROFILE.fixedHealthMultiplier() * 1.5,
                DEFAULT_PROFILE.useRandomHealth(),
                DEFAULT_PROFILE.damageMultiplier() * 1.25,
                true, // equip netherite even if global is off
                DEFAULT_PROFILE.maxEnchantments(),
                DEFAULT_PROFILE.dropChancePerItem(),
                DEFAULT_PROFILE.useGlow(),
                false,               // not random color
                "dark_green"         // fixed color for zombies
        );

        PROFILES.put(EntityType.ZOMBIE, zombieProfile);

        // Everything else currently falls back to DEFAULT_PROFILE.
        // As you expand, you can PROFILES.put(EntityType.SKELETON, someProfile) etc.
    }

    /**
     * Get the profile for a given entity type. Falls back to DEFAULT_PROFILE if none is registered.
     */
    public static ShinyProfile getProfileFor(EntityType<?> type) {
        ensureInitialized();
        return PROFILES.getOrDefault(type, DEFAULT_PROFILE);
    }

    /**
     * Allow other code (or future data-driven loading) to register/override profiles.
     */
    public static void register(EntityType<?> type, ShinyProfile profile) {
        ensureInitialized();
        PROFILES.put(type, profile);
    }
}
