/*
 * Copyright (c) 2026 Joshua Kac -- NeoForge port (BuildCraft 10)
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL
 * was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.lib.cache;

import java.lang.ref.WeakReference;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;

import buildcraft.lib.misc.ChunkUtil;

/** {@code Chunk#isLoaded()} doesn't exist any more (see {@link ChunkUtil}'s own class javadoc) -- validity is
 * checked against {@link Level#hasChunk(int, int)} for the cached chunk's own position instead. */
public class CachedChunk implements IChunkCache {

    private final BlockEntity tile;
    private WeakReference<LevelChunk> cachedChunk;

    public CachedChunk(BlockEntity tile) {
        this.tile = tile;
    }

    @Override
    public void invalidate() {
        cachedChunk = null;
    }

    @Override
    public LevelChunk getChunk(BlockPos pos) {
        if (tile.isRemoved()) {
            cachedChunk = null;
            return null;
        }
        BlockPos tPos = tile.getBlockPos();
        if (pos.getX() >> 4 != tPos.getX() >> 4 //
            || pos.getZ() >> 4 != tPos.getZ() >> 4) {
            return null;
        }
        Level world = tile.getLevel();
        if (world == null) {
            cachedChunk = null;
            return null;
        }
        if (cachedChunk != null) {
            LevelChunk c = cachedChunk.get();
            if (c != null && world.hasChunk(c.getPos().x, c.getPos().z)) {
                return c;
            }
            cachedChunk = null;
        }
        LevelChunk chunk = ChunkUtil.getChunk(world, pos, true);
        if (chunk != null && chunk.getLevel() == world) {
            cachedChunk = new WeakReference<>(chunk);
            return chunk;
        }
        return null;
    }
}
