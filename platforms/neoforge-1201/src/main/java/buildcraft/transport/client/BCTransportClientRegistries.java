/*
 * Copyright (c) 2026 Joshua Kac -- NeoForge port (BuildCraft 10)
 *
 * This file is part of the BuildCraft 10 port and is distributed under the terms of the MIT License.
 * Please check the contents of the license, which should be located as "LICENSE.PORT" in the BuildCraft
 * source code distribution.
 */
package buildcraft.transport.client;

import net.minecraft.client.gui.screens.MenuScreens;

import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

import buildcraft.transport.gui.GuiDiamondPipe;
import buildcraft.transport.gui.GuiDiamondWoodPipe;
import buildcraft.transport.gui.GuiGate;
import buildcraft.transport.tile.RenderTilePipeHolder;

import buildcraft.BCTransportRegistries;

/**
 * Client-only renderer/screen registration for {@code buildcraft.transport}. Mirrors the 26.x class of the same
 * name, and {@code buildcraft.energy.client.BCEnergyClientRegistries}/
 * {@code buildcraft.factory.client.BCFactoryClientRegistries} on this same target -- see either of those
 * classes' own javadoc for the full account of why this has to be gated at the listener registration itself, and
 * of why {@link #registerScreens} listens for {@link FMLClientSetupEvent} rather than a NeoForge-style
 * {@code RegisterMenuScreensEvent} on this target. {@link #registerScreens} is new this batch: the diamond
 * pipes are the first pipe material with a GUI.
 */
public final class BCTransportClientRegistries {

    private BCTransportClientRegistries() {}

    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(BCTransportRegistries.PIPE_HOLDER_TYPE.get(), RenderTilePipeHolder::new);
    }

    public static void registerScreens(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            MenuScreens.register(BCTransportRegistries.PIPE_DIAMOND_MENU.get(), GuiDiamondPipe::new);
            MenuScreens.register(BCTransportRegistries.PIPE_DIAMOND_WOOD_MENU.get(), GuiDiamondWoodPipe::new);
            MenuScreens.register(BCTransportRegistries.GATE_MENU.get(), GuiGate::new);
        });
    }
}
