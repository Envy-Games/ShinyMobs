package com.styenvy.egshiny.data;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.styenvy.egshiny.EGShiny;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class PlayerShinyData {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String DATA_FILE_NAME = "egshiny_player_data.json";
    private static final String HARD_DATA_FILE_NAME = "egshiny_player_hard_data.json";

    // Map of player UUID to their shiny enabled status (true = enabled, false = disabled)
    private static final Map<UUID, Boolean> playerShinyStatus = new HashMap<>();
    // Map of player UUID to their hard-mode shiny preference (true = hard mode on)
    private static final Map<UUID, Boolean> playerHardShinyStatus = new HashMap<>();

    public static void load(MinecraftServer server) {
        // Base enabled / disabled map
        File dataFile = getDataFile(server);

        if (dataFile.exists()) {
            try (FileReader reader = new FileReader(dataFile)) {
                Type type = new TypeToken<Map<String, Boolean>>() {}.getType();
                Map<String, Boolean> stringMap = GSON.fromJson(reader, type);

                if (stringMap != null) {
                    playerShinyStatus.clear();
                    for (Map.Entry<String, Boolean> entry : stringMap.entrySet()) {
                        try {
                            UUID uuid = UUID.fromString(entry.getKey());
                            playerShinyStatus.put(uuid, entry.getValue());
                        } catch (IllegalArgumentException e) {
                            EGShiny.LOGGER.warn("Invalid UUID in player data: {}", entry.getKey());
                        }
                    }
                    EGShiny.LOGGER.info("Loaded shiny mob data for {} players", playerShinyStatus.size());
                }
            } catch (IOException e) {
                EGShiny.LOGGER.error("Failed to load player shiny data", e);
            }
        } else {
            EGShiny.LOGGER.info("No existing shiny mob player data found, starting fresh");
        }

        // Hard-mode preference map (separate file, optional)
        File hardFile = getHardDataFile(server);
        if (hardFile.exists()) {
            try (FileReader reader = new FileReader(hardFile)) {
                Type type = new TypeToken<Map<String, Boolean>>() {}.getType();
                Map<String, Boolean> stringMap = GSON.fromJson(reader, type);

                if (stringMap != null) {
                    playerHardShinyStatus.clear();
                    for (Map.Entry<String, Boolean> entry : stringMap.entrySet()) {
                        try {
                            UUID uuid = UUID.fromString(entry.getKey());
                            playerHardShinyStatus.put(uuid, entry.getValue());
                        } catch (IllegalArgumentException e) {
                            EGShiny.LOGGER.warn("Invalid UUID in hard-mode player data: {}", entry.getKey());
                        }
                    }
                    EGShiny.LOGGER.info("Loaded hard-mode shiny data for {} players", playerHardShinyStatus.size());
                }
            } catch (IOException e) {
                EGShiny.LOGGER.error("Failed to load hard-mode player shiny data", e);
            }
        } else {
            EGShiny.LOGGER.info("No existing hard-mode shiny player data found, starting fresh");
        }
    }

    public static void save(MinecraftServer server) {
        // Base enabled / disabled map
        File dataFile = getDataFile(server);

        try {
            // Ensure directory exists
            Path parent = dataFile.toPath().getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }

            // Convert UUID to String for JSON serialization
            Map<String, Boolean> stringMap = new HashMap<>();
            for (Map.Entry<UUID, Boolean> entry : playerShinyStatus.entrySet()) {
                stringMap.put(entry.getKey().toString(), entry.getValue());
            }

            try (FileWriter writer = new FileWriter(dataFile)) {
                GSON.toJson(stringMap, writer);
                EGShiny.LOGGER.info("Saved shiny mob data for {} players", playerShinyStatus.size());
            }
        } catch (IOException e) {
            EGShiny.LOGGER.error("Failed to save player shiny data", e);
        }

        // Hard-mode preference map
        File hardFile = getHardDataFile(server);
        try {
            Path parent = hardFile.toPath().getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }

            Map<String, Boolean> stringMap = new HashMap<>();
            for (Map.Entry<UUID, Boolean> entry : playerHardShinyStatus.entrySet()) {
                stringMap.put(entry.getKey().toString(), entry.getValue());
            }

            try (FileWriter writer = new FileWriter(hardFile)) {
                GSON.toJson(stringMap, writer);
                EGShiny.LOGGER.info("Saved hard-mode shiny data for {} players", playerHardShinyStatus.size());
            }
        } catch (IOException e) {
            EGShiny.LOGGER.error("Failed to save hard-mode player shiny data", e);
        }
    }

    private static File getDataFile(MinecraftServer server) {
        // Store in world's data folder
        Path worldPath = server.getWorldPath(LevelResource.ROOT);
        Path dataPath = worldPath.resolve("data").resolve(DATA_FILE_NAME);
        return dataPath.toFile();
    }

    private static File getHardDataFile(MinecraftServer server) {
        // Store in world's data folder
        Path worldPath = server.getWorldPath(LevelResource.ROOT);
        Path dataPath = worldPath.resolve("data").resolve(HARD_DATA_FILE_NAME);
        return dataPath.toFile();
    }

    public static boolean isShinyDisabled(UUID playerUUID) {
        // If not in map, default to enabled (true means enabled, so return false for "is disabled")
        return !playerShinyStatus.getOrDefault(playerUUID, true);
    }

    public static void setShinyEnabled(UUID playerUUID, boolean enabled) {
        playerShinyStatus.put(playerUUID, enabled);
    }

    public static boolean isHardShinyEnabled(UUID playerUUID) {
        return playerHardShinyStatus.getOrDefault(playerUUID, false);
    }

    public static void setHardShinyEnabled(UUID playerUUID, boolean enabled) {
        playerHardShinyStatus.put(playerUUID, enabled);
    }

    public static void removePlayer(UUID playerUUID) {
        playerShinyStatus.remove(playerUUID);
        playerHardShinyStatus.remove(playerUUID);
    }

    public static Map<UUID, Boolean> getAllPlayerData() {
        return new HashMap<>(playerShinyStatus);
    }
}
