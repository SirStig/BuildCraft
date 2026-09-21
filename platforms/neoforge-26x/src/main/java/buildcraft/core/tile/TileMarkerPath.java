/*
 * Copyright (c) 2016 SpaceToad and the BuildCraft team
 * Copyright (c) 2026 Joshua Kac -- NeoForge port (BuildCraft 10)
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL
 * was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.core.tile;

import com.google.common.collect.ImmutableList;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import buildcraft.BCCoreRegistries;
import buildcraft.api.core.IPathProvider;

import buildcraft.lib.tile.TileMarker;

import buildcraft.core.marker.PathCache;
import buildcraft.core.marker.PathConnection;

/** One waypoint marker of a path. Unlike {@link TileMarkerVolume}, this tile never forms a connection through
 * its own block interaction ({@code BlockMarkerPath}'s use handler only reverses an existing path's direction) --
 * every path connection is made through {@code ItemMarkerConnector}'s generic marker-line interaction, which
 * already refreshes {@link buildcraft.api.properties.BuildCraftProperties#ACTIVE} for both markers it connects
 * (see that class' own javadoc), so this class needs no {@code refreshActiveState}-style helper of its own. */
public class TileMarkerPath extends TileMarker<PathConnection> implements IPathProvider {

    public TileMarkerPath(BlockPos pos, BlockState state) {
        super(BCCoreRegistries.MARKER_PATH_TYPE.get(), pos, state);
    }

    @Override
    public ImmutableList<BlockPos> getPath() {
        PathConnection connection = getCurrentConnection();
        if (connection == null) {
            return ImmutableList.of();
        }
        return connection.getMarkerPositions();
    }

    @Override
    public void removeFromWorld() {
        if (level == null) {
            return;
        }
        for (BlockPos pos : getPath()) {
            level.destroyBlock(pos, true, null, Block.UPDATE_LIMIT);
        }
    }

    @Override
    public PathCache getCache() {
        return PathCache.INSTANCE;
    }

    @Override
    public boolean isActiveForRender() {
        PathConnection connection = getCurrentConnection();
        return connection != null;
    }

    public void reverseDirection() {
        if (level == null || level.isClientSide()) {
            return;
        }
        PathConnection connection = getCurrentConnection();
        if (connection == null) {
            return;
        }
        connection.reverseDirection();
    }
}
