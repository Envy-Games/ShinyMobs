package com.styenvy.egshiny.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import com.styenvy.egshiny.EGShiny;
import com.styenvy.egshiny.data.PlayerShinyData;
import com.styenvy.egshiny.spawn.ShinySpawnManager;
import com.styenvy.egshiny.util.ShinyMobHelper;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;

import java.util.Map;
import java.util.UUID;

public class ShinyCommands {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        // /ShinyOn command
        dispatcher.register(
                Commands.literal("ShinyOn")
                        .executes(ShinyCommands::enableShiny)
        );

        // /ShinyOff command
        dispatcher.register(
                Commands.literal("ShinyOff")
                        .executes(ShinyCommands::disableShiny)
        );

        // /ShinyHardOn command
        dispatcher.register(
                Commands.literal("ShinyHardOn")
                        .executes(ShinyCommands::enableHardShiny)
        );

        // /ShinyHardOff command
        dispatcher.register(
                Commands.literal("ShinyHardOff")
                        .executes(ShinyCommands::disableHardShiny)
        );

        // /ShinySpawnTest [entity] command (requires op level 2)
        dispatcher.register(
                Commands.literal("ShinySpawnTest")
                        .requires(source -> source.hasPermission(2))
                        // No-arg version: use default behavior
                        .executes(ctx -> spawnTestShiny(ctx, null))
                        // With entity id argument, e.g. minecraft:zombie
                        .then(Commands.argument("entity", ResourceLocationArgument.id())
                                .executes(ctx -> spawnTestShiny(
                                        ctx,
                                        ResourceLocationArgument.getId(ctx, "entity")
                                )))
        );

        // /ShinyStatus command
        dispatcher.register(
                Commands.literal("ShinyStatus")
                        .executes(ShinyCommands::checkStatus)
        );

        // /ShinyClear command (admin command to clear tracked shiny mobs)
        dispatcher.register(
                Commands.literal("ShinyClear")
                        .requires(source -> source.hasPermission(2))
                        .executes(ShinyCommands::clearShinyMobs)
        );

