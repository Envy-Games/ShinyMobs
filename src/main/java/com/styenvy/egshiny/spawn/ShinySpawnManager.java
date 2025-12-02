package com.styenvy.egshiny.spawn;

import com.styenvy.egshiny.EGShiny;
import com.styenvy.egshiny.config.ShinyConfig;
import com.styenvy.egshiny.data.PlayerShinyData;
import com.styenvy.egshiny.util.ShinyMobHelper;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.Random;
import java.util.UUID;

public class ShinySpawnManager {
    private static final Random RANDOM = new Random();
    private int tickCounter = 0;

    @SubscribeEvent
    public void onServerTick(ServerTickEvent.Post event) {
        if (!ShinyConfig.ENABLE_MOD.get()) {
            return;
        }

        tickCounter++;
        // Check every second (20 ticks)
        if (tickCounter % 20 != 0) {
            return;
        }

        ServerLevel level = event.getServer().getLevel(Level.OVERWORLD);
        if (level == null) {
            return;
        }

        // Process each online player
        for (ServerPlayer player : event.getServer().getPlayerList().getPlayers()) {
            processPlayerSpawn(player, level);
        }
    }

    private void processPlayerSpawn(ServerPlayer player, ServerLevel level) {
        UUID playerUUID = player.getUUID();

        // Check if player has shinies disabled
        if (PlayerShinyData.isShinyDisabled(playerUUID)) {
            return;
        }

        // Check if player already has a shiny mob
        if (ShinyConfig.ONE_SHINY_PER_PLAYER.get() && EGShiny.PLAYER_SHINY_MOBS.containsKey(playerUUID)) {
            if (EGShiny.PLAYER_SHINY_MOBS.get(playerUUID) != null &&
                    EGShiny.PLAYER_SHINY_MOBS.get(playerUUID).isAlive()) {
                return;
            } else {
                // Remove dead entity reference
                EGShiny.PLAYER_SHINY_MOBS.remove(playerUUID);
            }
        }

        // Initialize or update spawn timer
        int currentTimer = EGShiny.PLAYER_SPAWN_TIMERS.getOrDefault(playerUUID, -1);
        if (currentTimer == -1) {
            // Initialize with random time
            int minMinutes = ShinyConfig.MIN_SPAWN_TIME.get();
            int maxMinutes = ShinyConfig.MAX_SPAWN_TIME.get();
            int spawnTimeMinutes = minMinutes + RANDOM.nextInt(Math.max(1, maxMinutes - minMinutes + 1));
            int spawnTimeTicks = spawnTimeMinutes * 60 * 20; // Convert to ticks
            EGShiny.PLAYER_SPAWN_TIMERS.put(playerUUID, spawnTimeTicks);
            EGShiny.LOGGER.debug("Set spawn timer for player {} to {} minutes", player.getName().getString(), spawnTimeMinutes);
        } else {
            // Decrement timer by 1 second (20 ticks)
            currentTimer -= 20;
            EGShiny.PLAYER_SPAWN_TIMERS.put(playerUUID, currentTimer);

            // Check if it's time to spawn
            if (currentTimer <= 0) {
                spawnShinyMob(player, level);

                // Reset timer for next spawn
                int minMinutes = ShinyConfig.MIN_SPAWN_TIME.get();
                int maxMinutes = ShinyConfig.MAX_SPAWN_TIME.get();
                int spawnTimeMinutes = minMinutes + RANDOM.nextInt(Math.max(1, maxMinutes - minMinutes + 1));
                int spawnTimeTicks = spawnTimeMinutes * 60 * 20;
                EGShiny.PLAYER_SPAWN_TIMERS.put(playerUUID, spawnTimeTicks);
            }
        }
    }

