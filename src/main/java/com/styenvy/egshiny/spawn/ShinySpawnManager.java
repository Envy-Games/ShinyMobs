package com.styenvy.egshiny.spawn;

import com.styenvy.egshiny.EGShiny;
import com.styenvy.egshiny.config.ShinyConfig;
import com.styenvy.egshiny.data.ActiveShinyData;
import com.styenvy.egshiny.data.PlayerShinyData;
import com.styenvy.egshiny.util.ShinyMobHelper;
import com.styenvy.egshiny.util.ShinyProfile;
import com.styenvy.egshiny.util.ShinyProfileRegistry;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.BossEvent;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
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

        // Process each online player
        for (ServerPlayer player : event.getServer().getPlayerList().getPlayers()) {
            processPlayerSpawn(player);
        }
    }

    private void processPlayerSpawn(ServerPlayer player) {
        UUID playerUUID = player.getUUID();
        ServerLevel level = player.serverLevel();

        // Check if player has shinies disabled
        if (PlayerShinyData.isShinyDisabled(playerUUID)) {
            return;
        }

        // Check if player already has a shiny mob
        if (ShinyConfig.ONE_SHINY_PER_PLAYER.get()) {
            if (hasActiveShiny(player)) {
                return;
            }
        }

        // Initialize or update spawn timer
        int currentTimer = EGShiny.PLAYER_SPAWN_TIMERS.getOrDefault(playerUUID, -1);
        if (currentTimer == -1) {
            // Initialize with random time
            int spawnTimeMinutes = getNextSpawnTimeMinutes();
            EGShiny.PLAYER_SPAWN_TIMERS.put(playerUUID, minutesToTicks(spawnTimeMinutes));
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
                EGShiny.PLAYER_SPAWN_TIMERS.put(playerUUID, minutesToTicks(getNextSpawnTimeMinutes()));
            }
        }
    }

    /**
     * Default shiny spawn used by the timer. Uses the profile registry for random selection.
     */
    public static void spawnShinyMob(ServerPlayer player, ServerLevel level) {
        spawnShinyMob(player, level, null);
    }

    /**
     * Test / flexible shiny spawn allows forcing a specific EntityType.
     * If forcedType is null, a random eligible profile-based type is used.
     */
    public static boolean spawnShinyMob(ServerPlayer player, ServerLevel level, @Nullable EntityType<?> forcedType) {
        int spawnDistance = ShinyConfig.SPAWN_DISTANCE.get();

        // Determine whether hard-mode shinies are enabled for this player
        boolean hardMode = PlayerShinyData.isHardShinyEnabled(player.getUUID());

        SpawnChoice spawnChoice = chooseSpawn(player, level, spawnDistance, hardMode, forcedType);
        if (spawnChoice == null) {
            EGShiny.LOGGER.warn("Could not find an eligible shiny spawn near player {}", player.getName().getString());
            return false;
        }
        BlockPos spawnPos = spawnChoice.pos();
        EntityType<?> selectedType = spawnChoice.entityType();

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

        // Finalize vanilla mob setup before applying shiny-specific attributes and equipment.
        if (living instanceof Mob mob) {
            EventHooks.finalizeMobSpawn(
                    mob,
                    level,
                    level.getCurrentDifficultyAt(spawnPos),
                    MobSpawnType.COMMAND,
                    null
            );
        }

        ShinyMobHelper.makeShiny(living, level, hardMode);
        if (!ShinyMobHelper.isShiny(living)) {
            EGShiny.LOGGER.warn("Selected entity type {} is not eligible for shiny spawning in this mode.", selectedType);
            living.discard();
            return false;
        }
        ShinyMobHelper.setOwner(living, player.getUUID());

        // Spawn the entity
        if (!level.addFreshEntity(living)) {
            ShinyMobHelper.cleanupShinyVisuals(living, level);
            EGShiny.LOGGER.warn("Shiny mob spawn was blocked for player {} at {}", player.getName().getString(), spawnPos);
            return false;
        }

        // Track the shiny mob for this player
        EGShiny.PLAYER_SHINY_MOBS.put(player.getUUID(), living);
        ActiveShinyData.get(player.getServer()).track(player.getUUID(), living.getUUID());

        // Send notification to player
        if (ShinyConfig.SHOW_SPAWN_MESSAGE.get()) {
            notifySpawn(player, spawnPos);
        }

        EGShiny.LOGGER.info("Spawned shiny mob ({}) for player {} at {}",
                level.registryAccess().registryOrThrow(net.minecraft.core.registries.Registries.ENTITY_TYPE)
                        .getKey(selectedType),
                player.getName().getString(),
                spawnPos
        );
        return true;
    }

    @Nullable
    private static SpawnChoice chooseSpawn(ServerPlayer player, ServerLevel level, int spawnDistance, boolean hardMode,
                                           @Nullable EntityType<?> forcedType) {
        ShinyProfile forcedProfile = null;
        if (forcedType != null) {
            forcedProfile = ShinyProfileRegistry.getProfileFor(forcedType);
            if (forcedProfile == null || forcedProfile.hardShiny() && !hardMode) {
                return null;
            }
        }

        for (int attempt = 0; attempt < 10; attempt++) {
            BlockPos spawnPos = findSpawnPosition(player, level, spawnDistance);
            if (spawnPos == null) {
                continue;
            }

            if (forcedType != null) {
                if (forcedProfile.canSpawnAt(level, spawnPos)) {
                    return new SpawnChoice(spawnPos, forcedType);
                }
                continue;
            }

            EntityType<?> selectedType = ShinyProfileRegistry.getRandomShinyEntityType(hardMode, RANDOM, level, spawnPos);
            if (selectedType != null) {
                return new SpawnChoice(spawnPos, selectedType);
            }
        }

        return null;
    }

    private record SpawnChoice(BlockPos pos, EntityType<?> entityType) {
    }

    private static boolean hasActiveShiny(ServerPlayer player) {
        UUID playerUUID = player.getUUID();
        LivingEntity trackedMob = EGShiny.PLAYER_SHINY_MOBS.get(playerUUID) instanceof LivingEntity living ? living : null;

        if (trackedMob != null && trackedMob.isAlive() && ShinyMobHelper.isShiny(trackedMob)) {
            return true;
        }

        EGShiny.PLAYER_SHINY_MOBS.remove(playerUUID);
        ActiveShinyData activeData = ActiveShinyData.get(player.getServer());
        if (activeData.getEntityUUID(playerUUID).isEmpty()) {
            return false;
        }

        return activeData.findLoadedEntity(player.getServer(), playerUUID)
                .map(living -> {
                    if (living.isAlive() && ShinyMobHelper.isShiny(living)) {
                        EGShiny.PLAYER_SHINY_MOBS.put(playerUUID, living);
                        return true;
                    }

                    activeData.clear(playerUUID);
                    return false;
                })
                .orElse(true);
    }

    private static void notifySpawn(ServerPlayer player, BlockPos spawnPos) {
        String mode = ShinyConfig.SPAWN_NOTIFICATION_MODE.get().toLowerCase(java.util.Locale.ROOT);
        if (!mode.equals("chat") && !mode.equals("title") && !mode.equals("bossbar") && !mode.equals("both")) {
            mode = "chat";
        }

        if ("title".equalsIgnoreCase(mode) || "both".equalsIgnoreCase(mode)) {
            player.connection.send(new net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket(
                    Component.literal("Shiny Mob").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD)
            ));
            player.connection.send(new net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket(
                    getSpawnHint(player, spawnPos)
            ));
        }

        if ("bossbar".equalsIgnoreCase(mode) || "both".equalsIgnoreCase(mode)) {
            var bossEvent = new net.minecraft.server.level.ServerBossEvent(
                    Component.literal("A Shiny Mob is nearby").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD),
                    BossEvent.BossBarColor.YELLOW,
                    BossEvent.BossBarOverlay.PROGRESS
            );
            bossEvent.setProgress(1.0F);
            bossEvent.addPlayer(player);
            player.getServer().tell(new net.minecraft.server.TickTask(
                    player.getServer().getTickCount() + ShinyConfig.SPAWN_BOSSBAR_SECONDS.get() * 20,
                    () -> bossEvent.removePlayer(player)
            ));
        }

        if ("chat".equalsIgnoreCase(mode) || "both".equalsIgnoreCase(mode)) {
            player.sendSystemMessage(getSpawnMessage(player, spawnPos));
        }
    }

    private static Component getSpawnMessage(@Nullable ServerPlayer player, BlockPos spawnPos) {
        if (ShinyConfig.SHOW_COORDINATES.get()) {
            return Component.literal(String.format(
                            "\u00A76\u00A7l\u2B50 A Shiny Mob has spawned at X: %d, Y: %d, Z: %d! \u2B50",
                            spawnPos.getX(), spawnPos.getY(), spawnPos.getZ()
                    ))
                    .withStyle(ChatFormatting.GOLD);
        }

        return player == null ? getBasicSpawnHint() : getSpawnHint(player, spawnPos);
    }

    private static Component getBasicSpawnHint() {
        return Component.literal("\u00A76\u00A7l\u2B50 A Shiny Mob has spawned nearby! \u2B50")
                .withStyle(ChatFormatting.GOLD);
    }

    private static Component getSpawnHint(ServerPlayer player, BlockPos spawnPos) {
        double dx = spawnPos.getX() + 0.5 - player.getX();
        double dz = spawnPos.getZ() + 0.5 - player.getZ();
        int distance = (int) Math.round(Math.sqrt(dx * dx + dz * dz));
        String direction = getDirection(dx, dz);

        return Component.literal(String.format(
                        "\u00A76\u00A7l\u2B50 A Shiny Mob stirs %s, about %d blocks away! \u2B50",
                        direction,
                        distance
                ))
                .withStyle(ChatFormatting.GOLD);
    }

    private static String getDirection(double dx, double dz) {
        double angle = Math.atan2(dz, dx);
        double eighthTurn = Math.PI / 4.0;
        int sector = Math.floorMod((int) Math.round(angle / eighthTurn), 8);

        return switch (sector) {
            case 0 -> "to the east";
            case 1 -> "to the south-east";
            case 2 -> "to the south";
            case 3 -> "to the south-west";
            case 4 -> "to the west";
            case 5 -> "to the north-west";
            case 6 -> "to the north";
            case 7 -> "to the north-east";
            default -> "nearby";
        };
    }

    private static int getNextSpawnTimeMinutes() {
        int minMinutes = ShinyConfig.MIN_SPAWN_TIME.get();
        int maxMinutes = ShinyConfig.MAX_SPAWN_TIME.get();
        int lower = Math.min(minMinutes, maxMinutes);
        int upper = Math.max(minMinutes, maxMinutes);
        return lower + RANDOM.nextInt(upper - lower + 1);
    }

    private static int minutesToTicks(int minutes) {
        return minutes * 60 * 20;
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
