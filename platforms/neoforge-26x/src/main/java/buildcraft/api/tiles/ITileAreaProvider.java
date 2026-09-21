/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * The BuildCraft API is distributed under the terms of the MIT License. Please check the contents of the license, which
 * should be located as "LICENSE.API" in the BuildCraft source code distribution.
 */
package buildcraft.api.tiles;

import net.minecraft.core.BlockPos;

import buildcraft.api.core.IAreaProvider;

/** Used for more fine-grained control of whether or not a machine connects to the provider here. */
public interface ITileAreaProvider extends IAreaProvider {
    boolean isValidFromLocation(BlockPos pos);
}
