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
import java.util.function.Supplier;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

import net.minecraftforge.network.NetworkEvent;

import buildcraft.lib.marker.MarkerCache;

/** Broadcasts a {@link MarkerCache}'s marker-load/marker-unload/connection changes to clients, addressed by the
 * cache's own index in {@link MarkerCache#CACHES}. Server-to-client only -- see {@code BCNetwork}, this
 * message's registration point, for the {@code NetworkDirection.PLAY_TO_CLIENT} restriction that enforces that.
 *
 * <p>1.12.2 built this by mutating a shared instance's {@code add}/{@code multiple}/{@code connection}/
 * {@code cacheId}/{@code count}/{@code positions} fields before handing it to {@code MessageManager}.
 * {@code multiple}/{@code count} existed purely to decide whether a count needed writing to the wire -- an
 * encoding detail, not message state -- so both are folded into {@link #write}/{@link #read} and dropped from
 * this class' public shape; every caller already had the real {@code positions} list in hand (see
 * {@code MarkerSubCache}), so nothing is lost by constructing this directly rather than mutating an empty one.
 *
 * <p>{@link #handle} used to read {@code Minecraft.getInstance().player} directly, inline. That crashed a
 * dedicated server at mod construction -- a {@code BootstrapMethodError} loading {@code LocalPlayer} "for
 * invalid dist DEDICATED_SERVER" (confirmed by trying it) -- even though {@link #handle} itself is never
 * actually <em>invoked</em> server-side (this message is server-to-client only). {@code BCNetwork.register()}
 * still has to pass {@code MessageMarker::handle} as a method reference on both sides to register the codec, and
 * that alone is enough to load and bytecode-verify this whole class, including {@link #handle}'s body -- and
 * verifying an assignment from {@code Minecraft.player}'s declared type ({@code LocalPlayer}) to a {@code Player}
 * local needs the verifier to resolve {@code LocalPlayer}'s hierarchy, which is exactly what Forge's runtime
 * dist-cleaner refuses to load on that side. {@link ClientPlayerLookup} isolates that one touch into its own
 * class file, which only loads lazily when {@link ClientPlayerLookup#get()} genuinely runs -- never, on a
 * dedicated server, since nothing calls it there. There's no ported client-world helper to reuse for this
 * (BCLibProxy is not ported), so this is a small, purpose-built stand-in rather than a general one. */
public class MessageMarker {
    public final boolean add;
    public final boolean connection;
    public final int cacheId;
    public final List<BlockPos> positions;

    public MessageMarker(boolean add, boolean connection, int cacheId, List<BlockPos> positions) {
        this.add = add;
        this.connection = connection;
        this.cacheId = cacheId;
        this.positions = positions;
    }

    public static void write(MessageMarker message, FriendlyByteBuf buf) {
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
    }

    public static MessageMarker read(FriendlyByteBuf buf) {
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

    public static void handle(MessageMarker message, Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        ctx.enqueueWork(() -> {
            // Server-to-client only: NetworkEvent.Context#getSender() only ever returns a non-null player for
            // a message travelling the other way (confirmed via javap -- there is no client-side player
            // accessor on this context at all), so the receiving player has to come from the client itself --
            // see ClientPlayerLookup, and this class' own javadoc, for why that lookup can't just live here.
            Player player = ClientPlayerLookup.get();
            Level level = player == null ? null : player.level();
            if (level == null) {
                return;
            }
            if (message.cacheId < 0 || message.cacheId >= MarkerCache.CACHES.size()) {
                return;
            }
            MarkerCache<?> cache = MarkerCache.CACHES.get(message.cacheId);
            cache.getSubCache(level).handleMessageMain(message);
        });
        ctx.setPacketHandled(true);
    }

    /** Isolated into its own class file so the one line that touches a client-only type ({@code Minecraft}/
     * {@code LocalPlayer}) never appears in {@link MessageMarker}'s own bytecode -- see that class' javadoc for
     * why that distinction matters here. Nested rather than top-level purely to keep it next to its only
     * caller; nesting doesn't change when this class file actually loads, which is what matters. */
    private static final class ClientPlayerLookup {
        private ClientPlayerLookup() {
        }

        static Player get() {
            return Minecraft.getInstance().player;
        }
    }
}
