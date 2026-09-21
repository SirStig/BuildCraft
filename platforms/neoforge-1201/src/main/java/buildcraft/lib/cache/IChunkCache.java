/*
 * Copyright (c) 2026 Joshua Kac -- NeoForge port (BuildCraft 10)
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL
 * was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.lib.cache;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.chunk.LevelChunk;

public interface IChunkCache {

    void invalidate();

    @Nullable
    LevelChunk getChunk(BlockPos pos);

    enum ChunkCacheState {
        CACHED,
        NOT_CACHED,
        LOADED,
        NOT_LOADED;
    }
}
