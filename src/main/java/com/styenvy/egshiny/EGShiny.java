package com.styenvy.egshiny;

import com.mojang.logging.LogUtils;
import com.styenvy.egshiny.commands.ShinyCommands;
import com.styenvy.egshiny.config.ShinyConfig;
import com.styenvy.egshiny.data.PlayerShinyData;
import com.styenvy.egshiny.events.ShinyEventHandler;
import com.styenvy.egshiny.spawn.ShinySpawnManager;
import net.minecraft.world.entity.Entity;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import org.slf4j.Logger;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Mod(EGShiny.MODID)
public class EGShiny {
    public static final String MODID = "egshiny";
    public static final Logger LOGGER = LogUtils.getLogger();
    
    // Track shiny mobs per player
    public static final Map<UUID, Entity> PLAYER_SHINY_MOBS = new HashMap<>();
    // Track spawn timers per player
    public static final Map<UUID, Integer> PLAYER_SPAWN_TIMERS = new HashMap<>();
    
    public EGShiny(IEventBus modEventBus, ModContainer modContainer) {
        // Register config
        modContainer.registerConfig(ModConfig.Type.COMMON, ShinyConfig.SPEC);
        
        // Register mod event listeners
        modEventBus.addListener(this::commonSetup);
        
        // Register NeoForge event listeners
        NeoForge.EVENT_BUS.register(new ShinyEventHandler());
        NeoForge.EVENT_BUS.register(new ShinySpawnManager());
        NeoForge.EVENT_BUS.addListener(this::onServerStarting);
        NeoForge.EVENT_BUS.addListener(this::onServerStopping);
        NeoForge.EVENT_BUS.addListener(this::registerCommands);
        
        LOGGER.info("EG Shiny Mobs mod initialized!");
    }
    
    private void commonSetup(final FMLCommonSetupEvent event) {
        LOGGER.info("EG Shiny Mobs Common Setup");
    }
    
    private void onServerStarting(ServerStartingEvent event) {
        // Load player data when server starts
        PlayerShinyData.load(event.getServer());
    }
    
    private void onServerStopping(ServerStoppingEvent event) {
        // Save player data when server stops
        PlayerShinyData.save(event.getServer());
        
        // Clear tracked entities
        PLAYER_SHINY_MOBS.clear();
        PLAYER_SPAWN_TIMERS.clear();
    }
    
    private void registerCommands(RegisterCommandsEvent event) {
        ShinyCommands.register(event.getDispatcher());
    }
}
