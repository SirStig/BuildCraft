/*
 * Copyright (c) 2026 Joshua Kac -- NeoForge port (BuildCraft 10)
 *
 * The BuildCraft API is distributed under the terms of the MIT License. Please check the contents of the
 * license, which should be located as "LICENSE.API" in the BuildCraft source code distribution.
 */
package buildcraft.api.blocks;

import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.core.Direction;

/** Rotates a block when a wrench is used on it. Either implement this on a block, or register one with
 * {@link CustomRotationHelper}. */
public interface ICustomRotationHandler {
    InteractionResult attemptRotation(Level level, BlockPos pos, BlockState state, Direction sideWrenched);
}
