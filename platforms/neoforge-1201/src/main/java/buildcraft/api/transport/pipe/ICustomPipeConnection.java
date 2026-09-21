/*
 * Copyright (c) 2026 Joshua Kac -- NeoForge port (BuildCraft 10)
 *
 * The BuildCraft API is distributed under the terms of the MIT License. Please check the contents of the
 * license, which should be located as "LICENSE.API" in the BuildCraft source code distribution.
 */
package buildcraft.api.transport.pipe;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/** Lets a block tell pipes how far to extend towards it. */
public interface ICustomPipeConnection {
    /**
     * @return How far the connecting pipe should extend, in addition to its normal 4/16f connection. Values
     *         less than or equal to {@code -4 / 16.0f} mean the pipe will not connect at all, and will render
     *         as if it were not connected.
     */
    float getExtension(Level level, BlockPos pos, Direction face, BlockState state);
}
