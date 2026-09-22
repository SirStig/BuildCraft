/*
 * Copyright (c) 2016 SpaceToad and the BuildCraft team
 * Copyright (c) 2026 Joshua Kac -- NeoForge port (BuildCraft 10)
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL
 * was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.core.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

import buildcraft.api.blocks.ICustomRotationHandler;

import buildcraft.lib.block.BlockBCTile;

import buildcraft.core.tile.TileEngineWood;

/**
 * Renamed from 1.12.2's shared, multi-variant {@code BlockEngine_BC8} -- see {@link TileEngineWood}'s javadoc for
 * the general reasoning. Mirrors the 26.x class of the same name; the one real difference is
 * {@link #neighborChanged}'s signature, which still carries {@code fromPos}/{@code isMoving} on this target rather
 * than 26.x's {@code Orientation}.
 *
 * <p>{@link BlockStateProperties#FACING} is declared here identically to the 26.x class -- see that class's own
 * javadoc and {@code TileEngineBase}'s "Facing visibility" entry for the full account of the fix this closes.
 */
public class BlockEngineWood extends BlockBCTile implements ICustomRotationHandler {

    public BlockEngineWood(BlockBehaviour.Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(BlockStateProperties.FACING, Direction.UP));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(BlockStateProperties.FACING);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new TileEngineWood(pos, state);
    }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide()) {
            return null;
        }
        return (tickLevel, pos, tickState, be) -> {
            if (be instanceof TileEngineWood engine) {
                engine.serverTick();
            }
        };
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (!level.isClientSide() && level.getBlockEntity(pos) instanceof TileEngineWood engine) {
            engine.onPlacedBy(placer, stack);
        }
    }

    @Override
    public void neighborChanged(BlockState state, Level level, BlockPos pos, Block neighborBlock, BlockPos fromPos, boolean isMoving) {
        super.neighborChanged(state, level, pos, neighborBlock, fromPos, isMoving);
        if (!level.isClientSide() && level.getBlockEntity(pos) instanceof TileEngineWood engine) {
            engine.onNeighbourBlockChanged();
        }
    }

    // ICustomRotationHandler

    @Override
    public InteractionResult attemptRotation(Level level, BlockPos pos, BlockState state, Direction sideWrenched) {
        if (level.getBlockEntity(pos) instanceof TileEngineWood engine) {
            return engine.attemptRotation();
        }
        return InteractionResult.FAIL;
    }
}
