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
import net.minecraft.world.phys.BlockHitResult;

import net.minecraftforge.network.NetworkHooks;

import buildcraft.api.blocks.ICustomRotationHandler;

import buildcraft.lib.block.BlockBCTile;

import buildcraft.energy.tile.TileEngineStone;

/**
 * Renamed from 1.12.2's shared, multi-variant {@code BlockEngine_BC8}. Mirrors the 26.x class of the same name;
 * the two real differences are {@link #neighborChanged}'s signature (still carries {@code fromPos}/
 * {@code isMoving} on this target rather than 26.x's {@code Orientation}) and how the GUI is opened -- this
 * target keeps a single {@link #use} (see {@code BlockFloodGate}'s own javadoc for that platform note) and opens
 * through {@link NetworkHooks#openScreen(ServerPlayer, net.minecraft.world.MenuProvider, BlockPos)}, which writes
 * the {@code BlockPos} the client needs to look the tile back up automatically -- unlike 26.x, which does that by
 * hand in {@code TileEngineStone#writeClientSideData}.
 */
public class BlockEngineStone extends BlockBCTile implements ICustomRotationHandler {

    public BlockEngineStone(BlockBehaviour.Properties properties) {
        super(properties);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new TileEngineStone(pos, state);
    }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide()) {
            return null;
        }
        return (tickLevel, pos, tickState, be) -> {
            if (be instanceof TileEngineStone engine) {
                engine.serverTick();
            }
        };
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (!level.isClientSide() && level.getBlockEntity(pos) instanceof TileEngineStone engine) {
            engine.onPlacedBy(placer, stack);
        }
    }

    @Override
    public void neighborChanged(BlockState state, Level level, BlockPos pos, Block neighborBlock, BlockPos fromPos, boolean isMoving) {
        super.neighborChanged(state, level, pos, neighborBlock, fromPos, isMoving);
        if (!level.isClientSide() && level.getBlockEntity(pos) instanceof TileEngineStone engine) {
            engine.onNeighbourBlockChanged();
        }
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (!level.isClientSide() && level.getBlockEntity(pos) instanceof TileEngineStone engine
            && player instanceof ServerPlayer serverPlayer) {
            NetworkHooks.openScreen(serverPlayer, engine, pos);
        }
        return InteractionResult.SUCCESS;
    }

    // ICustomRotationHandler

    @Override
    public InteractionResult attemptRotation(Level level, BlockPos pos, BlockState state, Direction sideWrenched) {
        if (level.getBlockEntity(pos) instanceof TileEngineStone engine) {
            return engine.attemptRotation();
        }
        return InteractionResult.FAIL;
    }
}
