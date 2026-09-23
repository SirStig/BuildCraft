/*
 * Copyright (c) 2016 SpaceToad and the BuildCraft team
 * Copyright (c) 2026 Joshua Kac -- NeoForge port (BuildCraft 10)
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL
 * was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package buildcraft.builders.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;

import buildcraft.api.properties.BuildCraftProperties;

import buildcraft.lib.block.BlockBCTile;
import buildcraft.lib.block.IBlockWithFacing;

import buildcraft.builders.tile.TileElectronicLibrary;

/**
 * Mirrors {@link BlockQuarry}/{@link BlockBuilder}'s own shape -- see those classes' javadoc for the general
 * pattern, and {@link TileElectronicLibrary}'s own javadoc for this round's scope cut (a master/duplicate-in/
 * duplicate-out block, not the original's network-mediated snapshot-database duplicator) and why there is no GUI.
 */
public class BlockElectronicLibrary extends BlockBCTile implements IBlockWithFacing {

    public BlockElectronicLibrary(BlockBehaviour.Properties properties) {
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
        return new TileElectronicLibrary(pos, state);
    }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide()) {
            return null;
        }
        return (tickLevel, pos, tickState, be) -> {
            if (be instanceof TileElectronicLibrary library) {
                library.serverTick();
            }
        };
    }

    @Override
    public SoundType getSoundType(BlockState state) {
        return SoundType.ANVIL;
    }

    // IBlockWithFacing

    @Override
    public boolean canBeRotated(Level level, BlockPos pos, BlockState state) {
        return false;
    }
}
