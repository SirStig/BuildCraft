/*
 * Copyright (c) 2026 Joshua Kac -- NeoForge port (BuildCraft 10)
 *
 * This file is part of the BuildCraft 10 port and is distributed under the terms of the MIT License.
 * Please check the contents of the license, which should be located as "LICENSE.PORT" in the BuildCraft
 * source code distribution.
 */
package buildcraft.energy.client;

import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.resources.ResourceLocation;

import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

import buildcraft.energy.gui.GuiEngineStone;

import buildcraft.BCCoreRegistries;
import buildcraft.BCEnergyRegistries;
import buildcraft.lib.engine.RenderTileEngine;

/**
 * Client-only menu screen and renderer registration for {@code buildcraft.energy}. Mirrors the 26.x class of the
 * same name, and {@code buildcraft.factory.client.BCFactoryClientRegistries} -- see that one's javadoc for the
 * full account of why this has to be gated at the *listener registration itself*, not just the method body.
 * {@link #registerRenderers} follows the identical rule for {@link RenderTileEngine}.
 */
public final class BCEnergyClientRegistries {

    private BCEnergyClientRegistries() {}

    public static void registerScreens(FMLClientSetupEvent event) {
        event.enqueueWork(() -> MenuScreens.register(BCEnergyRegistries.ENGINE_STONE_MENU.get(), GuiEngineStone::new));
    }

    /**
     * Registers {@link RenderTileEngine} for all three ported engines -- {@code ENGINE_WOOD}/{@code
     * ENGINE_CREATIVE} live in {@link BCCoreRegistries}, not here, since {@code buildcraft.core} owns those two
     * block/tile pairs (see {@code TileEngineWood}'s own javadoc); only {@code ENGINE_STONE} is actually this
     * module's own. Each registration supplies a different existing block texture -- see {@link RenderTileEngine}'s
     * own javadoc for why no new texture asset was authored for this pass.
     */
    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(
            BCCoreRegistries.ENGINE_WOOD_TYPE.get(),
            context -> new RenderTileEngine(context, new ResourceLocation("buildcraft", "block/engine_wood_side"))
        );
        event.registerBlockEntityRenderer(
            BCCoreRegistries.ENGINE_CREATIVE_TYPE.get(),
            context -> new RenderTileEngine(context, new ResourceLocation("buildcraft", "block/engine_creative_side"))
        );
        event.registerBlockEntityRenderer(
            BCEnergyRegistries.ENGINE_STONE_TYPE.get(),
            context -> new RenderTileEngine(context, new ResourceLocation("buildcraft", "block/engine_stone_side"))
        );
    }
}
