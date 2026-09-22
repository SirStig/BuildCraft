/*
 * Copyright (c) 2016 SpaceToad and the BuildCraft team
 * Copyright (c) 2026 Joshua Kac -- NeoForge port (BuildCraft 10)
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL
 * was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.core.marker;

import java.util.List;

import com.google.common.collect.ImmutableList;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;

import buildcraft.lib.client.render.laser.LaserData_BC8.LaserType;
import buildcraft.lib.marker.MarkerCache;
import buildcraft.lib.marker.MarkerSubCache;
import buildcraft.lib.net.MessageMarker;

import buildcraft.core.client.BuildCraftLaserManager;

/** Per-dimension state for path markers.
 *
 * <p>{@code World.getPerWorldStorage().getOrLoadData}/{@code .setData} (1.12.2's per-world save API) doesn't
 * exist on this target: {@link Level#getDataStorage()} isn't declared on the generic {@link Level} at all here
 * (confirmed via {@code javap} -- only {@link ServerLevel} has it, returning a {@code SavedDataStorage}, not the
 * {@code DimensionDataStorage} 1.20.1 keeps). A <em>client</em> {@link Level} therefore has no on-disk storage to
 * load from -- there never was a real save file to read there anyway; 1.12.2's disk load was always primarily a
 * server-side concern, with the client kept in sync by {@link MessageMarker} instead (see {@code MarkerSubCache}'s
 * own class javadoc). So this constructor loads from {@link PathSavedData#TYPE} only when {@code level} is
 * genuinely a {@link ServerLevel}, and relies on the network sync for the client, exactly as 1.12.2 effectively
 * already did.
 *
 * <p>{@link #MARKER_MAX_DISTANCE} stands in for 1.12.2's {@code BCCoreConfig.markerMaxDistance}, which needs the
 * old Forge {@code Property}/{@code Configuration} API redesigned before it can be ported (see PORTING.md's
 * deferred-config entry) -- {@code 64} is that config's own 1.12.2 default
 * ({@code config.get(general, "markerMaxDistance", 64)}), not a new value chosen for this port.
 * {@code BlockPos.distanceSq} is {@link BlockPos#distSqr(net.minecraft.core.Vec3i)} now.
 * {@link MessageMarker}'s 1.12.2 mutable fields are this record's accessors now -- see that class' own javadoc.
 *
 * <p>{@link #getPossibleLaserType()} is back (see {@link MarkerSubCache}'s javadoc), returning the same
 * {@code BuildCraftLaserManager} constant 1.12.2 did. */
public class PathSubCache extends MarkerSubCache<PathConnection> {
    private static final int MARKER_MAX_DISTANCE = 64;

    public PathSubCache(Level level) {
        super(level, MarkerCache.CACHES.indexOf(PathCache.INSTANCE));
        if (level instanceof ServerLevel serverLevel) {
            PathSavedData data = serverLevel.getDataStorage().computeIfAbsent(PathSavedData.TYPE);
            data.loadInto(this);
        }
    }

    @Override
    public boolean tryConnect(BlockPos from, BlockPos to) {
        PathConnection conFrom = getConnection(from);
        PathConnection conTo = getConnection(to);
        if (conFrom == null) {
            if (conTo == null) {
                return PathConnection.tryCreateConnection(this, from, to);
            } else {
                return conTo.addMarker(from, to);
            }
        } else {
            if (conTo == null) {
                return conFrom.addMarker(from, to);
            } else {
                return conFrom.mergeWith(conTo, from, to);
            }
        }
    }

    @Override
    public boolean canConnect(BlockPos from, BlockPos to) {
        PathConnection conFrom = getConnection(from);
        PathConnection conTo = getConnection(to);
        if (conFrom == null) {
            if (conTo == null) {
                return true;
            } else {
                return conTo.canAddMarker(from, to);
            }
        } else {
            if (conTo == null) {
                return conFrom.canAddMarker(from, to);
            } else {
                return conFrom.canMergeWith(conTo, from, to);
            }
        }
    }

    @Override
    public ImmutableList<BlockPos> getValidConnections(BlockPos from) {
        ImmutableList.Builder<BlockPos> list = ImmutableList.builder();
        final int maxLengthSquared = MARKER_MAX_DISTANCE * MARKER_MAX_DISTANCE;
        for (BlockPos pos : getAllMarkers()) {
            if (pos.equals(from)) {
                continue;
            }
            if (pos.distSqr(from) > maxLengthSquared) {
                continue;
            }
            if (canConnect(from, pos) || canConnect(pos, from)) {
                list.add(pos);
            }
        }
        return list.build();
    }

    @Override
    public LaserType getPossibleLaserType() {
        return BuildCraftLaserManager.MARKER_PATH_POSSIBLE;
    }

    @Override
    protected boolean handleMessage(MessageMarker message) {
        List<BlockPos> positions = message.positions();
        if (message.connection()) {
            if (message.add()) {
                for (BlockPos p : positions) {
                    PathConnection existing = this.getConnection(p);
                    destroyConnection(existing);
                }
                PathConnection con = new PathConnection(this, positions);
                addConnection(con);
            } else { // removing from a connection
                for (BlockPos p : positions) {
                    PathConnection existing = this.getConnection(p);
                    if (existing != null) {
                        existing.removeMarker(p);
                        refreshConnection(existing);
                    }
                }
            }
        }
        return false;
    }
}
