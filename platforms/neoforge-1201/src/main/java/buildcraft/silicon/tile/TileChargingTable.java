/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * Copyright (c) 2026 Joshua Kac -- NeoForge port (BuildCraft 10)
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL
 * was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.silicon.tile;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

import buildcraft.BCSiliconRegistries;

/** Mirrors the 26.x copy of this class -- see that one's own javadoc for why this is registered unconditionally
 * as an inert stub rather than gated behind a dev-only flag. */
public class TileChargingTable extends TileLaserTableBase {
    public TileChargingTable(BlockPos pos, BlockState state) {
        super(BCSiliconRegistries.CHARGING_TABLE_TYPE.get(), pos, state);
    }

    @Override
    public long getTarget() {
        return 0;
    }
}
