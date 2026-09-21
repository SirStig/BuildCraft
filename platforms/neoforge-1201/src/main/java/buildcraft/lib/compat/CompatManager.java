/*
 * Copyright (c) 2026 Joshua Kac -- NeoForge port (BuildCraft 10)
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL
 * was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.lib.compat;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import net.minecraftforge.fml.ModList;

public class CompatManager {
    public static final ISoftBlockAccessor blockAccessor;

    public static BlockEntity getTile(Level level, BlockPos pos, boolean force) {
        return blockAccessor.getTile(level, pos, force);
    }

    public static BlockState getState(Level level, BlockPos pos, boolean force) {
        return blockAccessor.getState(level, pos, force);
    }

    static {
        // Non-compile-dependent compat functions
        if (ModList.get().isLoaded("cubicchunks")) {
            // Our chunk-caching optimisation is basically useless with cubic chunks -
            // we should really replace this with one in the real compat module, later.
            blockAccessor = DefaultBlockAccessor.DIRECT;
        } else {
            blockAccessor = DefaultBlockAccessor.VIA_CHUNK;
        }
    }
}
