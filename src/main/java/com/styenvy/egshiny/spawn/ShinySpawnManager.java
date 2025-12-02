package com.styenvy.egshiny.spawn;

import com.styenvy.egshiny.EGShiny;
import com.styenvy.egshiny.config.ShinyConfig;
import com.styenvy.egshiny.data.PlayerShinyData;
import com.styenvy.egshiny.util.ShinyMobHelper;
import com.styenvy.egshiny.util.ShinyProfileRegistry;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.EventHooks;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.jetbrains.annotations.Nullable;

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
                // Natural spawns use a random eligible shiny profile type
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

    /**
     * Default shiny spawn – used by the timer. Uses the profile registry for random selection.
     */
    public static void spawnShinyMob(ServerPlayer player, ServerLevel level) {
        spawnShinyMob(player, level, null);
    }

    /**
     * Test / flexible shiny spawn – allows forcing a specific EntityType.
     * If forcedType is null, a random eligible profile-based type is used.
     */
    public static boolean spawnShinyMob(ServerPlayer player, ServerLevel level, @Nullable EntityType<?> forcedType) {
        int spawnDistance = ShinyConfig.SPAWN_DISTANCE.get();

        // Try to find a valid spawn position
        BlockPos spawnPos = findSpawnPosition(player, level, spawnDistance);
        if (spawnPos == null) {
            EGShiny.LOGGER.warn("Could not find valid spawn position for shiny mob near player {}", player.getName().getString());
            return false;
        }

        // Determine whether hard-mode shinies are enabled for this player
        boolean hardMode = PlayerShinyData.isHardShinyEnabled(player.getUUID());

        // Choose entity type: forced type (for tests) or a random eligible shiny profile
        EntityType<?> selectedType;
        if (forcedType != null) {
            selectedType = forcedType;
        } else {
            selectedType = ShinyProfileRegistry.getRandomShinyEntityType(hardMode, RANDOM);
            if (selectedType == null) {
                // Fallback to a basic zombie if the registry is empty for some reason
                selectedType = EntityType.ZOMBIE;
            }
        }

        // Create the entity instance
        net.minecraft.world.entity.Entity rawEntity = selectedType.create(level);
        if (!(rawEntity instanceof LivingEntity living)) {
            EGShiny.LOGGER.warn("Selected shiny entity type {} is not a LivingEntity, aborting.", selectedType);
            return false;
        }

        // Position the entity
        living.moveTo(
                spawnPos.getX() + 0.5,
                spawnPos.getY(),
                spawnPos.getZ() + 0.5,
                RANDOM.nextFloat() * 360.0F,
                0.0F
        );

        // Make it shiny (this respects hard-mode + profile.hardShiny())
        ShinyMobHelper.makeShiny(living, level, hardMode);

        // Finalize spawn if it's a Mob (not all LivingEntities are Mobs)
        if (living instanceof Mob mob) {
            EventHooks.finalizeMobSpawn(
                    mob,
                    level,
                    level.getCurrentDifficultyAt(spawnPos),
                    MobSpawnType.COMMAND,
                    null
            );
        }

        // Spawn the entity
        level.addFreshEntity(living);

        // Track the shiny mob for this player
        EGShiny.PLAYER_SHINY_MOBS.put(player.getUUID(), living);

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

        EGShiny.LOGGER.info("Spawned shiny mob ({}) for player {} at {}",
                level.registryAccess().registryOrThrow(net.minecraft.core.registries.Registries.ENTITY_TYPE)
                        .getKey(selectedType),
                player.getName().getString(),
                spawnPos
        );
        return true;
    }

    private static BlockPos findSpawnPosition(ServerPlayer player, ServerLevel level, int distance) {
        Vec3 playerPos = player.position();

        // Try up to 20 attempts to find a valid spawn position
        for (int attempts = 0; attempts < 20; attempts++) {
            // Generate random angle
            double angle = RANDOM.nextDouble() * 2 * Math.PI;

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
