/*
 * Copyright (c) 2016 SpaceToad and the BuildCraft team
 * Copyright (c) 2026 Joshua Kac -- NeoForge port (BuildCraft 10)
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL
 * was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.factory.tile;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

import buildcraft.BCFactoryRegistries;

public class TileAutoWorkbenchItems extends TileAutoWorkbenchBase {
    public TileAutoWorkbenchItems(BlockPos pos, BlockState state) {
        super(BCFactoryRegistries.AUTO_WORKBENCH_ITEMS_TYPE.get(), pos, state, 3, 3);
    }
}
