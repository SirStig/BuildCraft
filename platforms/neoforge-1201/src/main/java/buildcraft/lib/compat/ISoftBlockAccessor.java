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
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public interface ISoftBlockAccessor {
    /** @param force If true then the chunk containing the tile will be loaded from disk, false if this should only get
     *            the tile entity if it is currently loaded */
    @Nullable
    BlockEntity getTile(Level level, BlockPos pos, boolean force);

    /** @param force If true then the chunk containing the tile will be loaded from disk, false if this should only get
     *            the tile entity if it is currently loaded */
    BlockState getState(Level level, BlockPos pos, boolean force);
}
