/*
 * Copyright (c) 2026 Joshua Kac -- NeoForge port (BuildCraft 10)
 *
 * This file is part of the BuildCraft 10 port and is distributed under the terms of the MIT License.
 * Please check the contents of the license, which should be located as "LICENSE.PORT" in the BuildCraft
 * source code distribution.
 */
package buildcraft.builders.client;

import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;

import buildcraft.builders.client.render.RenderQuarry;
import buildcraft.builders.gui.GuiFiller;

import buildcraft.BCBuildersRegistries;

/**
 * Client-only menu screen and renderer registration for {@code buildcraft.builders} -- the Filler's GUI and the
 * Quarry's laser renderer are the first ones this module needs; the Quarry itself previously rendered as a plain
 * textured cube with no entry here at all. Mirrors {@code BCFactoryClientRegistries}' structure and its
 * client-only listener-registration gating exactly -- see that class's own javadoc.
 */
public final class BCBuildersClientRegistries {

    private BCBuildersClientRegistries() {}

    public static void registerScreens(RegisterMenuScreensEvent event) {
        event.register(BCBuildersRegistries.FILLER_MENU.get(), GuiFiller::new);
    }

    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(BCBuildersRegistries.QUARRY_TYPE.get(), RenderQuarry::new);
    }
}
