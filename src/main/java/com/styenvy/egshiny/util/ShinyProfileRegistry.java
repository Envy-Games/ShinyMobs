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
     * Zombies and skeletons get "juiced" profiles by default.
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
                false,
                true,                  // equip netherite even if global is off
                base.maxEnchantments(),
                base.dropChancePerItem(),
                base.useGlow(),
                false,                 // not random color
                "dark_green",          // fixed color for zombies
                "egshiny:shiny/zombie" // shinyLootTableId – custom loot table for shiny zombies
        );
        PROFILES.put(EntityType.ZOMBIE, zombieProfile);

        // Skeletons: slightly tankier but much higher damage, gray glow, always netherite
        ShinyProfile skeletonProfile = new ShinyProfile(
                base.minHealthMultiplier() * 1.2,
                base.maxHealthMultiplier() * 1.2,
                base.fixedHealthMultiplier() * 1.2,
                base.useRandomHealth(),
                base.damageMultiplier() * 1.4,
                false,
                true,                    // always equip netherite
                base.maxEnchantments(),
                base.dropChancePerItem(),
                base.useGlow(),
                false,                   // fixed color, not random
                "gray",                  // fixed color for skeletons
                "egshiny:shiny/skeleton" // shinyLootTableId – custom loot table for shiny skeletons
        );
        PROFILES.put(EntityType.SKELETON, skeletonProfile);

        // --------------------------------------------------------------------
        // New profiles with loot tables
        // --------------------------------------------------------------------

        // 1. Creeper – not hard, big damage boost, creeper-green glow
        ShinyProfile creeperProfile = new ShinyProfile(
                base.minHealthMultiplier() * 1.2,
                base.maxHealthMultiplier() * 1.2,
                base.fixedHealthMultiplier() * 1.2,
                base.useRandomHealth(),
                base.damageMultiplier() * 1.6,
                false,                     // not hard
                base.equipNetherite(),
                base.maxEnchantments(),
                base.dropChancePerItem(),
                base.useGlow(),
                false,
                "green",
                "egshiny:shiny/creeper"
        );
        PROFILES.put(EntityType.CREEPER, creeperProfile);

        // 2. Wither – hard, very tanky and strong, dark_gray glow
        ShinyProfile witherProfile = new ShinyProfile(
                base.minHealthMultiplier() * 2.0,
                base.maxHealthMultiplier() * 2.0,
                base.fixedHealthMultiplier() * 2.0,
                base.useRandomHealth(),
                base.damageMultiplier() * 2.0,
                true,                      // hard
                true,                      // always netherite-style gear if applicable
                base.maxEnchantments(),
                base.dropChancePerItem(),
                base.useGlow(),
                false,
                "dark_gray",
                "egshiny:shiny/wither"
        );
        PROFILES.put(EntityType.WITHER, witherProfile);

        // 3. Warden – hard, extremely tanky and strong, dark_aqua glow
        ShinyProfile wardenProfile = new ShinyProfile(
                base.minHealthMultiplier() * 2.5,
                base.maxHealthMultiplier() * 2.5,
                base.fixedHealthMultiplier() * 2.5,
                base.useRandomHealth(),
                base.damageMultiplier() * 2.2,
                true,                      // hard
                false,                     // no armor equip (warden doesn't use armor)
                base.maxEnchantments(),
                base.dropChancePerItem(),
                base.useGlow(),
                false,
                "dark_aqua",
                "egshiny:shiny/warden"
        );
        PROFILES.put(EntityType.WARDEN, wardenProfile);

        // 4. Wither Skeleton – not hard, high damage, dark_gray glow, always netherite
        ShinyProfile witherSkeletonProfile = new ShinyProfile(
                base.minHealthMultiplier() * 1.5,
                base.maxHealthMultiplier() * 1.5,
                base.fixedHealthMultiplier() * 1.5,
                base.useRandomHealth(),
                base.damageMultiplier() * 1.8,
                false,                     // not hard
                true,                      // always netherite
                base.maxEnchantments(),
                base.dropChancePerItem(),
                base.useGlow(),
                false,
                "dark_gray",
                "egshiny:shiny/wither_skeleton"
        );
        PROFILES.put(EntityType.WITHER_SKELETON, witherSkeletonProfile);

        // 5. Pillager – not hard, ranged bruiser, dark_red glow
        ShinyProfile pillagerProfile = new ShinyProfile(
                base.minHealthMultiplier() * 1.3,
                base.maxHealthMultiplier() * 1.3,
                base.fixedHealthMultiplier() * 1.3,
                base.useRandomHealth(),
                base.damageMultiplier() * 1.6,
                false,                     // not hard
                base.equipNetherite(),
                base.maxEnchantments(),
                base.dropChancePerItem(),
                base.useGlow(),
                false,
                "dark_red",
                "egshiny:shiny/pillager"
        );
        PROFILES.put(EntityType.PILLAGER, pillagerProfile);

        // 6. Ravager – hard, raid mini-boss, dark_purple glow
        ShinyProfile ravagerProfile = new ShinyProfile(
                base.minHealthMultiplier() * 2.2,
                base.maxHealthMultiplier() * 2.2,
                base.fixedHealthMultiplier() * 2.2,
                base.useRandomHealth(),
                base.damageMultiplier() * 2.0,
                true,                      // hard
                false,                     // no armor equip (ravager doesn't use armor)
                base.maxEnchantments(),
                base.dropChancePerItem(),
                base.useGlow(),
                false,
                "dark_purple",
                "egshiny:shiny/ravager"
        );
        PROFILES.put(EntityType.RAVAGER, ravagerProfile);

        // 7. Evoker – not hard, spell glass cannon, gold glow
        ShinyProfile evokerProfile = new ShinyProfile(
                base.minHealthMultiplier() * 1.3,
                base.maxHealthMultiplier() * 1.3,
                base.fixedHealthMultiplier() * 1.3,
                base.useRandomHealth(),
                base.damageMultiplier() * 1.7,
                false,                     // not hard
                base.equipNetherite(),
                base.maxEnchantments(),
                base.dropChancePerItem(),
                base.useGlow(),
                false,
                "gold",
                "egshiny:shiny/evoker"
        );
        PROFILES.put(EntityType.EVOKER, evokerProfile);

        // 8. Vindicator – not hard, melee bruiser, red glow
        ShinyProfile vindicatorProfile = new ShinyProfile(
                base.minHealthMultiplier() * 1.4,
                base.maxHealthMultiplier() * 1.4,
                base.fixedHealthMultiplier() * 1.4,
                base.useRandomHealth(),
                base.damageMultiplier() * 1.8,
                false,                     // not hard
                base.equipNetherite(),
                base.maxEnchantments(),
                base.dropChancePerItem(),
                base.useGlow(),
                false,
                "red",
                "egshiny:shiny/vindicator"
        );
        PROFILES.put(EntityType.VINDICATOR, vindicatorProfile);

        // 9. Witch – not hard, support caster, dark_purple glow
        ShinyProfile witchProfile = new ShinyProfile(
                base.minHealthMultiplier() * 1.3,
                base.maxHealthMultiplier() * 1.3,
                base.fixedHealthMultiplier() * 1.3,
                base.useRandomHealth(),
                base.damageMultiplier() * 1.5,
                false,                     // not hard
                false,                     // witches don't really use armor visually
                base.maxEnchantments(),
                base.dropChancePerItem(),
                base.useGlow(),
                false,
                "dark_purple",
                "egshiny:shiny/witch"
        );
        PROFILES.put(EntityType.WITCH, witchProfile);

        // 10. Zombie Villager – not hard, similar to zombie but themed, dark_green glow
        ShinyProfile zombieVillagerProfile = new ShinyProfile(
                base.minHealthMultiplier() * 1.4,
                base.maxHealthMultiplier() * 1.4,
                base.fixedHealthMultiplier() * 1.4,
                base.useRandomHealth(),
                base.damageMultiplier() * 1.3,
                false,                     // not hard
                true,                      // always netherite (fits with shiny undead theme)
                base.maxEnchantments(),
                base.dropChancePerItem(),
                base.useGlow(),
                false,
                "dark_green",
                "egshiny:shiny/zombie_villager"
        );
        PROFILES.put(EntityType.ZOMBIE_VILLAGER, zombieVillagerProfile);
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
