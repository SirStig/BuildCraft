/*
 * Copyright (c) 2026 Joshua Kac -- NeoForge port (BuildCraft 10)
 *
 * This file is part of the BuildCraft 10 port and is distributed under the terms of the MIT License.
 * Please check the contents of the license, which should be located as "LICENSE.PORT" in the BuildCraft
 * source code distribution.
 */
package buildcraft.transport.client;

import net.minecraftforge.client.event.EntityRenderersEvent;

import buildcraft.transport.tile.RenderTilePipeHolder;

import buildcraft.BCTransportRegistries;

/**
 * Client-only renderer registration for {@code buildcraft.transport}. Mirrors the 26.x class of the same name,
 * and {@code buildcraft.energy.client.BCEnergyClientRegistries}/{@code buildcraft.factory.client.BCFactoryClientRegistries}
 * on this same target -- see either of those classes' own javadoc for the full account of why this has to be
 * gated at the listener registration itself. No {@code registerScreens} exists here: no pipe has a GUI in this
 * port yet.
 */
public final class BCTransportClientRegistries {

    private BCTransportClientRegistries() {}

    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(BCTransportRegistries.PIPE_HOLDER_TYPE.get(), RenderTilePipeHolder::new);
    }
}
