/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * Copyright (c) 2026 Joshua Kac -- NeoForge port (BuildCraft 10)
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL
 * was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.lib.misc.data;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

/**
 * A block position qualified by which level it is in.
 *
 * <p>The dimension was an {@code int} in 1.12.2. Numeric dimension ids were removed in 1.16 -- dimensions
 * are registry entries now -- so this holds a {@link ResourceKey} instead. That is also why there is no
 * longer a constructor taking a bare id: there is nothing sensible to build a key from without a registry.
 */
public final class WorldPos {
    @SuppressWarnings("WeakerAccess")
    public final ResourceKey<Level> dimension;
    public final BlockPos pos;

    @SuppressWarnings("WeakerAccess")
    public WorldPos(ResourceKey<Level> dimension, BlockPos pos) {
        this.dimension = dimension;
        // BlockPos has no copy constructor now; immutable() returns this when it already is one.
        this.pos = pos.immutable();
    }

    public WorldPos(Level level, BlockPos pos) {
        this(level.dimension(), pos);
    }

    public WorldPos(BlockEntity tile) {
        this(tile.getLevel(), tile.getBlockPos());
    }

    @Override
    public boolean equals(Object o) {
        return this == o ||
            o != null &&
                getClass() == o.getClass() &&
                dimension.equals(((WorldPos) o).dimension) &&
                pos.equals(((WorldPos) o).pos);

    }

    @Override
    public int hashCode() {
        return 31 * dimension.hashCode() + pos.hashCode();
    }
}
