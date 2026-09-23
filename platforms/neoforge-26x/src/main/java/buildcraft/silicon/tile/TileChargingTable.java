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

/**
 * Ported as-is from 1.12.2's {@code TileChargingTable}: {@link #getTarget()} always returns 0, so this tile never
 * actually asks for or accepts laser power. 1.12.2 gated the whole block behind {@code BCLib.DEV} (never present in
 * a real 8.0.1 install) -- this port registers it unconditionally instead of rebuilding that dev-only gate, since
 * "compiles and does nothing" and "doesn't exist unless a flag is set" are the same outcome for a normal player.
 * See {@code buildcraft.silicon.block.BlockLaserTable}'s own javadoc for why it also opens no GUI.
 */
public class TileChargingTable extends TileLaserTableBase {
    public TileChargingTable(BlockPos pos, BlockState state) {
        super(BCSiliconRegistries.CHARGING_TABLE_TYPE.get(), pos, state);
    }

    @Override
    public long getTarget() {
        return 0;
    }
}
