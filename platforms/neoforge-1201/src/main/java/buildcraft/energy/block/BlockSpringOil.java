/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * Copyright (c) 2026 Joshua Kac -- NeoForge port (BuildCraft 10)
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.energy.block;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;

import buildcraft.api.enums.EnumSpring;

import buildcraft.lib.block.BlockBCTile;

import buildcraft.energy.tile.TileSpringOil;

/**
 * The oil half of 1.12.2's single metadata-subtyped {@code BlockSpring} -- see the 26.x copy of this class for
 * the full account of why oil needed its own block/tile design, unlike {@link BlockSpringWater}.
 */
public class BlockSpringOil extends BlockBCTile {

    public BlockSpringOil(BlockBehaviour.Properties properties) {
        super(properties);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new TileSpringOil(pos, state);
    }

    @Override
    public void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        super.onPlace(state, level, pos, oldState, movedByPiston);
        level.scheduleTick(pos, this, EnumSpring.OIL.tickRate);
    }

    @Override
    public void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        level.scheduleTick(pos, this, EnumSpring.OIL.tickRate);
        if (!EnumSpring.OIL.canGen || EnumSpring.OIL.liquidBlock == null) {
            return;
        }
        if (!level.getBlockState(pos.above()).isAir()) {
            return;
        }
        if (EnumSpring.OIL.chance != -1 && random.nextInt(EnumSpring.OIL.chance) != 0) {
            return;
        }
        level.setBlock(pos.above(), EnumSpring.OIL.liquidBlock, 3);
    }
}
