/*
 * Copyright (c) 2026 Joshua Kac -- NeoForge port (BuildCraft 10)
 *
 * This file is part of the BuildCraft 10 port and is distributed under the terms of the MIT License.
 * Please check the contents of the license, which should be located as "LICENSE.PORT" in the BuildCraft
 * source code distribution.
 */
package buildcraft.energy.client;

import net.minecraft.client.gui.screens.MenuScreens;

import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

import buildcraft.energy.gui.GuiEngineStone;

import buildcraft.BCEnergyRegistries;

/**
 * Client-only menu screen registration for {@code buildcraft.energy}. Mirrors the 26.x class of the same name,
 * and {@code buildcraft.factory.client.BCFactoryClientRegistries} -- see that one's javadoc for the full account
 * of why this has to be gated at the *listener registration itself*, not just the method body.
 */
public final class BCEnergyClientRegistries {

    private BCEnergyClientRegistries() {}

    public static void registerScreens(FMLClientSetupEvent event) {
        event.enqueueWork(() -> MenuScreens.register(BCEnergyRegistries.ENGINE_STONE_MENU.get(), GuiEngineStone::new));
    }
}
