/*
 * Copyright (c) 2026 Joshua Kac -- NeoForge port (BuildCraft 10)
 *
 * This file is part of the BuildCraft 10 port and is distributed under the terms of the MIT License.
 * Please check the contents of the license, which should be located as "LICENSE.PORT" in the BuildCraft
 * source code distribution.
 */
package buildcraft.energy.client;

import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;

import buildcraft.energy.gui.GuiEngineStone;

import buildcraft.BCEnergyRegistries;

/**
 * Client-only menu screen registration for {@code buildcraft.energy}. Mirrors
 * {@code buildcraft.factory.client.BCFactoryClientRegistries} exactly -- see that class's own javadoc for why
 * this has to be gated at the *listener registration itself* (in {@code BuildCraft}'s constructor), never just
 * inside the method body: a dedicated server must never have a reason to resolve {@link GuiEngineStone} (a
 * client-only type) at all.
 */
public final class BCEnergyClientRegistries {

    private BCEnergyClientRegistries() {}

    public static void registerScreens(RegisterMenuScreensEvent event) {
        event.register(BCEnergyRegistries.ENGINE_STONE_MENU.get(), GuiEngineStone::new);
    }
}
