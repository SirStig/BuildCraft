/*
 * Copyright (c) 2026 Joshua Kac -- NeoForge port (BuildCraft 10)
 *
 * This file is part of the BuildCraft 10 port and is distributed under the terms of the MIT License.
 * Please check the contents of the license, which should be located as "LICENSE.PORT" in the BuildCraft
 * source code distribution.
 */
package buildcraft.transport.client;

import net.neoforged.neoforge.client.event.EntityRenderersEvent;

import buildcraft.transport.tile.RenderTilePipeHolder;

import buildcraft.BCTransportRegistries;

/**
 * Client-only renderer registration for {@code buildcraft.transport} -- the first this module has needed, now
 * that {@link RenderTilePipeHolder} exists. Mirrors {@code buildcraft.energy.client.BCEnergyClientRegistries}/
 * {@code buildcraft.factory.client.BCFactoryClientRegistries} exactly, including why this has to be gated at the
 * *listener registration itself* (in {@code BuildCraft}'s constructor), never just inside the method body -- see
 * either of those classes' own javadoc for the full account. No {@code registerScreens} exists here: no pipe has
 * a GUI in this port yet.
 */
public final class BCTransportClientRegistries {

    private BCTransportClientRegistries() {}

    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(BCTransportRegistries.PIPE_HOLDER_TYPE.get(), RenderTilePipeHolder::new);
    }
}
