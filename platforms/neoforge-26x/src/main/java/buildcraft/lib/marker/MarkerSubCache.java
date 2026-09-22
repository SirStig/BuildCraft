/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * Copyright (c) 2026 Joshua Kac -- NeoForge port (BuildCraft 10)
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL
 * was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.lib.marker;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import com.google.common.collect.ImmutableList;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;

import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

import buildcraft.api.core.BCDebugging;
import buildcraft.api.core.BCLog;

import buildcraft.lib.client.render.laser.LaserData_BC8.LaserType;
import buildcraft.lib.net.MessageMarker;
import buildcraft.lib.tile.TileMarker;

/** Per-dimension state for one {@link MarkerCache} type: which positions have a loaded or unloaded marker tile,
 * and which of those positions are grouped into a {@link MarkerConnection}.
 *
 * <p>{@code World.provider.getDimension()} (an {@code int}) is {@link Level#dimension()}
 * ({@link ResourceKey}&lt;{@link Level}&gt;) now, and {@code World.isRemote} is {@link Level#isClientSide()}.
 * {@code EntityPlayerMP} is {@link ServerPlayer}.
 *
 * <p>{@link #getPossibleLaserType()} is back, with the laser-rendering pass (it was dropped in the first marker pass
 * for having nothing to call it). It was {@code @SideOnly(Side.CLIENT)} in 1.12.2; it needs no side guard here,
 * because {@link LaserType} is a plain value class holding only {@code Identifier}s/{@code ResourceLocation}s and
 * numbers -- naming one from common code never loads a client-only class.
 *
 * <p>1.12.2's {@code MessageManager.sendTo}/{@code sendToDimension} become {@code PacketDistributor}'s static
 * helpers on this target: {@link PacketDistributor#sendToPlayer} and
 * {@link PacketDistributor#sendToPlayersInDimension}, the latter needing an actual {@link ServerLevel} rather
 * than just its {@link ResourceKey}, resolved through {@link ServerLifecycleHooks#getCurrentServer()} --
 * {@code MinecraftServer#getLevel(ResourceKey)} is nullable if that dimension isn't currently loaded, so
 * {@link #sendToDimension} quietly drops the send rather than risk an NPE; in practice this is only ever called
 * while this very cache is processing a mutation for that dimension, so the level should always be present.
 *
 * <p>{@link #handleMessageMain}/{@link #handleMessage} were {@code @SideOnly(Side.CLIENT)} in 1.12.2; see
 * {@link buildcraft.api.tiles.IDebuggable}'s javadoc for why that annotation is gone and these stay plain
 * instead -- they are simply only ever called from {@link MessageMarker#handle}, which only runs client-side. */
public abstract class MarkerSubCache<C extends MarkerConnection<C>> {
    public static final boolean DEBUG_FULL = BCDebugging.shouldDebugComplex("lib.marker.full");

    public final int cacheId;
    public final ResourceKey<Level> dimensionId;
    public final boolean isServer;
    private final Map<BlockPos, C> posToConnection = new ConcurrentHashMap<>();
    private final Map<C, Set<BlockPos>> connectionToPos = new ConcurrentHashMap<>();
    private final Map<BlockPos, Optional<TileMarker<C>>> tileCache = new ConcurrentHashMap<>();

    public MarkerSubCache(Level level, int cacheId) {
        this.isServer = !level.isClientSide();
        this.dimensionId = level.dimension();
        this.cacheId = cacheId;
    }

    public void onPlayerJoinWorld(ServerPlayer player) {
        if (isServer) {// Sanity Check
            // Send ALL loaded markers
            if (!tileCache.isEmpty()) {
                MessageMarker message = new MessageMarker(true, false, cacheId, new ArrayList<>(tileCache.keySet()));
                PacketDistributor.sendToPlayer(player, message);
            }
            // Send ALL connections.
            for (C connection : connectionToPos.keySet()) {
                MessageMarker message = new MessageMarker(true, true, cacheId, new ArrayList<>(connection.getMarkerPositions()));
                PacketDistributor.sendToPlayer(player, message);
            }
        }
    }

    private void sendToDimension(MessageMarker message) {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        ServerLevel level = server == null ? null : server.getLevel(dimensionId);
        if (level != null) {
            PacketDistributor.sendToPlayersInDimension(level, message);
        }
    }

    public boolean hasLoadedOrUnloadedMarker(BlockPos pos) {
        return tileCache.containsKey(pos);
    }

    @Nullable
    public TileMarker<C> getMarker(BlockPos pos) {
        Optional<TileMarker<C>> op = tileCache.get(pos);
        if (op == null) {
            return null;
        } else {
            return op.orElse(null);
        }
    }

    public void loadMarker(BlockPos pos, @Nullable TileMarker<C> marker) {
        boolean did = tileCache.containsKey(pos);
        tileCache.put(pos, Optional.ofNullable(marker));
        if (DEBUG_FULL) {
            BCLog.logger.info("[lib.marker.full] Set a marker at " + pos + " as " + marker);
        }
        if (isServer && !did) {
            sendToDimension(new MessageMarker(true, false, cacheId, List.of(pos)));
        }
    }

    public void unloadMarker(BlockPos pos) {
        loadMarker(pos, null);
    }

    public void removeMarker(BlockPos pos) {
        if (DEBUG_FULL) {
            BCLog.logger.info("[lib.marker.full] Removed a marker at " + pos);
        }
        tileCache.remove(pos);
        C connection = getConnection(pos);
        if (connection != null) {
            connection.removeMarker(pos);
            refreshConnection(connection);
        }
        if (isServer) {
            sendToDimension(new MessageMarker(false, false, cacheId, List.of(pos)));
        }
    }

