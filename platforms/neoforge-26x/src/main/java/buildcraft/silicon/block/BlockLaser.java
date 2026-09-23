/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * Copyright (c) 2026 Joshua Kac -- NeoForge port (BuildCraft 10)
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL
 * was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.silicon.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

import buildcraft.api.properties.BuildCraftProperties;

import buildcraft.lib.block.BlockBCTile;
import buildcraft.lib.block.IBlockWithFacing;

import buildcraft.silicon.tile.TileLaser;

/**
 * The laser-beam emitter block -- see {@link TileLaser}'s own javadoc for the machine logic. Mirrors
 * {@code buildcraft.factory.block.BlockChute}'s own shape, exactly: {@link BuildCraftProperties#BLOCK_FACING_6}
 * ({@link #canFaceVertically()}), placement on the clicked face, and a real per-facing {@link VoxelShape} (1.12.2's
 * {@code isOpaqueCube()/isFullCube() -> false}, reproduced as a genuine non-cube shape from the start rather than
 * left as a full-cube placeholder -- see {@link LaserShapes}'s own javadoc for the geometry and its source).
 */
public class BlockLaser extends BlockBCTile implements IBlockWithFacing {

    public BlockLaser(BlockBehaviour.Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(BuildCraftProperties.BLOCK_FACING_6, Direction.UP));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(BuildCraftProperties.BLOCK_FACING_6);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return LaserShapes.get(state.getValue(BuildCraftProperties.BLOCK_FACING_6));
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return getShape(state, level, pos, context);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(BuildCraftProperties.BLOCK_FACING_6, context.getClickedFace());
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new TileLaser(pos, state);
    }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> entityType) {
        if (level.isClientSide()) {
            return null;
        }
        return (tickLevel, pos, tickState, be) -> {
            if (be instanceof TileLaser laser) {
                laser.serverTick();
            }
        };
    }

    // IBlockWithFacing

    @Override
    public boolean canFaceVertically() {
        return true;
    }
}
