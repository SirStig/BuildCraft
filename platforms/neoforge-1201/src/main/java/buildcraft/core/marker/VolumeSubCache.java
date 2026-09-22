/*
 * Copyright (c) 2016 SpaceToad and the BuildCraft team
 * Copyright (c) 2026 Joshua Kac -- NeoForge port (BuildCraft 10)
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL
 * was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.core.marker;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import com.google.common.collect.ImmutableList;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Axis;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;

import buildcraft.lib.client.render.laser.LaserData_BC8.LaserType;
import buildcraft.lib.marker.MarkerCache;
import buildcraft.lib.marker.MarkerSubCache;
import buildcraft.lib.net.MessageMarker;

import buildcraft.core.client.BuildCraftLaserManager;

/** Per-dimension state for volume markers.
 *
 * <p>{@code World.getPerWorldStorage().getOrLoadData}/{@code .setData} (1.12.2's per-world save API) doesn't
 * exist on this target either: {@link Level#getDataStorage()} is declared on neither {@link Level} nor
 * {@link net.minecraft.client.multiplayer.ClientLevel} (confirmed via {@code javap} against both), only on
 * {@link ServerLevel}, which returns a {@code DimensionDataStorage}. A <em>client</em> {@link Level} therefore has
 * no on-disk storage to load from here either -- the same situation as 26.x, not the split this class' 26.x copy's
 * javadoc once assumed before checking; there never was a real save file to read on the client anyway, and 1.12.2's
 * disk load was always primarily a server-side concern, with the client kept in sync by {@link MessageMarker}
 * instead (see {@code MarkerSubCache}'s own class javadoc). So this constructor loads from
 * {@link VolumeSavedData#LOADER} only when {@code level} is genuinely a {@link ServerLevel}, and relies on the
 * network sync for the client, exactly as 1.12.2 effectively already did.
 *
 * <p>{@code EnumFacing}/{@code EnumFacing.VALUES}/{@code Axis} are {@link Direction}/{@link Direction#values()}/
 * {@link Direction.Axis} now, and {@code BlockPos.offset(EnumFacing, int)} is
 * {@link BlockPos#relative(Direction, int)}. Unlike its 26.x copy, {@link MessageMarker} here is still a plain
 * class with public fields ({@code message.positions}, not {@code message.positions()}) -- see that class' own
 * javadoc.
 *
 * <p>{@link #getPossibleLaserType()} is back (see {@link MarkerSubCache}'s javadoc), returning the same
 * {@code BuildCraftLaserManager} constant 1.12.2 did. */
public class VolumeSubCache extends MarkerSubCache<VolumeConnection> {
    public VolumeSubCache(Level level) {
        super(level, MarkerCache.CACHES.indexOf(VolumeCache.INSTANCE));
        if (level instanceof ServerLevel serverLevel) {
            VolumeSavedData data = serverLevel.getDataStorage()
                .computeIfAbsent(VolumeSavedData.LOADER, VolumeSavedData::new, VolumeSavedData.NAME);
            data.loadInto(this);
        }
    }

    @Override
    public boolean tryConnect(BlockPos from, BlockPos to) {
        VolumeConnection fromConnection = getConnection(from);
        VolumeConnection toConnection = getConnection(to);
        if (fromConnection == null) {
            if (toConnection == null) {
                return VolumeConnection.tryCreateConnection(this, from, to);
            } else {// The other one has a connection
                return toConnection.addMarker(from);
            }
        } else {// We have a connection
            if (toConnection == null) {
                return fromConnection.addMarker(to);
            } else {// The other one has a connection
                return fromConnection.mergeWith(toConnection);
            }
        }
    }

    @Override
    public boolean canConnect(BlockPos from, BlockPos to) {
        VolumeConnection fromConnection = getConnection(from);
        VolumeConnection toConnection = getConnection(to);
        if (fromConnection == null) {
            if (toConnection == null) {
                return VolumeConnection.canCreateConnection(this, from, to);
            } else {// The other one has a connection
                return toConnection.canAddMarker(from);
            }
        } else {// We have a connection
            if (toConnection == null) {
                return fromConnection.canAddMarker(to);
            } else {// The other one has a connection
                return fromConnection.canMergeWith(toConnection);
            }
        }
    }

    @Override
    public ImmutableList<BlockPos> getValidConnections(BlockPos from) {
        VolumeConnection existing = getConnection(from);
        Set<Axis> taken = EnumSet.noneOf(Direction.Axis.class);
        if (existing != null) {
            taken.addAll(existing.getConnectedAxis());
        }

        ImmutableList.Builder<BlockPos> valids = ImmutableList.builder();
        for (Direction face : Direction.values()) {
            if (taken.contains(face.getAxis())) continue;
            for (int i = 1; i <= VolumeConnection.MARKER_MAX_DISTANCE; i++) {
                BlockPos toTry = from.relative(face, i);
                if (hasLoadedOrUnloadedMarker(toTry)) {
                    if (!canConnect(from, toTry)) break;
                    valids.add(toTry);
                    break;
                }
            }
        }
        return valids.build();
    }

    @Override
    public LaserType getPossibleLaserType() {
        return BuildCraftLaserManager.MARKER_VOLUME_POSSIBLE;
    }

    @Override
    protected boolean handleMessage(MessageMarker message) {
        List<BlockPos> positions = message.positions;
        if (message.connection) {
            if (message.add) {
                for (BlockPos p : positions) {
                    VolumeConnection existing = this.getConnection(p);
                    destroyConnection(existing);
                }
                VolumeConnection con = new VolumeConnection(this, positions);
                addConnection(con);
            } else { // removing from a connection
                for (BlockPos p : positions) {
                    VolumeConnection existing = this.getConnection(p);
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
