package com.styenvy.egshiny.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import com.styenvy.egshiny.EGShiny;
import com.styenvy.egshiny.config.ShinyConfig;
import com.styenvy.egshiny.data.ActiveShinyData;
import com.styenvy.egshiny.data.PlayerShinyData;
import com.styenvy.egshiny.spawn.ShinySpawnManager;
import com.styenvy.egshiny.util.ShinyMobHelper;
import com.styenvy.egshiny.util.ShinyProfileRegistry;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

public class ShinyCommands {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
                Commands.literal("shiny")
                        .then(Commands.literal("on")
                                .executes(ShinyCommands::enableShiny))
                        .then(Commands.literal("off")
                                .executes(ShinyCommands::disableShiny))
                        .then(Commands.literal("status")
                                .executes(ShinyCommands::checkStatus))
                        .then(Commands.literal("hard")
                                .then(Commands.literal("on")
                                        .executes(ShinyCommands::enableHardShiny))
                                .then(Commands.literal("off")
                                        .executes(ShinyCommands::disableHardShiny)))
                        .then(Commands.literal("profilelist")
                                .requires(ShinyCommands::hasAdminPermission)
                                .executes(ShinyCommands::listShinyProfiles))
                        .then(Commands.literal("spawntest")
                                .requires(ShinyCommands::hasAdminPermission)
                                .executes(ctx -> spawnTestShiny(ctx, null))
                                .then(Commands.argument("entity", ResourceLocationArgument.id())
                                        .executes(ctx -> spawnTestShiny(
                                                ctx,
                                                ResourceLocationArgument.getId(ctx, "entity")
                                        ))))
                        .then(Commands.literal("clear")
                                .requires(ShinyCommands::hasAdminPermission)
                                .executes(ShinyCommands::clearShinyMobs))
                        .then(Commands.literal("killall")
                                .requires(ShinyCommands::hasAdminPermission)
                                .executes(ShinyCommands::killAllShinyMobs))
        );
    }

    private static boolean hasAdminPermission(CommandSourceStack source) {
        return source.hasPermission(Commands.LEVEL_GAMEMASTERS);
    }

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
        PlayerShinyData.save(source.getServer());
        source.sendSuccess(() -> Component.literal("Shiny spawns enabled for you!")
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
        PlayerShinyData.save(source.getServer());
        source.sendSuccess(() -> Component.literal("Shiny spawns disabled for you!")
                .withStyle(ChatFormatting.RED), false);

        EGShiny.PLAYER_SPAWN_TIMERS.remove(playerUUID);

        if (ShinyConfig.DISABLE_REMOVES_ACTIVE_SHINY.get()) {
            ActiveShinyData activeData = ActiveShinyData.get(source.getServer());
            Entity shinyMob = EGShiny.PLAYER_SHINY_MOBS.remove(playerUUID);
            boolean removedLoadedMob = shinyMob != null && shinyMob.isAlive();
            if (removedLoadedMob) {
                cleanupShinyMob(shinyMob);
                shinyMob.discard();
                source.sendSuccess(() -> Component.literal("Your existing shiny mob has been removed.")
                        .withStyle(ChatFormatting.GRAY), false);
            } else {
                activeData.findLoadedEntity(source.getServer(), playerUUID)
                        .filter(LivingEntity::isAlive)
                        .ifPresent(living -> {
                            cleanupShinyMob(living);
                            living.discard();
                            source.sendSuccess(() -> Component.literal("Your existing shiny mob has been removed.")
                                    .withStyle(ChatFormatting.GRAY), false);
                        });
            }
            activeData.clear(playerUUID);
        }

        return 1;
    }

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
        PlayerShinyData.save(source.getServer());
        source.sendSuccess(() -> Component.literal("Hard mode shiny spawns enabled for you!")
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
        PlayerShinyData.save(source.getServer());
        source.sendSuccess(() -> Component.literal("Hard mode shiny spawns disabled for you!")
                .withStyle(ChatFormatting.GRAY), false);

        return 1;
    }

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

    private static int listShinyProfiles(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        List<EntityType<?>> entityTypes = new ArrayList<>(ShinyProfileRegistry.getRegisteredEntityTypes());

        if (entityTypes.isEmpty()) {
            source.sendSuccess(() -> Component.literal("No shiny mob profiles are currently loaded.")
                    .withStyle(ChatFormatting.YELLOW), false);
            return 0;
        }

        entityTypes.sort(Comparator.comparing(type -> BuiltInRegistries.ENTITY_TYPE.getKey(type).toString()));
        source.sendSuccess(() -> Component.literal("Loaded shiny mob profiles (" + entityTypes.size() + "):")
                .withStyle(ChatFormatting.AQUA), false);

        for (EntityType<?> entityType : entityTypes) {
            ResourceLocation entityId = BuiltInRegistries.ENTITY_TYPE.getKey(entityType);
            var profile = ShinyProfileRegistry.getProfileFor(entityType);
            boolean hard = profile != null && profile.hardShiny();
            ChatFormatting color = hard ? ChatFormatting.GOLD : ChatFormatting.GRAY;
            String suffix = hard ? " (hard)" : "";
            source.sendSuccess(() -> Component.literal("- " + entityId + suffix).withStyle(color), false);
        }

        return entityTypes.size();
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

        boolean success = ShinySpawnManager.spawnShinyMob(player, level, entityType);

        if (success) {
            if (entityType != null) {
                source.sendSuccess(
                        () -> Component.literal("Test shiny mob spawned: " + entityId)
                                .withStyle(ChatFormatting.GREEN),
                        false
                );
            } else {
                source.sendSuccess(
                        () -> Component.literal("Test shiny mob spawned successfully!")
                                .withStyle(ChatFormatting.GREEN),
                        false
                );
            }
            return 1;
        }

        source.sendFailure(
                Component.literal("Failed to spawn test shiny mob. Check logs for details.")
                        .withStyle(ChatFormatting.RED)
        );
        return 0;
    }

    private static int clearShinyMobs(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();

        int count = 0;
        List<Entity> trackedMobs = new ArrayList<>(EGShiny.PLAYER_SHINY_MOBS.values());
        for (Entity entity : trackedMobs) {
            if (entity != null && entity.isAlive()) {
                cleanupShinyMob(entity);
                entity.discard();
                count++;
            }
        }

        EGShiny.PLAYER_SHINY_MOBS.clear();
        ActiveShinyData.get(source.getServer()).clearAll();

        final int finalCount = count;
        source.sendSuccess(() -> Component.literal("Cleared " + finalCount + " tracked shiny mobs.")
                .withStyle(ChatFormatting.GREEN), true);

        return 1;
    }

    private static int killAllShinyMobs(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();

        int count = 0;

        for (ServerLevel level : source.getServer().getAllLevels()) {
            for (Entity entity : level.getAllEntities()) {
                if (entity instanceof LivingEntity living && ShinyMobHelper.isShiny(living)) {
                    ShinyMobHelper.cleanupShinyVisuals(living, level);
                    entity.discard();
                    count++;
                }
            }
        }

        EGShiny.PLAYER_SHINY_MOBS.clear();
        EGShiny.PLAYER_SPAWN_TIMERS.clear();
        ActiveShinyData.get(source.getServer()).clearAll();

        final int finalCount = count;
        source.sendSuccess(() -> Component.literal("Killed " + finalCount + " shiny mobs in all dimensions.")
                .withStyle(ChatFormatting.RED), true);

        return 1;
    }

    private static void cleanupShinyMob(Entity entity) {
        if (entity instanceof LivingEntity living && entity.level() instanceof ServerLevel level) {
            ShinyMobHelper.cleanupShinyVisuals(living, level);
        }
    }
}
