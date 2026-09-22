/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * Copyright (c) 2026 Joshua Kac -- NeoForge port (BuildCraft 10)
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL
 * was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.factory.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;

import buildcraft.api.properties.BuildCraftProperties;

import buildcraft.lib.block.BlockBCTile;
import buildcraft.lib.block.IBlockWithFacing;

import buildcraft.factory.tile.TileMiningWell;

/**
 * Digs straight down from its own position, one block at a time, powered by MJ. Mirrors the 26.x class of the
 * same name -- see that one's javadoc for why the facing property is kept purely for parity, not function. This
 * file differs only in the usual places: every {@code BlockBehaviour} hook here is {@code public} rather than
 * {@code protected}, and {@link #onRemove} exists here at all specifically because this target has no
 * {@code BlockEntity#preRemoveSideEffects} hook for {@code TileMiningWell} to reach the same cleanup from
 * directly (see {@code TileMiner}'s own javadoc) -- confirmed via {@code javap}: 1.20.1's
 * {@code BlockBehaviour#onRemove(BlockState, Level, BlockPos, BlockState, boolean)} still exists and, per a
 * decompile of the real {@code LevelChunk#setBlockState}, still runs while the old block entity is still valid,
 * exactly where 1.12.2's {@code BlockBCTile_Neptune#breakBlock} used to.
 */
public class BlockMiningWell extends BlockBCTile implements IBlockWithFacing {

    public BlockMiningWell(BlockBehaviour.Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(BuildCraftProperties.BLOCK_FACING, Direction.NORTH));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(BuildCraftProperties.BLOCK_FACING);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(BuildCraftProperties.BLOCK_FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new TileMiningWell(pos, state);
    }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide()) {
            return null;
        }
        return (tickLevel, pos, tickState, be) -> {
            if (be instanceof TileMiningWell well) {
                well.serverTick();
            }
        };
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock()) && level.getBlockEntity(pos) instanceof TileMiningWell well) {
            well.onMinerRemoved();
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }
}
