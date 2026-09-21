/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * Copyright (c) 2026 Joshua Kac -- NeoForge port (BuildCraft 10)
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL
 * was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.lib.net;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

import net.neoforged.neoforge.network.handling.IPayloadContext;

import buildcraft.api.core.BuildCraftAPI;

import buildcraft.lib.marker.MarkerCache;

/** Broadcasts a {@link MarkerCache}'s marker-load/marker-unload/connection changes to clients, addressed by the
 * cache's own index in {@link MarkerCache#CACHES}. Server-to-client only -- see {@code BCNetwork}, this
 * message's registration point, for the {@code playToClient} registration that enforces that.
 *
 * <p>1.12.2 built this by mutating a shared instance's {@code add}/{@code multiple}/{@code connection}/
 * {@code cacheId}/{@code count}/{@code positions} fields before handing it to {@code MessageManager}.
 * {@code multiple}/{@code count} existed purely to decide whether a count needed writing to the wire -- an
 * encoding detail, not message state -- so both are folded into {@link #STREAM_CODEC} and dropped from this
 * record's public shape; every caller already had the real {@code positions} list in hand (see
 * {@code MarkerSubCache}), so nothing is lost by constructing this directly rather than mutating an empty one. */
public record MessageMarker(boolean add, boolean connection, int cacheId, List<BlockPos> positions)
    implements CustomPacketPayload {
    public static final Type<MessageMarker> TYPE = new Type<>(BuildCraftAPI.nameToResourceId("marker"));

    public static final StreamCodec<FriendlyByteBuf, MessageMarker> STREAM_CODEC = StreamCodec.of(
        (buf, message) -> {
            buf.writeBoolean(message.add);
            buf.writeBoolean(message.connection);
            boolean multiple = message.positions.size() != 1;
            buf.writeBoolean(multiple);
            buf.writeShort(message.cacheId);
            if (multiple) {
                buf.writeShort(message.positions.size());
            }
            for (BlockPos pos : message.positions) {
                buf.writeBlockPos(pos);
            }
        },
        buf -> {
            boolean add = buf.readBoolean();
            boolean connection = buf.readBoolean();
            boolean multiple = buf.readBoolean();
            int cacheId = buf.readUnsignedShort();
            int count = multiple ? buf.readUnsignedShort() : 1;
            List<BlockPos> positions = new ArrayList<>(count);
            for (int i = 0; i < count; i++) {
                positions.add(buf.readBlockPos());
            }
            return new MessageMarker(add, connection, cacheId, positions);
        }
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(MessageMarker message, IPayloadContext ctx) {
        Player player = ctx.player();
        Level level = player == null ? null : player.level();
        if (level == null) {
            return;
        }
        if (message.cacheId < 0 || message.cacheId >= MarkerCache.CACHES.size()) {
            return;
        }
        MarkerCache<?> cache = MarkerCache.CACHES.get(message.cacheId);
        cache.getSubCache(level).handleMessageMain(message);
    }
}
