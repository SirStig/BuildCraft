/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * Copyright (c) 2026 Joshua Kac -- NeoForge port (BuildCraft 10)
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL
 * was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.factory.block;

import java.util.Locale;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.redstone.Orientation;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

import buildcraft.api.properties.BuildCraftProperties;

import buildcraft.lib.block.BlockBCTile;
import buildcraft.lib.block.IBlockWithFacing;

import buildcraft.factory.tile.TileHeatExchange;

/**
 * 1.12.2's {@code BlockHeatExchange}. 1.12.2 computed {@code part}/{@code connected_left}/{@code connected_right}
 * in {@code getActualState} (render-only, never stored); {@code getActualState} no longer exists, so all three are
 * real block state here: {@link #PROP_PART} is written by {@link TileHeatExchange} whenever its section changes,
 * and the two connection flags are kept current by {@link #updateShape}/{@link #getStateForPlacement} with 1.12.2's
 * own rule ({@code doesNeighbourConnect}: the neighbour on that side is a heat exchanger with the same facing;
 * "left" is {@code facing.getClockWise()}, 1.12.2's {@code rotateY()}). 1.12.2's {@code connected_y} was always
 * forced to {@code false} and is dropped. Wrench rotation goes to {@link TileHeatExchange#rotate}, as in 1.12.2.
 */
public class BlockHeatExchange extends BlockBCTile implements IBlockWithFacing {

    public enum EnumExchangePart implements StringRepresentable {
        START,
        MIDDLE,
        END;

        private final String lowerCaseName = name().toLowerCase(Locale.ROOT);

        @Override
        public String getSerializedName() {
            return lowerCaseName;
        }
    }

    public static final EnumProperty<EnumExchangePart> PROP_PART = EnumProperty.create("part", EnumExchangePart.class);
    public static final BooleanProperty PROP_CONNECTED_LEFT = BooleanProperty.create("connected_left");
    public static final BooleanProperty PROP_CONNECTED_RIGHT = BooleanProperty.create("connected_right");

    public BlockHeatExchange(BlockBehaviour.Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState()
            .setValue(BuildCraftProperties.BLOCK_FACING, Direction.WEST)
            .setValue(PROP_PART, EnumExchangePart.MIDDLE)
            .setValue(PROP_CONNECTED_LEFT, false)
            .setValue(PROP_CONNECTED_RIGHT, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(BuildCraftProperties.BLOCK_FACING, PROP_PART, PROP_CONNECTED_LEFT, PROP_CONNECTED_RIGHT);
    }

    /** The real per-part, per-facing shape -- see {@link HeatExchangeShapes}'s own javadoc for why this was
     * missing and how it is derived from the block's own already-real model geometry. */
    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return HeatExchangeShapes.get(state.getValue(PROP_PART), state.getValue(BuildCraftProperties.BLOCK_FACING));
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return getShape(state, level, pos, context);
    }

    private static boolean doesNeighbourConnect(BlockGetter level, BlockPos pos, Direction thisFacing, Direction dir) {
        BlockState neighbour = level.getBlockState(pos.relative(dir));
        return neighbour.getBlock() instanceof BlockHeatExchange
            && neighbour.getValue(BuildCraftProperties.BLOCK_FACING) == thisFacing;
    }

    private static BlockState withConnections(BlockState state, BlockGetter level, BlockPos pos) {
        Direction facing = state.getValue(BuildCraftProperties.BLOCK_FACING);
        return state
            .setValue(PROP_CONNECTED_LEFT, doesNeighbourConnect(level, pos, facing, facing.getClockWise()))
            .setValue(PROP_CONNECTED_RIGHT, doesNeighbourConnect(level, pos, facing, facing.getCounterClockWise()));
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockState state = defaultBlockState().setValue(BuildCraftProperties.BLOCK_FACING,
            context.getHorizontalDirection().getOpposite());
        return withConnections(state, context.getLevel(), context.getClickedPos());
    }

    @Override
    protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks, BlockPos pos,
        Direction directionToNeighbour, BlockPos neighbourPos, BlockState neighbourState, RandomSource random) {
        if (directionToNeighbour.getAxis().isVertical()) {
            return state;
        }
        return withConnections(state, level, pos);
    }

    /** 1.12.2's {@code onNeighbourBlockChanged}, which ignored neighbours at a different Y. 26.x's
     * {@code neighborChanged} no longer passes the neighbour's position, so any change re-checks the line
     * (cheap: at most 10 block-entity lookups, once, on this tile's next tick). */
    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block block, @Nullable Orientation orientation,
        boolean movedByPiston) {
        super.neighborChanged(state, level, pos, block, orientation, movedByPiston);
        if (level.getBlockEntity(pos) instanceof TileHeatExchange tile) {
            tile.onNeighbourChanged();
        }
    }

    @Override
    public InteractionResult attemptRotation(Level level, BlockPos pos, BlockState state, Direction sideWrenched) {
        if (level.getBlockEntity(pos) instanceof TileHeatExchange tile) {
            return tile.rotate() ? InteractionResult.SUCCESS : InteractionResult.FAIL;
        }
        return InteractionResult.FAIL;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new TileHeatExchange(pos, state);
    }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide()) {
            return (tickLevel, pos, tickState, be) -> {
                if (be instanceof TileHeatExchange tile) {
                    tile.clientTick();
                }
            };
        }
        return (tickLevel, pos, tickState, be) -> {
            if (be instanceof TileHeatExchange tile) {
                tile.serverTick();
            }
        };
    }
}
