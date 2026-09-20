/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLLoader;

import buildcraft.api.core.BCDebugging;

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
    }
}
