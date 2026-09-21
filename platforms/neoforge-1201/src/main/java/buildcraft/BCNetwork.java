/*
 * Copyright (c) 2026 Joshua Kac -- NeoForge port (BuildCraft 10)
 *
 * This file is part of the BuildCraft 10 port and is distributed under the terms of the MIT License.
 * Please check the contents of the license, which should be located as "LICENSE.PORT" in the BuildCraft
 * source code distribution.
 */
package buildcraft;

import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Supplier;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

import buildcraft.lib.net.MessageUpdateTile;

/**
 * Registers every network message BuildCraft sends.
 *
 * <p>1.12.2's {@code MessageManager} dynamically assigned each registered message class a numeric id per mod
 * at FML postInit, then dispatched by class through a {@code SimpleNetworkWrapper}. This target keeps
 * {@code SimpleChannel}, the same networking layer 1.12.2 used, but there is exactly one BuildCraft mod id now
 * (not one channel per module), so this collapses to a single channel with each message statically registered
 * against its own id up front -- the direct equivalent of {@code BCRegistries} for network messages, one
 * call-out point with one entry per message, added here as each message lands.
 */
public final class BCNetwork {

    private static final String PROTOCOL_VERSION = "1";

    private static final SimpleChannel CHANNEL = NetworkRegistry.ChannelBuilder
        .named(new ResourceLocation(BuildCraft.MOD_ID, "main"))
        .networkProtocolVersion(() -> PROTOCOL_VERSION)
        .clientAcceptedVersions(PROTOCOL_VERSION::equals)
        .serverAcceptedVersions(PROTOCOL_VERSION::equals)
        .simpleChannel();

    private static int nextId = 0;

    private BCNetwork() {}

    public static void register() {
        registerMessage(MessageUpdateTile.class, MessageUpdateTile::write, MessageUpdateTile::read,
            MessageUpdateTile::handle);
    }

    private static <T> void registerMessage(Class<T> messageClass, BiConsumer<T, FriendlyByteBuf> encoder,
        Function<FriendlyByteBuf, T> decoder, BiConsumer<T, Supplier<NetworkEvent.Context>> handler) {
        CHANNEL.registerMessage(nextId++, messageClass, encoder, decoder, handler);
    }
}
