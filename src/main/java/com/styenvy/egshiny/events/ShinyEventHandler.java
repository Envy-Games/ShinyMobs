package com.styenvy.egshiny.events;

import com.styenvy.egshiny.EGShiny;
import com.styenvy.egshiny.config.ShinyConfig;
import com.styenvy.egshiny.util.ShinyMobHelper;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.FinalizeSpawnEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

import java.util.Map;
import java.util.UUID;

public class ShinyEventHandler {

    @SubscribeEvent
    public void onEntityDeath(LivingDeathEvent event) {
        LivingEntity entity = event.getEntity();

        // Check if this was a shiny mob
        if (ShinyMobHelper.isShiny(entity)) {
            // Find which player owns this shiny mob
            UUID ownerUUID = null;
            for (Map.Entry<UUID, Entity> entry : EGShiny.PLAYER_SHINY_MOBS.entrySet()) {
                if (entry.getValue() == entity) {
                    ownerUUID = entry.getKey();
                    break;
                }
            }

            if (ownerUUID != null) {
                EGShiny.PLAYER_SHINY_MOBS.remove(ownerUUID);
                EGShiny.LOGGER.debug("Shiny mob died, removed from tracking for player {}", ownerUUID);

                // Notify the player if they killed it
                if (event.getSource().getEntity() instanceof ServerPlayer killer) {
                    killer.sendSystemMessage(
                            Component.literal("★ You defeated a Shiny Mob! ★")
                                    .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD)
                    );

                    giveShinyRewards(killer);
                }
            }
        }
    }

    @SubscribeEvent
    public void onLivingDamage(LivingDamageEvent.Pre event) {
        // Apply extra effects when a shiny mob attacks
        if (event.getSource().getEntity() instanceof LivingEntity attacker && ShinyMobHelper.isShiny(attacker)) {
            if (event.getEntity() instanceof Player player && ShinyConfig.DAMAGE_MULTIPLIER.get() > 1.0) {
                // Light the player on fire for 3 seconds
                player.setRemainingFireTicks(60);
            }
        }
    }

    @SubscribeEvent
    public void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            UUID playerUUID = player.getUUID();

            // Don't remove the mob, just log that the timer is effectively paused
            if (EGShiny.PLAYER_SPAWN_TIMERS.containsKey(playerUUID)) {
                EGShiny.LOGGER.debug(
                        "Player {} logged out, pausing shiny spawn timer",
                        player.getName().getString()
                );
            }
        }
    }

    @SubscribeEvent
    public void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            UUID playerUUID = player.getUUID();

            // Check if player has an existing shiny mob
            if (EGShiny.PLAYER_SHINY_MOBS.containsKey(playerUUID)) {
                Entity shinyMob = EGShiny.PLAYER_SHINY_MOBS.get(playerUUID);
                if (shinyMob == null || !shinyMob.isAlive()) {
                    // Clean up dead reference
                    EGShiny.PLAYER_SHINY_MOBS.remove(playerUUID);
                } else {
                    // Notify player they still have an active shiny
                    player.sendSystemMessage(
                            Component.literal("You still have an active Shiny Mob in the world!")
                                    .withStyle(ChatFormatting.GOLD)
                    );
                }
            }
        }
    }

    /**
     * Fired just before {@link net.minecraft.world.entity.Mob#finalizeSpawn} runs.
     * We make sure that if this mob is shiny, its spawn is not cancelled.
     */
    @SubscribeEvent
    public void onMobFinalizeSpawn(FinalizeSpawnEvent event) {
        LivingEntity mob = event.getEntity();

        // If this is one of our shiny mobs, ensure its spawn is not blocked
        if (ShinyMobHelper.isShiny(mob)) {
            // In case some other handler tried to cancel the spawn, force it back on
            if (event.isSpawnCancelled()) {
                EGShiny.LOGGER.debug("Overriding spawn cancel for shiny mob {}", mob);
            }
            event.setSpawnCancelled(false);
        }
    }

    private void giveShinyRewards(ServerPlayer player) {
        // Give bonus experience
        player.giveExperiencePoints(100);

        // Notify the player
        player.sendSystemMessage(
                Component.literal("+100 XP bonus for defeating a Shiny Mob!")
                        .withStyle(ChatFormatting.YELLOW)
        );
    }
}
