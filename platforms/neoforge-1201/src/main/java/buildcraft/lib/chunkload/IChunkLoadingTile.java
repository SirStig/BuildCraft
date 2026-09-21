/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * Copyright (c) 2026 Joshua Kac -- NeoForge port (BuildCraft 10)
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL
 * was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.lib.chunkload;

import java.util.HashSet;
import java.util.Set;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.entity.BlockEntity;

import buildcraft.lib.BCLibConfig;
import buildcraft.lib.BCLibConfig.ChunkLoaderLevel;

/**
 * Implemented by block entities that wish to be chunkloaded by BuildCraft lib.
 *
 * <p>The interface itself is unaffected by the port. What actually forces the chunks, {@code ChunkLoaderManager},
 * has not been ported on either target: Forge's ticket API was redesigned into a
 * {@code TicketHelper}/{@code LoadingValidationCallback} pair before 1.20.1 already (this target's
 * {@code ForgeChunkManager} is already the new shape, not 1.12.2's {@code requestTicket}/{@code Ticket}), and
 * 26.x redesigned it again into a registered {@code TicketController}. It needs rewriting on both targets, not
 * porting on one and renaming on the other. It follows once a machine (the quarry, the pump) actually needs it.
 */
public interface IChunkLoadingTile {
    /** @return The chunkloading type, or null if this tile doesn't want to be chunkloaded. */
    @Nullable
    default LoadType getLoadType() {
        return LoadType.SOFT;
    }

    /**
     * Gets a list of all the ADDITIONAL chunks to load.
     *
     * <p>The default implementation returns neighbouring chunks if this block is on a chunk boundary.
     *
     * @return A set of all the additional chunks to load, optionally including the {@link ChunkPos} that this
     *         tile is contained within. If the return value is null then only the chunk containing this block
     *         will be chunkloaded.
     */
    @Nullable
    default Set<ChunkPos> getChunksToLoad() {
        BlockPos pos = ((BlockEntity) this).getBlockPos();
        Set<ChunkPos> chunkPoses = new HashSet<>(4);
        for (Direction face : Direction.Plane.HORIZONTAL) {
            chunkPoses.add(new ChunkPos(pos.relative(face)));
        }
        return chunkPoses;
    }

    enum LoadType {
        /**
         * Softly attempt to chunkload this. If {@link BCLibConfig#chunkLoadingType} is
         * {@link ChunkLoaderLevel#STRICT_TILES} or {@link ChunkLoaderLevel#NONE} then it won't be loaded.
         */
        SOFT,
        /**
         * If {@link BCLibConfig#chunkLoadingType} is {@link ChunkLoaderLevel#NONE} then it won't be loaded.
         * Generally this should only be enabled for machines designed to operate far from a player's
         * territory, like a quarry or a pump.
         */
        HARD
    }
}
