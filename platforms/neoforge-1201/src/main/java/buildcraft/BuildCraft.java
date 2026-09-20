/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft;

import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLLoader;

import buildcraft.api.core.BCDebugging;

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
    }
}
