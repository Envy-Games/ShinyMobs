package com.styenvy.egshiny.data;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public class ActiveShinyData extends SavedData {
    private static final String DATA_NAME = "egshiny_active_shinies";
    private static final String ENTRIES_TAG = "entries";
    private static final String PLAYER_TAG = "player";
    private static final String ENTITY_TAG = "entity";

    private final Map<UUID, UUID> activeShinies = new HashMap<>();

    public static ActiveShinyData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(factory(), DATA_NAME);
    }

    public static SavedData.Factory<ActiveShinyData> factory() {
        return new SavedData.Factory<>(ActiveShinyData::new, ActiveShinyData::load);
    }

    public static ActiveShinyData load(CompoundTag tag, HolderLookup.Provider registries) {
        ActiveShinyData data = new ActiveShinyData();
        ListTag entries = tag.getList(ENTRIES_TAG, Tag.TAG_COMPOUND);

        for (int i = 0; i < entries.size(); i++) {
            CompoundTag entry = entries.getCompound(i);
            if (entry.hasUUID(PLAYER_TAG) && entry.hasUUID(ENTITY_TAG)) {
                data.activeShinies.put(entry.getUUID(PLAYER_TAG), entry.getUUID(ENTITY_TAG));
            }
        }

        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag entries = new ListTag();

        for (Map.Entry<UUID, UUID> activeShiny : activeShinies.entrySet()) {
            CompoundTag entry = new CompoundTag();
            entry.putUUID(PLAYER_TAG, activeShiny.getKey());
            entry.putUUID(ENTITY_TAG, activeShiny.getValue());
            entries.add(entry);
        }

        tag.put(ENTRIES_TAG, entries);
        return tag;
    }

    public void track(UUID playerUUID, UUID entityUUID) {
        activeShinies.put(playerUUID, entityUUID);
        setDirty();
    }

    public void clear(UUID playerUUID) {
        if (activeShinies.remove(playerUUID) != null) {
            setDirty();
        }
    }

    public void clearAll() {
        if (!activeShinies.isEmpty()) {
            activeShinies.clear();
            setDirty();
        }
    }

    public Optional<UUID> getEntityUUID(UUID playerUUID) {
        return Optional.ofNullable(activeShinies.get(playerUUID));
    }

    public Optional<LivingEntity> findLoadedEntity(MinecraftServer server, UUID playerUUID) {
        UUID entityUUID = activeShinies.get(playerUUID);
        if (entityUUID == null) {
            return Optional.empty();
        }

        for (ServerLevel level : server.getAllLevels()) {
            Entity entity = level.getEntity(entityUUID);
            if (entity instanceof LivingEntity living) {
                return Optional.of(living);
            }
        }

        return Optional.empty();
    }
}