        // /ShinyKillAll command (admin command to kill all shiny mobs in all dimensions)
        dispatcher.register(
                Commands.literal("ShinyKillAll")
                        .requires(source -> source.hasPermission(2))
                        .executes(ShinyCommands::killAllShinyMobs)
        );
    }

    // === Basic shiny enable/disable ===

    private static int enableShiny(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();

        if (!(source.getEntity() instanceof ServerPlayer player)) {
            source.sendFailure(Component.literal("This command can only be used by players!"));
            return 0;
        }

        UUID playerUUID = player.getUUID();

        if (!PlayerShinyData.isShinyDisabled(playerUUID)) {
            source.sendSuccess(() -> Component.literal("Shiny spawns are already enabled for you!")
                    .withStyle(ChatFormatting.GREEN), false);
            return 0;
        }

        PlayerShinyData.setShinyEnabled(playerUUID, true);
        source.sendSuccess(() -> Component.literal("✔ Shiny spawns enabled for you!")
                .withStyle(ChatFormatting.GREEN), false);

        return 1;
    }

    private static int disableShiny(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();

        if (!(source.getEntity() instanceof ServerPlayer player)) {
            source.sendFailure(Component.literal("This command can only be used by players!"));
            return 0;
        }

        UUID playerUUID = player.getUUID();

        if (PlayerShinyData.isShinyDisabled(playerUUID)) {
            source.sendSuccess(() -> Component.literal("Shiny spawns are already disabled for you!")
                    .withStyle(ChatFormatting.RED), false);
            return 0;
        }

        PlayerShinyData.setShinyEnabled(playerUUID, false);
        source.sendSuccess(() -> Component.literal("✖ Shiny spawns disabled for you!")
                .withStyle(ChatFormatting.RED), false);

        // Clear spawn timer
        EGShiny.PLAYER_SPAWN_TIMERS.remove(playerUUID);

        // Kill existing shiny mob if present
        if (EGShiny.PLAYER_SHINY_MOBS.containsKey(playerUUID)) {
            var shinyMob = EGShiny.PLAYER_SHINY_MOBS.get(playerUUID);
            if (shinyMob != null && shinyMob.isAlive()) {
                shinyMob.discard();
                source.sendSuccess(() -> Component.literal("Your existing shiny mob has been removed.")
                        .withStyle(ChatFormatting.GRAY), false);
            }
            EGShiny.PLAYER_SHINY_MOBS.remove(playerUUID);
        }

        return 1;
    }

    // === Hard mode toggles ===

    private static int enableHardShiny(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();

        if (!(source.getEntity() instanceof ServerPlayer player)) {
            source.sendFailure(Component.literal("This command can only be used by players!"));
            return 0;
        }

        UUID playerUUID = player.getUUID();

        if (PlayerShinyData.isHardShinyEnabled(playerUUID)) {
            source.sendSuccess(() -> Component.literal("Hard mode shiny spawns are already enabled for you!")
                    .withStyle(ChatFormatting.GOLD), false);
            return 0;
        }

        PlayerShinyData.setHardShinyEnabled(playerUUID, true);
        source.sendSuccess(() -> Component.literal("✔ Hard mode shiny spawns enabled for you!")
                .withStyle(ChatFormatting.GOLD), false);

        return 1;
    }

    private static int disableHardShiny(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();

        if (!(source.getEntity() instanceof ServerPlayer player)) {
            source.sendFailure(Component.literal("This command can only be used by players!"));
            return 0;
        }

        UUID playerUUID = player.getUUID();

        if (!PlayerShinyData.isHardShinyEnabled(playerUUID)) {
            source.sendSuccess(() -> Component.literal("Hard mode shiny spawns are already disabled for you!")
                    .withStyle(ChatFormatting.GRAY), false);
            return 0;
        }

        PlayerShinyData.setHardShinyEnabled(playerUUID, false);
        source.sendSuccess(() -> Component.literal("✖ Hard mode shiny spawns disabled for you!")
                .withStyle(ChatFormatting.GRAY), false);

        return 1;
    }

    // === Status & test ===

    private static int checkStatus(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();

        if (!(source.getEntity() instanceof ServerPlayer player)) {
            source.sendFailure(Component.literal("This command can only be used by players!"));
            return 0;
        }

        UUID playerUUID = player.getUUID();

        boolean disabled = PlayerShinyData.isShinyDisabled(playerUUID);
        boolean hard = PlayerShinyData.isHardShinyEnabled(playerUUID);

        ChatFormatting mainColor = disabled ? ChatFormatting.RED : ChatFormatting.GREEN;
        String statusText = disabled ? "DISABLED" : "ENABLED";

        Component line1 = Component.literal("Shiny spawns: " + statusText).withStyle(mainColor);
        Component line2 = Component.literal("Hard mode: " + (hard ? "ON" : "OFF"))
                .withStyle(hard ? ChatFormatting.GOLD : ChatFormatting.GRAY);

        source.sendSuccess(() -> Component.literal("--- Shiny Status ---").withStyle(ChatFormatting.AQUA), false);
        source.sendSuccess(() -> line1, false);
        source.sendSuccess(() -> line2, false);

        return 1;
    }

    private static int spawnTestShiny(CommandContext<CommandSourceStack> context, ResourceLocation entityId) {
        CommandSourceStack source = context.getSource();

        if (!(source.getEntity() instanceof ServerPlayer player)) {
            source.sendFailure(Component.literal("This command can only be used by players!"));
            return 0;
        }

        ServerLevel level = source.getLevel();
        EntityType<?> entityType = null;

        if (entityId != null) {
            var registry = level.registryAccess().registryOrThrow(Registries.ENTITY_TYPE);
            entityType = registry
                    .getOptional(ResourceKey.create(Registries.ENTITY_TYPE, entityId))
                    .orElse(null);

            if (entityType == null) {
                source.sendFailure(
                        Component.literal("Unknown entity type: " + entityId)
                                .withStyle(ChatFormatting.RED)
                );
                return 0;
            }
        }

        // Force spawn a shiny mob for testing, optionally with a specific entity type
        boolean success = ShinySpawnManager.spawnShinyMob(player, level, entityType);

        if (success) {
            if (entityType != null) {
                source.sendSuccess(
                        () -> Component.literal("✔ Test shiny mob spawned: " + entityId)
                                .withStyle(ChatFormatting.GREEN),
                        false
                );
            } else {
                source.sendSuccess(
                        () -> Component.literal("✔ Test shiny mob spawned successfully!")
                                .withStyle(ChatFormatting.GREEN),
                        false
                );
            }
            return 1;
        } else {
            source.sendFailure(
                    Component.literal("Failed to spawn test shiny mob. Check logs for details.")
                            .withStyle(ChatFormatting.RED)
            );
            return 0;
        }
    }

    // === Admin commands ===

    private static int clearShinyMobs(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();

        int count = 0;
        for (Map.Entry<UUID, net.minecraft.world.entity.Entity> entry : EGShiny.PLAYER_SHINY_MOBS.entrySet()) {
            if (entry.getValue() != null && entry.getValue().isAlive()) {
                entry.getValue().discard();
                count++;
            }
        }

        EGShiny.PLAYER_SHINY_MOBS.clear();

        final int finalCount = count;
        source.sendSuccess(() -> Component.literal("✔ Cleared " + finalCount + " tracked shiny mobs!")
                .withStyle(ChatFormatting.GREEN), true);

        return 1;
    }

    private static int killAllShinyMobs(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();

        int count = 0;

        // Scan all dimensions for any shiny-tagged mobs
        for (ServerLevel level : source.getServer().getAllLevels()) {
            for (net.minecraft.world.entity.Entity entity : level.getAllEntities()) {
                if (entity instanceof LivingEntity living && ShinyMobHelper.isShiny(living)) {
                    entity.discard();
                    count++;
                }
            }
        }

        // Clear tracking maps as reinforcement
        EGShiny.PLAYER_SHINY_MOBS.clear();
        EGShiny.PLAYER_SPAWN_TIMERS.clear();

        final int finalCount = count;
        source.sendSuccess(() -> Component.literal("✔ Killed " + finalCount + " shiny mobs in all dimensions!")
                .withStyle(ChatFormatting.RED), true);

        return 1;
    }
}
