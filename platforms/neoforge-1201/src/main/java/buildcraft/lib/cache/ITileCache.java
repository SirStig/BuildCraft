/*
 * Copyright (c) 2026 Joshua Kac -- NeoForge port (BuildCraft 10)
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL
 * was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.lib.cache;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.entity.BlockEntity;

public interface ITileCache {

    /** Call this from {@link BlockEntity#setRemoved()} to remove everything that has been cached. */
    void invalidate();

    @Nullable
    TileCacheRet getTile(BlockPos pos);

    @Nullable
    TileCacheRet getTile(Direction offset);

    enum TileCacheState {
        CACHED,
        NOT_CACHED,
        NOT_PRESENT;
    }
}
