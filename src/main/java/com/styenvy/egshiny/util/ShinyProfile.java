package com.styenvy.egshiny.util;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.biome.Biome;

import java.util.Set;

public record ShinyProfile(
        double minHealthMultiplier,
        double maxHealthMultiplier,
        double fixedHealthMultiplier,
        boolean useRandomHealth,
        double damageMultiplier,
        boolean hardShiny,
        boolean equipNetherite,
        boolean maxEnchantments,
        double dropChancePerItem,
        boolean useGlow,
        boolean randomTeamColor,
        String fixedTeamColorKey,
        String shinyLootTableId,
        Set<ResourceLocation> allowedDimensions,
        Set<ResourceLocation> allowedBiomes,
        Set<TagKey<Biome>> allowedBiomeTags
) {
    public ShinyProfile {
        allowedDimensions = Set.copyOf(allowedDimensions);
        allowedBiomes = Set.copyOf(allowedBiomes);
        allowedBiomeTags = Set.copyOf(allowedBiomeTags);
    }

    public boolean canSpawnAt(ServerLevel level, BlockPos pos) {
        if (!allowedDimensions.isEmpty() && !allowedDimensions.contains(level.dimension().location())) {
            return false;
        }

        if (allowedBiomes.isEmpty() && allowedBiomeTags.isEmpty()) {
            return true;
        }

        var biome = level.getBiome(pos);
        for (ResourceLocation biomeId : allowedBiomes) {
            if (biome.is(biomeId)) {
                return true;
            }
        }

        for (TagKey<Biome> biomeTag : allowedBiomeTags) {
            if (biome.is(biomeTag)) {
                return true;
            }
        }

        return false;
    }
}
