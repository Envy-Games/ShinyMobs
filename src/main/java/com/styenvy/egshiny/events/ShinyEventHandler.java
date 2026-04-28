package com.styenvy.egshiny.events;

import com.styenvy.egshiny.EGShiny;
import com.styenvy.egshiny.config.ShinyConfig;
import com.styenvy.egshiny.data.ActiveShinyData;
import com.styenvy.egshiny.data.PlayerShinyData;
import com.styenvy.egshiny.util.ShinyMobHelper;
import com.styenvy.egshiny.util.ShinyProfile;
import com.styenvy.egshiny.util.ShinyProfileRegistry;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

import java.util.Map;
import java.util.UUID;

public class ShinyEventHandler {

    @SubscribeEvent
    public void onEntityDeath(LivingDeathEvent event) {
        LivingEntity entity = event.getEntity();

        if (!ShinyMobHelper.isShiny(entity)) {
            return;
        }

        if (!(entity.level() instanceof ServerLevel serverLevel)) {
            return;
        }

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
            ActiveShinyData.get(serverLevel.getServer()).clear(ownerUUID);
            EGShiny.LOGGER.debug("Shiny mob died, removed from tracking for player {}", ownerUUID);
        } else {
            ShinyMobHelper.getOwner(entity)
                    .ifPresent(owner -> ActiveShinyData.get(serverLevel.getServer()).clear(owner));
        }

        ShinyMobHelper.cleanupShinyVisuals(entity, serverLevel);
        dropShinyLoot(entity, serverLevel, event);

        // Notify the player if they killed it
        if (event.getSource().getEntity() instanceof ServerPlayer killer) {
            killer.sendSystemMessage(
                    Component.literal("You defeated a Shiny Mob!")
                            .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD)
            );

            giveShinyRewards(killer);
        }
    }

    @SubscribeEvent
    public void onLivingDamage(LivingDamageEvent.Pre event) {
        if (event.getEntity().level().isClientSide()) {
            return;
        }

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
            } else {
                ActiveShinyData activeData = ActiveShinyData.get(player.getServer());
                if (activeData.getEntityUUID(playerUUID).isPresent()) {
                    var loadedShiny = activeData.findLoadedEntity(player.getServer(), playerUUID);
                    if (loadedShiny.isPresent()) {
                        LivingEntity living = loadedShiny.get();
                        if (!living.isAlive() || !ShinyMobHelper.isShiny(living)) {
                            activeData.clear(playerUUID);
                            return;
                        }

                        EGShiny.PLAYER_SHINY_MOBS.put(playerUUID, living);
                    }

                    player.sendSystemMessage(
                            Component.literal("You still have an active Shiny Mob in the world!")
                                    .withStyle(ChatFormatting.GOLD)
                    );
                }
            }
        }
    }

    @SubscribeEvent
    public void onEntityJoinLevel(EntityJoinLevelEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level) || !(event.getEntity() instanceof LivingEntity living)) {
            return;
        }

        if (!ShinyMobHelper.isShiny(living)) {
            return;
        }

        ShinyMobHelper.getOwner(living).ifPresent(ownerUUID -> {
            ActiveShinyData activeData = ActiveShinyData.get(level.getServer());
            if (PlayerShinyData.isShinyDisabled(ownerUUID) && ShinyConfig.DISABLE_REMOVES_ACTIVE_SHINY.get()) {
                ShinyMobHelper.cleanupShinyVisuals(living, level);
                activeData.clear(ownerUUID);
                living.discard();
                return;
            }

            if (activeData.getEntityUUID(ownerUUID)
                    .filter(entityUUID -> entityUUID.equals(living.getUUID()))
                    .isEmpty()) {
                return;
            }

            EGShiny.PLAYER_SHINY_MOBS.put(ownerUUID, living);
        });
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

    private void dropShinyLoot(LivingEntity entity, ServerLevel level, LivingDeathEvent event) {
        if (!level.getGameRules().getBoolean(GameRules.RULE_DOMOBLOOT)) {
            return;
        }

        ShinyProfile profile = ShinyProfileRegistry.getProfileFor(entity.getType());
        if (profile == null || profile.shinyLootTableId() == null || profile.shinyLootTableId().isBlank()) {
            return;
        }

        ResourceKey<LootTable> lootTableKey = ResourceKey.create(
                Registries.LOOT_TABLE,
                ResourceLocation.parse(profile.shinyLootTableId())
        );
        LootTable lootTable = level.getServer().reloadableRegistries().getLootTable(lootTableKey);
        LootParams.Builder lootParams = new LootParams.Builder(level)
                .withParameter(LootContextParams.THIS_ENTITY, entity)
                .withParameter(LootContextParams.ORIGIN, entity.position())
                .withParameter(LootContextParams.DAMAGE_SOURCE, event.getSource())
                .withOptionalParameter(LootContextParams.ATTACKING_ENTITY, event.getSource().getEntity())
                .withOptionalParameter(LootContextParams.DIRECT_ATTACKING_ENTITY, event.getSource().getDirectEntity());

        if (event.getSource().getEntity() instanceof ServerPlayer player) {
            lootParams.withParameter(LootContextParams.LAST_DAMAGE_PLAYER, player)
                    .withLuck(player.getLuck());
        }

        lootTable.getRandomItems(lootParams.create(LootContextParamSets.ENTITY), entity::spawnAtLocation);
    }
}