    public static boolean spawnShinyMob(ServerPlayer player, ServerLevel level) {
        int spawnDistance = ShinyConfig.SPAWN_DISTANCE.get();

        // Try to find a valid spawn position
        BlockPos spawnPos = findSpawnPosition(player, level, spawnDistance);
        if (spawnPos == null) {
            EGShiny.LOGGER.warn("Could not find valid spawn position for shiny mob near player {}", player.getName().getString());
            return false;
        }

        // Create the shiny zombie
        Zombie zombie = EntityType.ZOMBIE.create(level);
        if (zombie == null) {
            return false;
        }

        // Position the zombie
        zombie.moveTo(
                spawnPos.getX() + 0.5,
                spawnPos.getY(),
                spawnPos.getZ() + 0.5,
                RANDOM.nextFloat() * 360.0F,
                0.0F
        );

        // Make it shiny!
        ShinyMobHelper.makeShiny(zombie, level);

        // Spawn the entity
        zombie.finalizeSpawn(
                level,
                level.getCurrentDifficultyAt(spawnPos),
                MobSpawnType.COMMAND,
                null
        );
        level.addFreshEntity(zombie);

        // Track the shiny mob for this player
        EGShiny.PLAYER_SHINY_MOBS.put(player.getUUID(), zombie);

        // Send notification to player
        if (ShinyConfig.SHOW_SPAWN_MESSAGE.get()) {
            Component message;
            if (ShinyConfig.SHOW_COORDINATES.get()) {
                message = Component.literal(String.format(
                                "\u00A76\u00A7l\u2B50 A Shiny Mob has spawned at X: %d, Y: %d, Z: %d! \u2B50",
                                spawnPos.getX(), spawnPos.getY(), spawnPos.getZ()
                        ))
                        .withStyle(ChatFormatting.GOLD);
            } else {
                message = Component.literal("\u00A76\u00A7l\u2B50 A Shiny Mob has spawned nearby! \u2B50")
                        .withStyle(ChatFormatting.GOLD);
            }
            player.sendSystemMessage(message);
        }

        EGShiny.LOGGER.info("Spawned shiny mob for player {} at {}", player.getName().getString(), spawnPos);
        return true;
    }

    private static BlockPos findSpawnPosition(ServerPlayer player, ServerLevel level, int distance) {
        Random rand = new Random();
        Vec3 playerPos = player.position();

        // Try up to 20 attempts to find a valid spawn position
        for (int attempts = 0; attempts < 20; attempts++) {
            // Generate random angle
            double angle = rand.nextDouble() * 2 * Math.PI;

            // Calculate position at the specified distance
            int x = (int) (playerPos.x + Math.cos(angle) * distance);
            int z = (int) (playerPos.z + Math.sin(angle) * distance);

            // Find a valid Y position
            BlockPos.MutableBlockPos mutablePos = new BlockPos.MutableBlockPos(x, (int) playerPos.y, z);

            // Check up and down for a valid spawn spot
            for (int yOffset = -10; yOffset <= 10; yOffset++) {
                mutablePos.setY((int) playerPos.y + yOffset);

                if (isValidSpawnLocation(level, mutablePos)) {
                    return mutablePos.immutable();
                }
            }

            // Try surface level
            int surfaceY = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
            mutablePos.setY(surfaceY);
            if (isValidSpawnLocation(level, mutablePos)) {
                return mutablePos.immutable();
            }
        }

        return null;
    }

    private static boolean isValidSpawnLocation(ServerLevel level, BlockPos pos) {
        // Check if the position and one above are air/passable
        if (!level.getBlockState(pos).isAir() || !level.getBlockState(pos.above()).isAir()) {
            return false;
        }

        // Check if there's a solid, sturdy block below (modern replacement for isSolid())
        BlockPos belowPos = pos.below();
        if (!level.getBlockState(belowPos).isFaceSturdy(level, belowPos, Direction.UP)) {
            return false;
        }

        // Check light level (zombies spawn in darkness)
        return level.getBrightness(LightLayer.BLOCK, pos) <= 7;
    }
}
