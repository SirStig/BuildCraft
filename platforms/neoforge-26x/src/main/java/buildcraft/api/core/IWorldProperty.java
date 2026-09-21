/*
 * Copyright (c) 2011-2015, SpaceToad and the BuildCraft Team http://www.mod-buildcraft.com
 *
 * The BuildCraft API is distributed under the terms of the MIT License. Please check the contents of the license, which
 * should be located as "LICENSE.API" in the BuildCraft source code distribution.
 */
package buildcraft.api.core;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

/** A cached yes/no question about a block position, such as "is this block soft?". */
public interface IWorldProperty {
    boolean get(Level level, BlockPos pos);

    void clear();
}
