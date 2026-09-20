/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.core.block;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

import buildcraft.core.tile.TilePowerConsumerTester;
import buildcraft.lib.block.BlockBCTile;

/** Ported from 1.12.2. The block itself is unchanged; it now supplies its block entity and ticker through
 * {@code EntityBlock} instead of the removed {@code hasTileEntity}/{@code createTileEntity} pair. */
public class BlockPowerConsumerTester extends BlockBCTile {

    public BlockPowerConsumerTester(Properties properties) {
        super(properties);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new TilePowerConsumerTester(pos, state);
    }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide()) {
            return null;
        }
        return (tickLevel, pos, tickState, be) -> {
            if (be instanceof TilePowerConsumerTester tester) {
                tester.serverTick();
            }
        };
    }
}
