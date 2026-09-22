/*
 * Copyright (c) 2026 Joshua Kac -- NeoForge port (BuildCraft 10)
 *
 * This file is part of the BuildCraft 10 port and is distributed under the terms of the MIT License.
 * Please check the contents of the license, which should be located as "LICENSE.PORT" in the BuildCraft
 * source code distribution.
 */
package buildcraft.factory.client;

import net.minecraft.client.gui.screens.MenuScreens;

import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

import buildcraft.factory.gui.GuiAutoCraftFluids;
import buildcraft.factory.gui.GuiAutoCraftItems;

import buildcraft.BCFactoryRegistries;

/**
 * Client-only menu screen registration for {@code buildcraft.factory}. Mirrors the 26.x class of the same name
 * -- see that one's javadoc for the full account of why this has to be gated at the *listener registration*
 * itself (never unconditionally reachable from a dedicated server), not just inside the method body.
 *
 * <p>{@code MenuScreens.register} has to run once client setup begins, so this listens for
 * {@link FMLClientSetupEvent} rather than reacting to a NeoForge-style {@code RegisterMenuScreensEvent} (which
 * doesn't exist on this target -- Forge/NeoForge 1.20.1 never grew a dedicated registration event for this).
 */
public final class BCFactoryClientRegistries {

    private BCFactoryClientRegistries() {}

    public static void registerScreens(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            MenuScreens.register(BCFactoryRegistries.AUTO_WORKBENCH_ITEMS_MENU.get(), GuiAutoCraftItems::new);
            MenuScreens.register(BCFactoryRegistries.AUTO_WORKBENCH_FLUIDS_MENU.get(), GuiAutoCraftFluids::new);
        });
    }
}
