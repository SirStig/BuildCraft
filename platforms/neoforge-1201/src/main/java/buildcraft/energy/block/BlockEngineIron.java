/*
 * Copyright (c) 2016 SpaceToad and the BuildCraft team
 * Copyright (c) 2026 Joshua Kac -- NeoForge port (BuildCraft 10)
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL
 * was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.energy.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
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

import net.minecraftforge.fluids.FluidUtil;
import net.minecraftforge.network.NetworkHooks;

import buildcraft.api.blocks.ICustomRotationHandler;
import buildcraft.api.transport.pipe.IItemPipe;

import buildcraft.lib.block.BlockBCTile;
import buildcraft.lib.misc.EntityUtil;

import buildcraft.energy.tile.TileEngineIron;

/**
 * The Combustion Engine's block -- see the 26.x copy for the right-click order (fluid container, then wrench/pipe
 * pass-through, then the GUI). This target keeps a single {@link #use}; a {@link InteractionResult#PASS} from it lets
 * the held item's own {@code useOn} run, which is how the wrench reaches the engine. The fluid container is handled
 * with Forge's {@link FluidUtil#interactWithFluidHandler(Player, InteractionHand, net.minecraftforge.fluids.capability.IFluidHandler)}.
 */
public class BlockEngineIron extends BlockBCTile implements ICustomRotationHandler {

    public BlockEngineIron(BlockBehaviour.Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(BlockStateProperties.FACING, Direction.UP));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(BlockStateProperties.FACING);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new TileEngineIron(pos, state);
    }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide()) {
            return null;
        }
        return (tickLevel, pos, tickState, be) -> {
            if (be instanceof TileEngineIron engine) {
                engine.serverTick();
            }
        };
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (!level.isClientSide() && level.getBlockEntity(pos) instanceof TileEngineIron engine) {
            engine.onPlacedBy(placer, stack);
        }
    }

    @Override
    public void neighborChanged(BlockState state, Level level, BlockPos pos, Block neighborBlock, BlockPos fromPos, boolean isMoving) {
        super.neighborChanged(state, level, pos, neighborBlock, fromPos, isMoving);
        if (!level.isClientSide() && level.getBlockEntity(pos) instanceof TileEngineIron engine) {
            engine.onNeighbourBlockChanged();
        }
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (!(level.getBlockEntity(pos) instanceof TileEngineIron engine)) {
            return InteractionResult.PASS;
        }
        ItemStack stack = player.getItemInHand(hand);
        if (!stack.isEmpty()) {
            if (FluidUtil.getFluidHandler(stack.copyWithCount(1)).isPresent()) {
                if (!level.isClientSide()) {
                    FluidUtil.interactWithFluidHandler(player, hand, engine.allTanks);
                }
                return InteractionResult.SUCCESS;
            }
            if (EntityUtil.getWrenchHand(player) != null || stack.getItem() instanceof IItemPipe) {
                return InteractionResult.PASS;
            }
        }
        if (!level.isClientSide() && player instanceof ServerPlayer serverPlayer) {
            NetworkHooks.openScreen(serverPlayer, engine, pos);
        }
        return InteractionResult.SUCCESS;
    }

    // ICustomRotationHandler

    @Override
    public InteractionResult attemptRotation(Level level, BlockPos pos, BlockState state, Direction sideWrenched) {
        if (level.getBlockEntity(pos) instanceof TileEngineIron engine) {
            return engine.attemptRotation();
        }
        return InteractionResult.FAIL;
    }
}
