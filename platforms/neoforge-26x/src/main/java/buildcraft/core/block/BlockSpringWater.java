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
 * (water, oil) -- block metadata subtypes are gone (PORTING.md's structural-changes list, item 1), so each
 * spring type becomes its own {@link Block}, the same way {@code DyedBlockVariants} splits a colour-indexed
 * 1.12.2 block into one real block per colour. This is the water half; there is no {@code BlockSpringOil}
 * counterpart yet -- see PORTING.md's survey of {@code buildcraft.core}'s remaining files for why the oil half
 * needs its own design (its block entity is only sometimes present, decided by whether the unported
 * {@code buildcraft.energy} module has registered one, which does not map onto the modern
 * {@code EntityBlock}/{@code BlockEntityType} model's static registration the way 1.12.2's per-instance
 * {@code createTileEntity} check did).
 *
 * <p>{@code setBlockUnbreakable()} + {@code setResistance(6000000.0F)} is {@code strength(-1.0F, 6000000.0F)}.
 * {@code World#scheduleUpdate(pos, block, delay)} is the default {@code scheduleTick(pos, block, delay)} on
 * {@link Level} (via {@code ScheduledTickAccess}); the scheduled callback itself is {@link #tick} rather than
 * {@code updateTick} (1.12.2 used one method for both scheduled and random ticks -- modern versions split them
 * into {@code tick} and {@code randomTick} -- and since this block only ever re-schedules itself, {@code tick} is
 * the only one it needs; {@code randomTicks()} is not set). {@code World#getHeight()} as a Y-coordinate loop
 * bound is replaced with {@link Level#getMaxY()}: the old call happened to work only because 1.12.2's world
 * floor was always Y=0, which is no longer guaranteed.
 */
public class BlockSpringWater extends Block {
    public BlockSpringWater(Properties properties) {
        super(properties);
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        super.onPlace(state, level, pos, oldState, movedByPiston);
        level.scheduleTick(pos, this, 5);
    }

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        level.scheduleTick(pos, this, 5);
        if (!level.getBlockState(pos.above()).isAir()) {
            return;
        }
        level.setBlock(pos.above(), Blocks.WATER.defaultBlockState(), 3);
    }
}
