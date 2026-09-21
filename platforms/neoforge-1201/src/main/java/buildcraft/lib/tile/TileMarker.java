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
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

import buildcraft.api.tiles.IDebuggable;

import buildcraft.lib.marker.MarkerCache;
import buildcraft.lib.marker.MarkerConnection;
import buildcraft.lib.marker.MarkerSubCache;

/** Base block entity for every marker tile (volume corners, path waypoints, ...).
 *
 * <p>{@code TileEntity#onLoad}/{@code onChunkUnload}/{@code invalidate} are all gone from {@code BlockEntity}
 * (confirmed via {@code javap} and the real decompiled {@code LevelChunk}, checked against 26.x's copy of this
 * file): the lifecycle collapsed to {@link #clearRemoved()} (fired for both "just placed" and "chunk loaded from
 * disk") and {@link #setRemoved()} (fired for BOTH chunk unload AND genuine destruction -- 1.12.2 could tell
 * those apart at the tile level via separate {@code onChunkUnload}/{@code invalidate} hooks; modern code cannot,
 * from the block entity's generic lifecycle alone).
 *
 * <p>Unlike 26.x (see that copy of this file, which has a dedicated {@code BlockEntity#preRemoveSideEffects}
 * hook this target doesn't), this target still has {@code BlockBehaviour#onRemove(BlockState state, Level level,
 * BlockPos pos, BlockState newState, boolean movedByPiston)} (confirmed present via {@code javap}), which -- by
 * comparing {@code state.getBlock() != newState.getBlock()} -- can tell a genuine removal (the block itself
 * changed) apart from a chunk unload (which never touches the blockstate at all) before the generic block entity
 * removal machinery runs. That comparison needs the owning {@code Block}, not just this tile, so
 * {@link #removeFromMarkerCache()} is the contract a future {@code BlockMarkerBase} (follows this package, not
 * written in this pass -- see PORTING.md) needs to call from its own {@code onRemove} override, when that
 * comparison is true, <em>before</em> the vanilla removal machinery goes on to invoke {@link #setRemoved()}. */
public abstract class TileMarker<C extends MarkerConnection<C>> extends TileBC implements IDebuggable {

    /** Set once {@link #removeFromMarkerCache()} has run the genuine-removal cleanup, so the generic
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

    /** The genuine-removal path, as opposed to {@link #setRemoved()}'s chunk-unload-safe {@code unloadMarker}.
     * See the class javadoc: a future {@code BlockMarkerBase} must call this from its own {@code onRemove}
     * override before the vanilla removal machinery invokes {@link #setRemoved()}. */
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
