package com.styenvy.egshiny.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import com.styenvy.egshiny.EGShiny;
import com.styenvy.egshiny.config.ShinyConfig;
import com.styenvy.egshiny.data.PlayerShinyData;
import com.styenvy.egshiny.spawn.ShinySpawnManager;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.Random;
import java.util.UUID;

public class ShinyCommands {
    private static final Random RANDOM = new Random();
    
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
        
        // /ShinySpawnTest command (requires op level 2)
        dispatcher.register(
            Commands.literal("ShinySpawnTest")
                .requires(source -> source.hasPermission(2))
                .executes(ShinyCommands::spawnTestShiny)
        );
        
        // /ShinyStatus command (bonus command to check status)
        dispatcher.register(
            Commands.literal("ShinyStatus")
                .executes(ShinyCommands::checkStatus)
        );
        
        // /ShinyClear command (admin command to clear all shiny mobs)
        dispatcher.register(
            Commands.literal("ShinyClear")
                .requires(source -> source.hasPermission(2))
                .executes(ShinyCommands::clearShinyMobs)
        );
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
        source.sendSuccess(() -> Component.literal("\u2714 Shiny mob spawns have been enabled!")
                .withStyle(ChatFormatting.GREEN), false);
        
        // Reset spawn timer for this player using config values
        int minMinutes = ShinyConfig.MIN_SPAWN_TIME.get();
        int maxMinutes = ShinyConfig.MAX_SPAWN_TIME.get();
        int spawnTimeMinutes = minMinutes + RANDOM.nextInt(Math.max(1, maxMinutes - minMinutes + 1));
        EGShiny.PLAYER_SPAWN_TIMERS.put(playerUUID, spawnTimeMinutes * 60 * 20);
        
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
        source.sendSuccess(() -> Component.literal("\u2718 Shiny mob spawns have been disabled!")
                .withStyle(ChatFormatting.RED), false);
        
        // Remove spawn timer
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
    
    private static int spawnTestShiny(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        
        if (!(source.getEntity() instanceof ServerPlayer player)) {
            source.sendFailure(Component.literal("This command can only be used by players!"));
            return 0;
        }
        
        ServerLevel level = source.getLevel();
        
        // Force spawn a shiny mob for testing
        boolean success = ShinySpawnManager.spawnShinyMob(player, level);
        
        if (success) {
            source.sendSuccess(() -> Component.literal("\u2714 Test shiny mob spawned successfully!")
                    .withStyle(ChatFormatting.GREEN), false);
            return 1;
        } else {
            source.sendFailure(Component.literal("Failed to spawn test shiny mob. Check logs for details.")
                    .withStyle(ChatFormatting.RED));
            return 0;
        }
    }
    
    private static int checkStatus(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        
        if (!(source.getEntity() instanceof ServerPlayer player)) {
            source.sendFailure(Component.literal("This command can only be used by players!"));
            return 0;
        }
        
        UUID playerUUID = player.getUUID();
        boolean enabled = !PlayerShinyData.isShinyDisabled(playerUUID);
        
        Component status = enabled 
                ? Component.literal("ENABLED").withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD)
                : Component.literal("DISABLED").withStyle(ChatFormatting.RED, ChatFormatting.BOLD);
        
        source.sendSuccess(() -> Component.literal("Shiny spawns status: ")
                .withStyle(ChatFormatting.GRAY).append(status), false);
        
        if (enabled && EGShiny.PLAYER_SPAWN_TIMERS.containsKey(playerUUID)) {
            int ticksRemaining = EGShiny.PLAYER_SPAWN_TIMERS.get(playerUUID);
            int minutesRemaining = ticksRemaining / (20 * 60);
            int secondsRemaining = (ticksRemaining / 20) % 60;
            source.sendSuccess(() -> Component.literal(String.format("Next spawn in approximately: %d:%02d", 
                    minutesRemaining, secondsRemaining))
                    .withStyle(ChatFormatting.GRAY)
                    .append(Component.literal(String.format(" %d:%02d", minutesRemaining, secondsRemaining))
                            .withStyle(ChatFormatting.YELLOW)), false);
        }
        
        if (EGShiny.PLAYER_SHINY_MOBS.containsKey(playerUUID)) {
            var shinyMob = EGShiny.PLAYER_SHINY_MOBS.get(playerUUID);
            if (shinyMob != null && shinyMob.isAlive()) {
                source.sendSuccess(() -> Component.literal("You have an active shiny mob!")
                        .withStyle(ChatFormatting.GRAY), false);
            }
        }
        
        return 1;
    }
    
    private static int clearShinyMobs(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        
        int count = 0;
        for (var entry : EGShiny.PLAYER_SHINY_MOBS.entrySet()) {
            if (entry.getValue() != null && entry.getValue().isAlive()) {
                entry.getValue().discard();
                count++;
            }
        }
        
        EGShiny.PLAYER_SHINY_MOBS.clear();
        
        final int finalCount = count;
        source.sendSuccess(() -> Component.literal("\u2714 Cleared " + finalCount + " shiny mobs!")
                .withStyle(ChatFormatting.GREEN), true);
        
        return 1;
    }
}
