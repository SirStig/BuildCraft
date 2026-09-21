/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * Copyright (c) 2026 Joshua Kac -- NeoForge port (BuildCraft 10)
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL
 * was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.core.block;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 1.12.2's {@code BlockSpring} used a single block with a two-value {@code EnumSpring} blockstate property
 * (water, oil) -- block metadata subtypes are gone on this target too, so each spring type becomes its own
 * {@link Block}. This is the water half; see the 26.x copy of this class (and PORTING.md's survey of
 * {@code buildcraft.core}'s remaining files) for why the oil half needs its own design first.
 *
 * <p>{@code setBlockUnbreakable()} + {@code setResistance(6000000.0F)} is {@code strength(-1.0F, 6000000.0F)}.
 * {@code World#scheduleUpdate(pos, block, delay)} is {@code Level#scheduleTick(pos, block, delay)}
 * (a default method on {@code LevelAccessor}), and the scheduled callback itself is {@link #tick} -- unchanged
 * in shape from 26.x here, since 1.20.1 already split scheduled and random ticks into separate methods (that
 * split predates both targets).
 */
public class BlockSpringWater extends Block {
    public BlockSpringWater(Properties properties) {
        super(properties);
    }

    @Override
    public void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        super.onPlace(state, level, pos, oldState, movedByPiston);
        level.scheduleTick(pos, this, 5);
    }

    @Override
    public void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        level.scheduleTick(pos, this, 5);
        if (!level.getBlockState(pos.above()).isAir()) {
            return;
        }
        level.setBlock(pos.above(), Blocks.WATER.defaultBlockState(), 3);
    }
}
