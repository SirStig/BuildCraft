/*
 * Copyright (c) 2026 Joshua Kac -- NeoForge port (BuildCraft 10)
 *
 * This file is part of the BuildCraft 10 port and is distributed under the terms of the MIT License.
 * Please check the contents of the license, which should be located as "LICENSE.PORT" in the BuildCraft
 * source code distribution.
 */
package buildcraft.silicon.client;

import net.minecraft.client.gui.screens.MenuScreens;

import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

import buildcraft.silicon.gui.GuiAdvancedCraftingTable;
import buildcraft.silicon.gui.GuiAssemblyTable;
import buildcraft.silicon.gui.GuiIntegrationTable;

import buildcraft.BCSiliconRegistries;

/** Mirrors the 26.x class of the same name -- see that one's own javadoc and
 * {@code buildcraft.factory.client.BCFactoryClientRegistries}'s (1.20.1) own javadoc for why this listens for
 * {@link FMLClientSetupEvent} rather than a NeoForge-style registration event. */
public final class BCSiliconClientRegistries {

    private BCSiliconClientRegistries() {}

    public static void registerScreens(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            MenuScreens.register(BCSiliconRegistries.ASSEMBLY_TABLE_MENU.get(), GuiAssemblyTable::new);
            MenuScreens.register(BCSiliconRegistries.ADVANCED_CRAFTING_TABLE_MENU.get(), GuiAdvancedCraftingTable::new);
            MenuScreens.register(BCSiliconRegistries.INTEGRATION_TABLE_MENU.get(), GuiIntegrationTable::new);
        });
    }
}
