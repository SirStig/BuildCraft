/*
 * Copyright (c) 2026 Joshua Kac -- NeoForge port (BuildCraft 10)
 *
 * This file is part of the BuildCraft 10 port and is distributed under the terms of the MIT License.
 * Please check the contents of the license, which should be located as "LICENSE.PORT" in the BuildCraft
 * source code distribution.
 */
package buildcraft;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.fml.loading.FMLLoader;

import buildcraft.api.core.BCDebugging;

import buildcraft.energy.client.BCEnergyClientRegistries;
import buildcraft.factory.client.BCFactoryClientRegistries;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Entry point for BuildCraft on Minecraft 26.x.
 *
 * <p>The 1.12.2 build shipped as eight separate mod ids (buildcraftcore, buildcraftlib,
 * buildcrafttransport, ...) held together by FML's {@code parent} mechanism, which no longer
 * exists. The port collapses them into a single {@code buildcraft} mod id; the old module
 * boundaries survive as packages and as separate {@code BC*} registration holders.
 */
@Mod(BuildCraft.MOD_ID)
public final class BuildCraft {

    public static final String MOD_ID = "buildcraft";

    public static final Logger LOGGER = LoggerFactory.getLogger("BuildCraft");

    public BuildCraft(IEventBus modBus, ModContainer container) {
        // Must happen before anything reads a debug option, since those are resolved once.
        BCDebugging.setDevEnvironment(!FMLLoader.getCurrent().isProduction());

        LOGGER.info("BuildCraft {} starting up.", container.getModInfo().getVersion());

        BCRegistries.register(modBus);
        BCFactoryRegistries.register(modBus);
        BCEnergyRegistries.register(modBus);
        modBus.addListener(BCNetwork::register);

        // Screen registration is inherently client-only. Gating the *listener registration itself* (rather than
        // just the body of the method it would call) keeps a dedicated server from ever having to load or verify
        // BCFactoryClientRegistries/BCEnergyClientRegistries -- and, transitively, the client-only Screen classes
        // they reference -- at all. See BCFactoryClientRegistries' own javadoc.
        if (FMLEnvironment.getDist().isClient()) {
            modBus.addListener(BCFactoryClientRegistries::registerScreens);
            modBus.addListener(BCEnergyClientRegistries::registerScreens);
        }
    }
}