    public ImmutableList<BlockPos> getAllMarkers() {
        return ImmutableList.copyOf(tileCache.keySet());
    }

    @Nullable
    public C getConnection(BlockPos pos) {
        return posToConnection.get(pos);
    }

    public void destroyConnection(@Nullable C connection) {
        if (connection == null) {
            return;
        }
        Set<BlockPos> set = connectionToPos.remove(connection);
        if (set != null) {
            deinitConnection(set);
        }

        if (DEBUG_FULL) {
            validateAllConnections();
        }
    }

    public void addConnection(@NotNull C connection) {
        Set<BlockPos> lastSeen = new HashSet<>(connection.getMarkerPositions());
        initConnection(connection, lastSeen);
        if (DEBUG_FULL) {
            validateAllConnections();
        }
    }

    public void refreshConnection(@NotNull C connection) {
        Set<BlockPos> lastSeen = connectionToPos.get(connection);
        if (DEBUG_FULL) {
            BCLog.logger.info("[lib.marker.full] Refreshing a connection");
            BCLog.logger.info("[lib.marker.full]    - Old = " + lastSeen);
            BCLog.logger.info("[lib.marker.full]    - New = " + connection.getMarkerPositions());
        }
        if (lastSeen == null) {
            // Why did you call this?
            addConnection(connection);
        } else {
            Set<BlockPos> invalid = new HashSet<>(lastSeen);
            lastSeen = new HashSet<>(connection.getMarkerPositions());
            invalid.removeAll(lastSeen);
            deinitConnection(invalid);
            initConnection(connection, lastSeen);
            if (lastSeen.isEmpty()) {
                connectionToPos.remove(connection);
            }
        }

        if (DEBUG_FULL) {
            validateAllConnections();
        }
    }

    private void validateAllConnections() {
        final String logStart = "[lib.marker.full][" + cacheId + "]";

        Set<C> visited = new HashSet<>();
        Set<BlockPos> visitedPos = new HashSet<>();

        for (Entry<C, Set<BlockPos>> entry : connectionToPos.entrySet()) {
            C con = entry.getKey();
            Set<BlockPos> positions = entry.getValue();
            Set<BlockPos> actual = new HashSet<>(con.getMarkerPositions());
            if (!positions.equals(actual)) {
                BCLog.logger.warn(logStart + " Positions differed!");
                List<BlockPos> total = new ArrayList<>();
                total.addAll(positions);
                total.addAll(actual);
                for (BlockPos p : total) {
                    String s = "(";
                    s += positions.contains(p) ? "R" : "_";
                    s += actual.contains(p) ? "S" : "_";
                    BCLog.logger.warn(logStart + "  - " + p + " " + s + ")");
                }
            }
            for (BlockPos p : positions) {
                if (visitedPos.contains(p)) {
                    BCLog.logger.warn(logStart + " Duplicate block positions!" + p + " - " + con);
                }
                visitedPos.add(p);
            }
            visited.add(con);
        }

        for (Entry<BlockPos, C> entry : posToConnection.entrySet()) {
            C connection = entry.getValue();
            BlockPos p = entry.getKey();
            if (!visited.contains(connection)) {
                BCLog.logger.warn(logStart + " Unknown connection " + connection + "(" + p + ")");
            }
            if (!visitedPos.contains(p)) {
                BCLog.logger.warn(logStart + " Unknown Position " + p + " (" + connection + ")");
            }
        }
    }

    private void deinitConnection(Set<BlockPos> set) {
        if (DEBUG_FULL) {
            BCLog.logger.info("[lib.marker.full] Tearing down all connections in " + set);
        }
        for (BlockPos p : set) {
            posToConnection.remove(p);
        }
        if (isServer && set.size() > 0) {
            sendToDimension(new MessageMarker(false, true, cacheId, new ArrayList<>(set)));
        }
    }

    private void initConnection(C connection, Set<BlockPos> lastSeen) {
        if (DEBUG_FULL) {
            BCLog.logger.info("[lib.marker.full] Setting up a connection with " + lastSeen);
        }
        if (lastSeen.size() < 2) {
            connectionToPos.remove(connection);
            for (BlockPos p : lastSeen) {
                posToConnection.remove(p);
            }
            return;
        }

        connectionToPos.put(connection, lastSeen);
        for (BlockPos p : lastSeen) {
            posToConnection.put(p, connection);
        }
        if (isServer && lastSeen.size() > 0) {
            sendToDimension(new MessageMarker(true, true, cacheId, new ArrayList<>(connection.getMarkerPositions())));
        }
    }

    public ImmutableList<C> getConnections() {
        return ImmutableList.copyOf(connectionToPos.keySet());
    }

    public abstract boolean tryConnect(BlockPos from, BlockPos to);

    /** Checks if {@link #tryConnect(BlockPos, BlockPos)} would succeed at this time. */
    public abstract boolean canConnect(BlockPos a, BlockPos b);

    public abstract ImmutableList<BlockPos> getValidConnections(BlockPos from);

    /** The laser drawn between two markers of this type that <em>could</em> be connected, while a player holds a
     * marker connector -- see {@code buildcraft.core.client.render.RenderMarkerConnections}. */
    public abstract LaserType getPossibleLaserType();

    public final void handleMessageMain(MessageMarker message) {
        if (handleMessage(message)) {
            return;
        }
        if (!message.connection()) {
            List<BlockPos> positions = message.positions();
            if (message.add()) {
                for (BlockPos p : positions) {
                    if (!hasLoadedOrUnloadedMarker(p)) {
                        loadMarker(p, null);
                    }
                }
            } else {
                for (BlockPos p : positions) {
                    removeMarker(p);
                }
            }
        }
    }

    protected abstract boolean handleMessage(MessageMarker message);
}
