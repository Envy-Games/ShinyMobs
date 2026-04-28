package com.styenvy.egshiny.util;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.styenvy.egshiny.EGShiny;
import com.styenvy.egshiny.config.ShinyConfig;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.tags.TagKey;
import net.minecraft.util.GsonHelper;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.biome.Biome;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/**
 * Registry that maps entity types to reloadable datapack-driven ShinyProfiles.
 *
 * Profiles are loaded from data/<namespace>/shiny_profiles/*.json.
 */
public final class ShinyProfileRegistry {

    private static final Gson GSON = new Gson();
    private static final Map<EntityType<?>, ShinyProfile> PROFILES = new HashMap<>();

    private ShinyProfileRegistry() {
    }

    public static ShinyProfile getProfileFor(EntityType<?> type) {
        return PROFILES.get(type);
    }

    public static Set<EntityType<?>> getRegisteredEntityTypes() {
        return Set.copyOf(PROFILES.keySet());
    }

    public static EntityType<?> getRandomShinyEntityType(boolean includeHardProfiles, Random random,
                                                         net.minecraft.server.level.ServerLevel level,
                                                         net.minecraft.core.BlockPos pos) {
        List<EntityType<?>> candidates = new ArrayList<>();
        for (Map.Entry<EntityType<?>, ShinyProfile> entry : PROFILES.entrySet()) {
            ShinyProfile profile = entry.getValue();
            if (profile == null) {
                continue;
            }
            if ((!profile.hardShiny() || includeHardProfiles) && profile.canSpawnAt(level, pos)) {
                candidates.add(entry.getKey());
            }
        }

        if (candidates.isEmpty()) {
            return null;
        }

        return candidates.get(random.nextInt(candidates.size()));
    }

    private static void replaceProfiles(Map<EntityType<?>, ShinyProfile> profiles) {
        PROFILES.clear();
        PROFILES.putAll(profiles);
        EGShiny.LOGGER.info("Loaded {} shiny mob profiles", PROFILES.size());
    }

    public static class ReloadListener extends SimpleJsonResourceReloadListener {
        public ReloadListener() {
            super(GSON, "shiny_profiles");
        }

        @Override
        protected void apply(Map<ResourceLocation, JsonElement> objects, ResourceManager resourceManager, ProfilerFiller profiler) {
            Map<EntityType<?>, ShinyProfile> loadedProfiles = new HashMap<>();

            for (Map.Entry<ResourceLocation, JsonElement> entry : objects.entrySet()) {
                try {
                    JsonObject json = GsonHelper.convertToJsonObject(entry.getValue(), "shiny profile");
                    if (!GsonHelper.getAsBoolean(json, "enabled", true)) {
                        continue;
                    }

                    ResourceLocation entityTypeId = getResourceLocation(json, "entity_type");
                    EntityType<?> entityType = BuiltInRegistries.ENTITY_TYPE.getOptional(entityTypeId)
                            .orElseThrow(() -> new JsonParseException("Unknown entity type: " + entityTypeId));

                    loadedProfiles.put(entityType, parseProfile(json));
                } catch (RuntimeException exception) {
                    EGShiny.LOGGER.error("Failed to load shiny profile {}", entry.getKey(), exception);
                }
            }

            replaceProfiles(loadedProfiles);
        }
    }

