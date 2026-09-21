/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * Copyright (c) 2026 Joshua Kac -- NeoForge port (BuildCraft 10)
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL
 * was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.lib.misc;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.LevelChunk;

/** {@code IChunkProvider#provideChunk}/{@code #getLoadedChunk} are gone; {@code Level#getChunk(x, z)} force-loads
 * (via {@code ChunkStatus.FULL}) and {@code ChunkSource#getChunkNow(x, z)} is the non-forcing lookup. {@code
 * Chunk#isLoaded()} is also gone -- there is no longer a flag on the chunk object itself that flips when it
 * unloads, so the cache-validity check here uses {@link Level#hasChunk(int, int)} against the *requested*
 * coordinates instead, which is the direct modern equivalent for "is this position's chunk currently loaded".
 * {@link ChunkPos}'s {@code x}/{@code z} fields are private on this target, read through the {@code x()}/{@code
 * z()} accessors instead -- the 1.20.1 copy of this class still has the public fields. */
public class ChunkUtil {
    private static final ThreadLocal<LevelChunk> lastChunk = new ThreadLocal<>();

    public static LevelChunk getChunk(Level world, BlockPos pos, boolean force) {
        return getChunk(world, pos.getX() >> 4, pos.getZ() >> 4, force);
    }

    public static LevelChunk getChunk(Level world, ChunkPos pos, boolean force) {
        return getChunk(world, pos.x(), pos.z(), force);
    }

    public static LevelChunk getChunk(Level world, int x, int z, boolean force) {
        LevelChunk chunk = lastChunk.get();

        if (chunk != null) {
            if (world.hasChunk(x, z)) {
                if (chunk.getLevel() == world && chunk.getPos().x() == x && chunk.getPos().z() == z) {
                    return chunk;
                }
            } else {
                lastChunk.set(null);
            }
        }

        if (force) {
            chunk = world.getChunk(x, z);
        } else {
            chunk = world.getChunkSource().getChunkNow(x, z);
        }

        if (chunk != null) {
            lastChunk.set(chunk);
        }
        return chunk;
    }
}
