package com.styenvy.egshiny.util;

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
        String shinyLootTableId
) {
}
