/*
 * Copyright (c) 2016 SpaceToad and the BuildCraft team
 * Copyright (c) 2026 Joshua Kac -- NeoForge port (BuildCraft 10)
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL
 * was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.core.marker;

import java.util.Collection;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Set;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Axis;

import buildcraft.lib.marker.MarkerConnection;
import buildcraft.lib.misc.PositionUtil;
import buildcraft.lib.misc.data.Box;

/** One connected volume box: a group of markers making up the corners/edges of a single {@link Box}.
 *
 * <p>{@code EnumFacing}/{@code EnumFacing.Axis} are {@link Direction}/{@link Direction.Axis} now, and
 * {@code BlockPos.offset(EnumFacing, int)} is {@link BlockPos#relative(Direction, int)}.
 *
 * <p>{@link #MARKER_MAX_DISTANCE} stands in for 1.12.2's {@code BCCoreConfig.markerMaxDistance}, which needs the
 * old Forge {@code Property}/{@code Configuration} API redesigned before it can be ported (see PORTING.md's
 * deferred-config entry) -- {@code 64} is that config's own 1.12.2 default
 * ({@code config.get(general, "markerMaxDistance", 64)}), not a new value chosen for this port.
 * {@link VolumeSubCache#getValidConnections(BlockPos)} and the client-side signal-laser renderer
 * ({@code buildcraft.core.client.render.RenderMarkerVolume}, which draws exactly this far) both need the same
 * constant, so it is public rather than private.
 *
 * <p>{@link #renderInWorld()} stays an empty override: this connection's box lasers are drawn by
 * {@code buildcraft.core.client.render.RenderMarkerConnections}, which reads {@link #getBox()} from the client-side
 * cache during level render-state extraction. A per-connection "render yourself now" call has no modern equivalent
 * (on 26.x there is no "now": geometry is extracted first and submitted later), so the drawing lives in that one
 * client-only class instead, keeping this common class free of client types. See
 * {@link MarkerConnection#renderInWorld()}'s own class javadoc for why this method exists as a plain (not
 * {@code @SideOnly}) override at all. */
public class VolumeConnection extends MarkerConnection<VolumeConnection> {
    public static final int MARKER_MAX_DISTANCE = 64;

    private final Set<BlockPos> makeup = new HashSet<>();
    private final Box box = new Box();

    public static boolean tryCreateConnection(VolumeSubCache subCache, BlockPos from, BlockPos to) {
        if (canCreateConnection(subCache, from, to)) {
            VolumeConnection connection = new VolumeConnection(subCache);
            connection.makeup.add(from);
            connection.makeup.add(to);
            connection.createBox();
            subCache.addConnection(connection);
            return true;
        }
        return false;
    }

    public static boolean canCreateConnection(VolumeSubCache subCache, BlockPos from, BlockPos to) {
        Direction directOffset = PositionUtil.getDirectFacingOffset(from, to);
        if (directOffset == null) return false;
        for (int i = 1; i <= MARKER_MAX_DISTANCE; i++) {
            BlockPos offset = from.relative(directOffset, i);
            if (offset.equals(to)) return true;
            if (subCache.hasLoadedOrUnloadedMarker(offset)) return false;
        }
        return false;
    }

    public VolumeConnection(VolumeSubCache subCache) {
        super(subCache);
    }

    public VolumeConnection(VolumeSubCache subCache, Collection<BlockPos> positions) {
        super(subCache);
        makeup.addAll(positions);
        createBox();
    }

    @Override
    public void removeMarker(BlockPos pos) {
        makeup.remove(pos);
        if (makeup.size() < 2) {
            // This connection will be removed by the sub-cache
            makeup.clear();
        }
        createBox();
    }

    public boolean addMarker(BlockPos pos) {
        if (canAddMarker(pos)) {
            makeup.add(pos);
            createBox();
            subCache.refreshConnection(this);
            return true;
        }
        return false;
    }

    public boolean canAddMarker(BlockPos to) {
        Set<Axis> taken = getConnectedAxis();
        for (BlockPos from : makeup) {
            Direction direct = PositionUtil.getDirectFacingOffset(from, to);
            if (direct != null && !taken.contains(direct.getAxis())) {
                return true;
            }
        }
        return !makeup.contains(to) && box.isCorner(to);
    }

    public boolean mergeWith(VolumeConnection other) {
        if (canMergeWith(other)) {
            makeup.addAll(other.makeup);
            other.makeup.clear();
            createBox();
            subCache.refreshConnection(other);
            subCache.refreshConnection(this);
            return true;
        }
        return false;
    }

    public boolean canMergeWith(VolumeConnection other) {
        EnumSet<Axis> us = getConnectedAxis();
        EnumSet<Axis> them = other.getConnectedAxis();
        if (us.size() != 1 || them.size() != 1) {
            return false;
        }
        if (us.equals(them)) {
            return false;
        }
        Set<Axis> blacklisted = EnumSet.copyOf(us);
        blacklisted.addAll(them);
        for (BlockPos from : makeup) {
            for (BlockPos to : other.makeup) {
                Direction offset = PositionUtil.getDirectFacingOffset(from, to);
                if (offset != null && !blacklisted.contains(offset.getAxis())) {
                    return true;
                }
            }
        }
        return false;
    }

    public EnumSet<Axis> getConnectedAxis() {
        EnumSet<Axis> taken = EnumSet.noneOf(Direction.Axis.class);
        for (BlockPos a : getMarkerPositions()) {
            for (BlockPos b : getMarkerPositions()) {
                Direction offset = PositionUtil.getDirectFacingOffset(a, b);
                if (offset != null) {
                    taken.add(offset.getAxis());
                }
            }
        }
        return taken;
    }

    @Override
    public Collection<BlockPos> getMarkerPositions() {
        return makeup;
    }

    private void createBox() {
        box.reset();
        for (BlockPos p : makeup) {
            box.extendToEncompass(p);
        }
    }

    public Box getBox() {
        return new Box(box.min(), box.max());
    }

    // ###########
    //
    // Rendering
    //
    // ###########

    @Override
    public void renderInWorld() {
        // Drawn by buildcraft.core.client.render.RenderMarkerConnections -- see the class javadoc.
    }
}
