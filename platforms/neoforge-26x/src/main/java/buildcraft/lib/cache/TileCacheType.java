/*
 * Copyright (c) 2026 Joshua Kac -- NeoForge port (BuildCraft 10)
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL
 * was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.lib.cache;

import java.util.function.Function;

import net.minecraft.world.level.block.entity.BlockEntity;

public enum TileCacheType {
    NO_CACHE(tile -> NoopTileCache.INSTANCE),
    NEIGHBOUR_CACHE(NeighbourTileCache::new);

    private final Function<BlockEntity, ITileCache> constructor;

    TileCacheType(Function<BlockEntity, ITileCache> constructor) {
        this.constructor = constructor;
    }

    public ITileCache create(BlockEntity tile) {
        return constructor.apply(tile);
    }
}
