/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * Copyright (c) 2026 Joshua Kac -- NeoForge port (BuildCraft 10)
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL
 * was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.lib.net;

import io.netty.buffer.ByteBuf;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

import net.neoforged.neoforge.network.handling.IPayloadContext;

import buildcraft.api.core.BCLog;
import buildcraft.api.core.BuildCraftAPI;

/** Routes an opaque payload to whatever {@link IPayloadReceiver} block entity sits at {@link #pos}, on
 * whichever side receives it -- this is BuildCraft's one generic "sync/ask a tile something" envelope, not a
 * specific tile's own message shape.
 *
 * <p>1.12.2 registered this once per direction against a dynamically-assigned numeric id, resolved at FML
 * postInit by {@code MessageManager}. The modern payload system registers each message individually and
 * statically instead (see {@code BCNetwork}, this message's registration point) -- there is no generic
 * dispatch table left to build, so {@code MessageManager} itself is not ported; every message just is its own
 * {@link CustomPacketPayload}. {@code IMessageHandler}'s return-a-reply contract is gone too:
 * {@link IPayloadContext#reply} exists directly on the context a handler already receives, so
 * {@link IPayloadReceiver} has nothing left to hand back.
 *
 * <p>{@code MessageContext}'s side-based player lookup (via {@code BCLibProxy}) is
 * {@link IPayloadContext#player()} directly on both sides. Main-thread scheduling
 * ({@code BCLibProxy.addScheduledTask}) is {@link IPayloadContext#enqueueWork}. */
public record MessageUpdateTile(BlockPos pos, ByteBuf payload) implements CustomPacketPayload {
    public static final Type<MessageUpdateTile> TYPE =
        new Type<>(BuildCraftAPI.nameToResourceId("update_tile"));

    public static final StreamCodec<FriendlyByteBuf, MessageUpdateTile> STREAM_CODEC = StreamCodec.of(
        (buf, message) -> {
            buf.writeBlockPos(message.pos);
            buf.writeVarInt(message.payload.readableBytes());
            buf.writeBytes(message.payload, message.payload.readerIndex(), message.payload.readableBytes());
        },
        buf -> {
            BlockPos pos = buf.readBlockPos();
            int length = buf.readVarInt();
            return new MessageUpdateTile(pos, buf.readBytes(length));
        }
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(MessageUpdateTile message, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            Player player = ctx.player();
            Level level = player == null ? null : player.level();
            if (level == null) {
                return;
            }
            BlockEntity tile = level.getBlockEntity(message.pos);
            if (tile instanceof IPayloadReceiver receiver) {
                receiver.receivePayload(ctx, PacketBufferBC.asPacketBufferBc(message.payload));
            } else if (BCLog.logger.isDebugEnabled()) {
                BCLog.logger.debug("[lib.net] Dropped message for player " + player.getName().getString()
                    + " for tile at " + message.pos + " (found " + tile + ")");
            }
        });
    }
}
