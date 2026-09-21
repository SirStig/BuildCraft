/*
 * Copyright (c) 2026 Joshua Kac -- NeoForge port (BuildCraft 10)
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL
 * was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.lib.compat;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkSource;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunk.EntityCreationType;

public enum DefaultBlockAccessor implements ISoftBlockAccessor {
    DIRECT(true),
    VIA_CHUNK(false);

    private final boolean direct;

    DefaultBlockAccessor(boolean direct) {
        this.direct = direct;
    }

    @Override
    @Nullable
    public BlockEntity getTile(Level level, BlockPos pos, boolean force) {
        if (direct | force) {
            if (force || level.isLoaded(pos)) {
                return level.getBlockEntity(pos);
            }
            return null;
        } else {
            LevelChunk chunk = getChunk(level, pos, false);
            if (chunk == null) {
                return null;
            }
            return chunk.getBlockEntity(pos, EntityCreationType.CHECK);
        }
    }

    @Override
    public BlockState getState(Level level, BlockPos pos, boolean force) {
        if (direct | force) {
            if (force || level.isLoaded(pos)) {
                return level.getBlockState(pos);
            }
            return Blocks.AIR.defaultBlockState();
        } else {
            LevelChunk chunk = getChunk(level, pos, false);
            if (chunk == null) {
                return Blocks.AIR.defaultBlockState();
            }
            return chunk.getBlockState(pos);
        }
    }

    /**
     * 1.12.2 routed this through {@code buildcraft.lib.misc.ChunkUtil}, which kept a per-thread "last chunk"
     * cache on top of {@code IChunkProvider#provideChunk}/{@code getLoadedChunk}. {@code ChunkUtil} has not been
     * ported yet (it lives in {@code buildcraft.lib.misc}, outside this pass), so this goes straight to
     * {@link ChunkSource#getChunkNow(int, int)} instead -- functionally identical, just without the cache. If
     * {@code ChunkUtil} is ported later this can go back through it.
     */
    @Nullable
    private static LevelChunk getChunk(Level level, BlockPos pos, boolean force) {
        int x = pos.getX() >> 4;
        int z = pos.getZ() >> 4;
        if (force) {
            return level.getChunk(x, z);
        }
        ChunkSource chunkSource = level.getChunkSource();
        return chunkSource.getChunkNow(x, z);
    }
}
