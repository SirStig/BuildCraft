/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * Copyright (c) 2026 Joshua Kac -- NeoForge port (BuildCraft 10)
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL
 * was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.lib.net;

import java.util.function.Supplier;

import io.netty.buffer.ByteBuf;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

import net.minecraftforge.network.NetworkEvent;

import buildcraft.api.core.BCLog;

/** Routes an opaque payload to whatever {@link IPayloadReceiver} block entity sits at {@link #pos}, on
 * whichever side receives it -- this is BuildCraft's one generic "sync/ask a tile something" envelope, not a
 * specific tile's own message shape.
 *
 * <p>1.12.2 registered this once per direction against a dynamically-assigned numeric id, resolved at FML
 * postInit by {@code MessageManager}. This target's {@code SimpleChannel} still wants a numeric id per message,
 * but assigned once, statically, at registration time (see {@code BCNetwork}, this message's registration
 * point) -- there is no dynamic per-mod dispatch table left to build, so {@code MessageManager} itself is not
 * ported; every message is just registered for itself. {@code IMessageHandler}'s return-a-reply contract is
 * gone too: {@code NetworkEvent.Context#reply} exists directly on the context a handler already receives, so
 * {@link IPayloadReceiver} has nothing left to hand back.
 *
 * <p>{@code MessageContext}'s side-based player lookup (via {@code BCLibProxy}) is
 * {@code NetworkEvent.Context#getSender()} on the server side (this message is only ever handled server-side
 * in practice, since the client already knows what it asked for). Main-thread scheduling
 * ({@code BCLibProxy.addScheduledTask}) is {@code NetworkEvent.Context#enqueueWork}. */
public class MessageUpdateTile {
    public final BlockPos pos;
    public final ByteBuf payload;

    public MessageUpdateTile(BlockPos pos, ByteBuf payload) {
        this.pos = pos;
        this.payload = payload;
    }

    public static void write(MessageUpdateTile message, FriendlyByteBuf buf) {
        buf.writeBlockPos(message.pos);
        buf.writeVarInt(message.payload.readableBytes());
        buf.writeBytes(message.payload, message.payload.readerIndex(), message.payload.readableBytes());
    }

    public static MessageUpdateTile read(FriendlyByteBuf buf) {
        BlockPos pos = buf.readBlockPos();
        int length = buf.readVarInt();
        return new MessageUpdateTile(pos, buf.readBytes(length));
    }

    public static void handle(MessageUpdateTile message, Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        ctx.enqueueWork(() -> {
            Player player = ctx.getSender();
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
        ctx.setPacketHandled(true);
    }
}
