/*
 * Copyright (c) 2026 Joshua Kac -- NeoForge port (BuildCraft 10)
 *
 * This file is part of the BuildCraft 10 port and is distributed under the terms of the MIT License.
 * Please check the contents of the license, which should be located as "LICENSE.PORT" in the BuildCraft
 * source code distribution.
 */
package buildcraft.core.client;

import net.neoforged.neoforge.client.event.EntityRenderersEvent;

import buildcraft.core.client.render.RenderMarkerVolume;

import buildcraft.BCCoreRegistries;

/**
 * Client-only renderer registration for {@code buildcraft.core}: the volume marker's signal lasers
 * ({@link RenderMarkerVolume}).
 *
 * <p>Same shape, and same dedicated-server reasoning, as {@code BCFactoryClientRegistries}/
 * {@code BCTransportClientRegistries}: referenced only from behind the {@code FMLEnvironment.getDist().isClient()}
 * guard in {@code BuildCraft}'s constructor, so a dedicated server never loads or verifies it (or, transitively,
 * {@link RenderMarkerVolume}). The marker <em>connection</em> lasers need no registration here at all -- they are
 * drawn from level-render events by {@code buildcraft.core.client.render.RenderMarkerConnections}, which registers
 * itself through a client-only {@code @EventBusSubscriber}.
 */
public final class BCCoreClientRegistries {

    private BCCoreClientRegistries() {}

    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(BCCoreRegistries.MARKER_VOLUME_TYPE.get(), RenderMarkerVolume::new);
    }
}
