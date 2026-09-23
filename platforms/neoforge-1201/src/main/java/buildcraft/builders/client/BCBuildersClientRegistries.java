/*
 * Copyright (c) 2026 Joshua Kac -- NeoForge port (BuildCraft 10)
 *
 * This file is part of the BuildCraft 10 port and is distributed under the terms of the MIT License.
 * Please check the contents of the license, which should be located as "LICENSE.PORT" in the BuildCraft
 * source code distribution.
 */
package buildcraft.builders.client;

import net.minecraft.client.gui.screens.MenuScreens;

import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

import buildcraft.builders.client.render.RenderQuarry;
import buildcraft.builders.gui.GuiFiller;

import buildcraft.BCBuildersRegistries;

/** Mirrors the 26.x class of the same name -- see {@code BCFactoryClientRegistries} (1201) for why this target
 * registers menu screens from {@link FMLClientSetupEvent} rather than a dedicated registration event. */
public final class BCBuildersClientRegistries {

    private BCBuildersClientRegistries() {}

    public static void registerScreens(FMLClientSetupEvent event) {
        event.enqueueWork(() -> MenuScreens.register(BCBuildersRegistries.FILLER_MENU.get(), GuiFiller::new));
    }

    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(BCBuildersRegistries.QUARRY_TYPE.get(), RenderQuarry::new);
    }
}
