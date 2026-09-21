/*
 * Copyright (c) 2026 Joshua Kac -- NeoForge port (BuildCraft 10)
 *
 * This file is part of the BuildCraft 10 port and is distributed under the terms of the MIT License.
 * Please check the contents of the license, which should be located as "LICENSE.PORT" in the BuildCraft
 * source code distribution.
 */
package buildcraft;

import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

import buildcraft.lib.net.MessageUpdateTile;

/**
 * Registers every {@code CustomPacketPayload} BuildCraft sends.
 *
 * <p>1.12.2's {@code MessageManager} dynamically assigned each registered message class a numeric id per mod
 * at FML postInit, then dispatched by class through a {@code SimpleNetworkWrapper}. That whole mechanism is
 * gone: NeoForge's payload system wants each message statically registered against a
 * {@code CustomPacketPayload.Type}, individually, up front -- there is no dynamic dispatch table left to build,
 * so nothing here replaces {@code MessageManager} itself. This is the direct equivalent of {@code BCRegistries}
 * for network messages: one call-out point, one entry per message, added here as each message lands.
 */
public final class BCNetwork {

    private BCNetwork() {}

    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(BuildCraft.MOD_ID).versioned("1");

        registrar.playBidirectional(MessageUpdateTile.TYPE, MessageUpdateTile.STREAM_CODEC, MessageUpdateTile::handle);
    }
}