    private static ShinyProfile parseProfile(JsonObject json) {
        ShinyProfile base = createBaseProfileFromConfig();

        return new ShinyProfile(
                getScaledDouble(json, "min_health_multiplier", "min_health_multiplier_scale", base.minHealthMultiplier()),
                getScaledDouble(json, "max_health_multiplier", "max_health_multiplier_scale", base.maxHealthMultiplier()),
                getScaledDouble(json, "fixed_health_multiplier", "fixed_health_multiplier_scale", base.fixedHealthMultiplier()),
                GsonHelper.getAsBoolean(json, "use_random_health", base.useRandomHealth()),
                getScaledDouble(json, "damage_multiplier", "damage_multiplier_scale", base.damageMultiplier()),
                GsonHelper.getAsBoolean(json, "hard_shiny", base.hardShiny()),
                GsonHelper.getAsBoolean(json, "equip_netherite", base.equipNetherite()),
                GsonHelper.getAsBoolean(json, "max_enchantments", base.maxEnchantments()),
                GsonHelper.getAsDouble(json, "drop_chance_per_item", base.dropChancePerItem()),
                GsonHelper.getAsBoolean(json, "use_glow", base.useGlow()),
                GsonHelper.getAsBoolean(json, "random_team_color", base.randomTeamColor()),
                GsonHelper.getAsString(json, "fixed_team_color", base.fixedTeamColorKey()),
                getOptionalString(json, "shiny_loot_table"),
                parseResourceLocations(json, "allowed_dimensions"),
                parseBiomeIds(json, "allowed_biomes"),
                parseBiomeTags(json, "allowed_biomes")
        );
    }

    private static ShinyProfile createBaseProfileFromConfig() {
        return new ShinyProfile(
                ShinyConfig.MIN_HEALTH_MULTIPLIER.get(),
                ShinyConfig.MAX_HEALTH_MULTIPLIER.get(),
                ShinyConfig.FIXED_HEALTH_MULTIPLIER.get(),
                ShinyConfig.USE_RANDOM_HEALTH.get(),
                ShinyConfig.DAMAGE_MULTIPLIER.get(),
                false,
                ShinyConfig.EQUIP_NETHERITE.get(),
                ShinyConfig.MAX_ENCHANTMENTS.get(),
                ShinyConfig.DROP_CHANCE_PER_ITEM.get(),
                ShinyConfig.USE_GLOW_EFFECT.get(),
                ShinyConfig.RANDOM_TEAM_COLOR.get(),
                ShinyConfig.FIXED_TEAM_COLOR.get(),
                null,
                Set.of(),
                Set.of(),
                Set.of()
        );
    }

    private static double getScaledDouble(JsonObject json, String directKey, String scaleKey, double baseValue) {
        if (json.has(directKey)) {
            return GsonHelper.getAsDouble(json, directKey);
        }

        return baseValue * GsonHelper.getAsDouble(json, scaleKey, 1.0D);
    }

    @Nullable
    private static String getOptionalString(JsonObject json, String key) {
        if (!json.has(key) || json.get(key).isJsonNull()) {
            return null;
        }

        return GsonHelper.getAsString(json, key);
    }

    private static ResourceLocation getResourceLocation(JsonObject json, String key) {
        return ResourceLocation.parse(GsonHelper.getAsString(json, key));
    }

    private static Set<ResourceLocation> parseResourceLocations(JsonObject json, String key) {
        Set<ResourceLocation> values = new HashSet<>();
        if (!json.has(key)) {
            return values;
        }

        JsonArray array = GsonHelper.getAsJsonArray(json, key);
        for (JsonElement element : array) {
            String value = GsonHelper.convertToString(element, key);
            if (!value.startsWith("#")) {
                values.add(ResourceLocation.parse(value));
            }
        }

        return values;
    }

    private static Set<ResourceLocation> parseBiomeIds(JsonObject json, String key) {
        return parseResourceLocations(json, key);
    }

    private static Set<TagKey<Biome>> parseBiomeTags(JsonObject json, String key) {
        Set<TagKey<Biome>> tags = new HashSet<>();
        if (!json.has(key)) {
            return tags;
        }

        JsonArray array = GsonHelper.getAsJsonArray(json, key);
        for (JsonElement element : array) {
            String value = GsonHelper.convertToString(element, key);
            if (value.startsWith("#")) {
                tags.add(TagKey.create(Registries.BIOME, ResourceLocation.parse(value.substring(1))));
            }
        }

        return tags;
    }
}
