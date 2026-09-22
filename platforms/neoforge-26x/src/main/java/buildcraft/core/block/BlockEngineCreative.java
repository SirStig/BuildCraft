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
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
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
import net.minecraft.world.level.redstone.Orientation;
import net.minecraft.world.phys.BlockHitResult;

import org.jetbrains.annotations.Nullable;

import buildcraft.api.blocks.ICustomRotationHandler;

import buildcraft.lib.block.BlockBCTile;

import buildcraft.core.tile.TileEngineCreative;

/**
 * Renamed from 1.12.2's shared, multi-variant {@code BlockEngine_BC8} -- see {@link BlockEngineWood}'s javadoc for
 * the general shape of the split, and {@link TileEngineCreative}'s for the wrench-output-cycling feature this
 * block wires in through {@link #useItemOn}.
 *
 * <p>Note that {@code ItemWrench#useOn} already intercepts every wrench right-click through
 * {@code CustomRotationHelper.INSTANCE.attemptRotateBlock} and returns a definite result before the game ever
 * reaches a block's own {@link #useItemOn}/{@link #useWithoutItem} -- and since this block also implements
 * {@link ICustomRotationHandler}, wrenching it always rotates it rather than ever falling through to
 * {@link TileEngineCreative#onActivated}. This mirrors 1.12.2's own architecture exactly (its wrench likewise
 * intercepted rotation before {@code onBlockActivated} could run), so the output-cycling wiring below is ported
 * faithfully rather than newly broken -- but it means the feature was very likely already unreachable through a
 * wrench in 1.12.2 too. Kept in case a future non-rotation-registered wrench, or a change to
 * {@code ItemWrench}'s short-circuiting, ever reaches it; worth flagging for anyone relying on this cycling
 * actually firing from a wrench today.
 *
 * <p>{@link BlockStateProperties#FACING} is declared here for exactly the reason {@code BlockEngineWood} declares
 * it -- see that class's own javadoc and {@code TileEngineBase}'s "Facing visibility" entry for the full account.
 */
public class BlockEngineCreative extends BlockBCTile implements ICustomRotationHandler {

    public BlockEngineCreative(BlockBehaviour.Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(BlockStateProperties.FACING, Direction.UP));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(BlockStateProperties.FACING);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new TileEngineCreative(pos, state);
    }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide()) {
            return null;
        }
        return (tickLevel, pos, tickState, be) -> {
            if (be instanceof TileEngineCreative engine) {
                engine.serverTick();
            }
        };
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (!level.isClientSide() && level.getBlockEntity(pos) instanceof TileEngineCreative engine) {
            engine.onPlacedBy(placer, stack);
        }
    }

    @Override
    protected void neighborChanged(
        BlockState state, Level level, BlockPos pos, Block neighborBlock, @Nullable Orientation orientation, boolean movedByPiston
    ) {
        super.neighborChanged(state, level, pos, neighborBlock, orientation, movedByPiston);
        if (!level.isClientSide() && level.getBlockEntity(pos) instanceof TileEngineCreative engine) {
            engine.onNeighbourBlockChanged();
        }
    }

    @Override
    protected InteractionResult useItemOn(
        ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit
    ) {
        if (level.getBlockEntity(pos) instanceof TileEngineCreative engine && engine.onActivated(player, hand)) {
            return InteractionResult.SUCCESS;
        }
        return super.useItemOn(stack, state, level, pos, player, hand, hit);
    }

    // ICustomRotationHandler

    @Override
    public InteractionResult attemptRotation(Level level, BlockPos pos, BlockState state, Direction sideWrenched) {
        if (level.getBlockEntity(pos) instanceof TileEngineCreative engine) {
            return engine.attemptRotation();
        }
        return InteractionResult.FAIL;
    }
}
