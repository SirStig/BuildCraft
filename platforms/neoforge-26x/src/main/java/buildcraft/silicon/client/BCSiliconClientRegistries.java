/*
 * Copyright (c) 2026 Joshua Kac -- NeoForge port (BuildCraft 10)
 *
 * This file is part of the BuildCraft 10 port and is distributed under the terms of the MIT License.
 * Please check the contents of the license, which should be located as "LICENSE.PORT" in the BuildCraft
 * source code distribution.
 */
package buildcraft.silicon.client;

import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;

import buildcraft.silicon.client.render.RenderLaser;
import buildcraft.silicon.gui.GuiAdvancedCraftingTable;
import buildcraft.silicon.gui.GuiAssemblyTable;
import buildcraft.silicon.gui.GuiIntegrationTable;

import buildcraft.BCSiliconRegistries;

/**
 * Client-only menu screen and renderer registration for the {@code buildcraft.silicon} standalone machines.
 * Mirrors {@code buildcraft.factory.client.BCFactoryClientRegistries}'s structure and its reasoning for why this
 * is a separate class only ever referenced from behind a client-dist guard in {@code BuildCraft}'s constructor.
 *
 * <p>None of the four tables has a custom {@code BlockEntityRenderer} this round. The laser emitter does --
 * {@link RenderLaser}, registered by {@link #registerRenderers} -- matching {@code BCFactoryClientRegistries}'s
 * own {@code registerRenderers}. The charging table has no menu at all -- see
 * {@code buildcraft.silicon.tile.TileChargingTable}'s own javadoc.
 */
public final class BCSiliconClientRegistries {

    private BCSiliconClientRegistries() {}

    public static void registerScreens(RegisterMenuScreensEvent event) {
        event.register(BCSiliconRegistries.ASSEMBLY_TABLE_MENU.get(), GuiAssemblyTable::new);
        event.register(BCSiliconRegistries.ADVANCED_CRAFTING_TABLE_MENU.get(), GuiAdvancedCraftingTable::new);
        event.register(BCSiliconRegistries.INTEGRATION_TABLE_MENU.get(), GuiIntegrationTable::new);
    }

    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(BCSiliconRegistries.LASER_TYPE.get(), RenderLaser::new);
    }
}
