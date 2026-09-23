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
import net.minecraft.world.phys.BlockHitResult;

import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

import buildcraft.api.blocks.ICustomRotationHandler;

import buildcraft.lib.block.BlockBCTile;
import buildcraft.lib.block.EngineShapes;

import buildcraft.core.tile.TileEngineCreative;

/**
 * Renamed from 1.12.2's shared, multi-variant {@code BlockEngine_BC8} -- see {@link BlockEngineWood}'s javadoc for
 * the general shape of the split, and {@link TileEngineCreative}'s for the wrench-output-cycling feature this
 * block wires in through {@link #use}. See the 26.x class of the same name for why that wiring is very likely
 * unreachable through an actual wrench, on both targets, since {@code ItemWrench} intercepts wrench clicks before
 * a block's own interaction hook ever runs. The one real difference from the 26.x class is that 1.20.1 keeps a
 * single {@link #use} rather than the {@code useItemOn}/{@code useWithoutItem} split, and
 * {@link #neighborChanged} still carries {@code fromPos}/{@code isMoving} rather than an {@code Orientation}.
 *
 * <p>{@link BlockStateProperties#FACING} is declared here identically to the 26.x class -- see
 * {@code BlockEngineWood}'s own javadoc and {@code TileEngineBase}'s "Facing visibility" entry for the full
 * account of the fix this closes.
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

    /** The real per-facing shape, replacing the default full-cube collision/outline this block had no override
     * for at all until now -- see {{@link EngineShapes}}'s own javadoc for why and how it's derived. */
    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {{
        return EngineShapes.get(state.getValue(BlockStateProperties.FACING));
    }}

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {{
        return getShape(state, level, pos, context);
    }}

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
    public void neighborChanged(BlockState state, Level level, BlockPos pos, Block neighborBlock, BlockPos fromPos, boolean isMoving) {
        super.neighborChanged(state, level, pos, neighborBlock, fromPos, isMoving);
        if (!level.isClientSide() && level.getBlockEntity(pos) instanceof TileEngineCreative engine) {
            engine.onNeighbourBlockChanged();
        }
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (level.getBlockEntity(pos) instanceof TileEngineCreative engine && engine.onActivated(player, hand)) {
            return InteractionResult.SUCCESS;
        }
        return super.use(state, level, pos, player, hand, hit);
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
