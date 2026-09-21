/*
 * Copyright (c) 2026 Joshua Kac -- NeoForge port (BuildCraft 10)
 *
 * The BuildCraft API is distributed under the terms of the MIT License. Please check the contents of the
 * license, which should be located as "LICENSE.API" in the BuildCraft source code distribution.
 */
package buildcraft.api.transport;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

/** Handles a stripes pipe breaking a block in front of it. */
public interface IStripesHandlerBlock {

    /**
     * @return True if this broke a block, false otherwise. Note that a handler MUST NOT return false if it has
     *         changed the world in any way.
     */
    boolean handle(Level level, BlockPos pos, Direction direction, Player player, IStripesActivator activator);
}
