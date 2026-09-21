/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * Copyright (c) 2026 Joshua Kac -- NeoForge port (BuildCraft 10)
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL
 * was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.lib.tile;

import java.util.List;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

import buildcraft.api.tiles.IDebuggable;

import buildcraft.lib.marker.MarkerCache;
import buildcraft.lib.marker.MarkerConnection;
import buildcraft.lib.marker.MarkerSubCache;

/** Base block entity for every marker tile (volume corners, path waypoints, ...).
 *
 * <p>{@code TileEntity#onLoad}/{@code onChunkUnload}/{@code invalidate} are all gone from {@link BlockEntity}
 * (confirmed via {@code javap} and the real decompiled {@code LevelChunk}): the lifecycle collapsed to
 * {@link #clearRemoved()} (fired for both "just placed" and "chunk loaded from disk") and {@link #setRemoved()}
 * (fired for BOTH chunk unload AND genuine destruction -- 1.12.2 could tell those apart at the tile level via
 * separate {@code onChunkUnload}/{@code invalidate} hooks; modern code cannot, from the block entity's generic
 * lifecycle alone).
 *
 * <p>On this target that distinction turns out <em>not</em> to need a cooperating {@code Block} at all, unlike
 * 1.20.1 (see that copy of this file): decompiling the real {@code LevelChunk#setBlockState} shows that
 * {@code Block#onRemove(state, level, pos, newState, movedByPiston)} no longer exists here (confirmed via
 * {@code javap} against {@code BlockBehaviour} -- it's genuinely gone, not renamed, and its replacement,
 * {@code affectNeighborsAfterRemoval}, drops the {@code newState} parameter entirely, so the old
 * "{@code state.getBlock() != newState.getBlock()}" trick can't be reproduced through it). Instead,
 * {@code LevelChunk#setBlockState} now calls a brand new hook directly on the block entity itself,
 * {@link BlockEntity#preRemoveSideEffects(BlockPos, BlockState)}, and -- critically -- only when the block at
 * that position actually changed (never on a chunk unload, which doesn't touch the blockstate at all). That is
 * exactly the "genuine removal" signal 1.12.2 needed a whole separate tile hook for, so {@link TileMarker}
 * handles it entirely by itself here: {@link #preRemoveSideEffects} does the real removal
 * ({@link #removeFromMarkerCache()}), and {@link #setRemoved()} (which still fires afterwards, for both cases)
 * skips the chunk-unload-style {@code unloadMarker} if that already ran, via {@link #genuinelyRemoved}.
 *
 * <p>A future {@code BlockMarkerBase} (follows this package, not written in this pass -- see PORTING.md) needs
 * nothing extra from this class on this target: the removal distinction is already handled here. */
public abstract class TileMarker<C extends MarkerConnection<C>> extends TileBC implements IDebuggable {

    /** Set once {@link #preRemoveSideEffects} has run the genuine-removal cleanup, so the generic
     * {@link #setRemoved()} (which fires afterwards regardless of whether this was a real removal or a chunk
     * unload) doesn't also treat it as a chunk unload. See the class javadoc. */
    private boolean genuinelyRemoved;

    protected TileMarker(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    public abstract MarkerCache<? extends MarkerSubCache<C>> getCache();

    public MarkerSubCache<C> getLocalCache() {
        return getCache().getSubCache(getLevel());
    }

    /** @return True if this has lasers being emitted, or any other reason you want. Activates the surrounding "glow"
     *         parts for the block model. */
    public abstract boolean isActiveForRender();

    public C getCurrentConnection() {
        return getLocalCache().getConnection(getBlockPos());
    }

    @Override
    public void clearRemoved() {
        super.clearRemoved();
        getLocalCache().loadMarker(getBlockPos(), this);
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        if (!genuinelyRemoved) {
            getLocalCache().unloadMarker(getBlockPos());
        }
    }

    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState oldState) {
        super.preRemoveSideEffects(pos, oldState);
        removeFromMarkerCache();
    }

    /** The genuine-removal path, as opposed to {@link #setRemoved()}'s chunk-unload-safe {@code unloadMarker}.
     * On this target {@link #preRemoveSideEffects} already calls this automatically -- see the class javadoc --
     * so nothing else needs to call it here. It stays public only for parity with the 1.20.1 copy of this
     * class, where a future {@code BlockMarkerBase} has to call it directly. */
    public void removeFromMarkerCache() {
        genuinelyRemoved = true;
        getLocalCache().removeMarker(getBlockPos());
    }

    protected void disconnectFromOthers() {
        C currentConnection = getCurrentConnection();
        if (currentConnection != null) {
            currentConnection.removeMarker(getBlockPos());
        }
    }

    @Override
    public void getDebugInfo(List<String> left, List<String> right, @Nullable Direction side) {
        C current = getCurrentConnection();
        MarkerSubCache<C> cache = getLocalCache();
        left.add("Exists = " + (cache.getMarker(getBlockPos()) == this));
        if (current == null) {
            left.add("Connection = null");
        } else {
            left.add("Connection:");
            current.getDebugInfo(getBlockPos(), left);
        }
    }
}
