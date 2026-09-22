/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * Copyright (c) 2026 Joshua Kac -- NeoForge port (BuildCraft 10)
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL
 * was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.factory.block;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

import net.minecraftforge.network.NetworkHooks;

import buildcraft.lib.block.BlockBCTile;

import buildcraft.factory.tile.TileAutoWorkbenchFluids;

/**
 * A straight mirror of {@link BlockAutoWorkbenchItems}'s shape on this target -- same properties, same single
 * {@link #use} GUI-opening pattern through {@link NetworkHooks#openScreen}. See
 * {@code buildcraft.factory.tile.TileAutoWorkbenchFluids}'s own javadoc for why this block never actually shipped
 * in 1.12.2 despite its source existing, and for what stands in for the fluids variant's never-created assets in
 * this port.
 */
public class BlockAutoWorkbenchFluids extends BlockBCTile {

    public BlockAutoWorkbenchFluids(BlockBehaviour.Properties properties) {
        super(properties);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new TileAutoWorkbenchFluids(pos, state);
    }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide()) {
            return null;
        }
        return (tickLevel, pos, tickState, be) -> {
            if (be instanceof TileAutoWorkbenchFluids tile) {
                tile.serverTick();
            }
        };
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (!level.isClientSide() && level.getBlockEntity(pos) instanceof TileAutoWorkbenchFluids tile) {
            tile.onPlacedBy(placer);
        }
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (!level.isClientSide() && level.getBlockEntity(pos) instanceof TileAutoWorkbenchFluids tile
            && player instanceof ServerPlayer serverPlayer) {
            NetworkHooks.openScreen(serverPlayer, tile, pos);
        }
        return InteractionResult.SUCCESS;
    }
}
