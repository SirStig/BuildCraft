/*
 * Copyright (c) 2026 Joshua Kac -- NeoForge port (BuildCraft 10)
 *
 * This file is part of the BuildCraft 10 port and is distributed under the terms of the MIT License.
 * Please check the contents of the license, which should be located as "LICENSE.PORT" in the BuildCraft
 * source code distribution.
 */
package buildcraft;

import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.fml.loading.FMLLoader;

import buildcraft.api.core.BCDebugging;

import buildcraft.energy.client.BCEnergyClientRegistries;
import buildcraft.factory.client.BCFactoryClientRegistries;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Entry point for BuildCraft on Minecraft 1.20.1.
 *
 * <p>1.20.1 NeoForge is a direct fork of MinecraftForge, so the loader API here is still the
 * {@code net.minecraftforge.*} one -- notably {@code RegistryObject}/{@code ForgeRegistries}
 * instead of the {@code DeferredHolder}/{@code Registries} pair used by the 26.x target.
 * The two platform source trees are deliberately kept separate for that reason; shared,
 * Minecraft-free logic lives in the {@code :expression} and {@code :shared} modules.
 */
@Mod(BuildCraft.MOD_ID)
public final class BuildCraft {

    public static final String MOD_ID = "buildcraft";

    public static final Logger LOGGER = LoggerFactory.getLogger("BuildCraft");

    public BuildCraft() {
        // Must happen before anything reads a debug option, since those are resolved once.
        BCDebugging.setDevEnvironment(!FMLLoader.isProduction());

        IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();
        LOGGER.info("BuildCraft starting up (Minecraft 1.20.1).");

        BCRegistries.register(modBus);
        BCFactoryRegistries.register(modBus);
        BCEnergyRegistries.register(modBus);
        BCTransportRegistries.register(modBus);
        BCNetwork.register();

        // Screen registration is inherently client-only. Gating the *listener registration itself* (rather than
        // just the body of the method it would call) keeps a dedicated server from ever having to load or verify
        // BCFactoryClientRegistries/BCEnergyClientRegistries -- and, transitively, the client-only Screen classes
        // they reference -- at all. See BCFactoryClientRegistries' own javadoc.
        if (FMLEnvironment.dist.isClient()) {
            modBus.addListener(BCFactoryClientRegistries::registerScreens);
            modBus.addListener(BCEnergyClientRegistries::registerScreens);
        }
    }
}
