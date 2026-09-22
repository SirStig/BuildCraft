/*
 * Copyright (c) 2016 SpaceToad and the BuildCraft team
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

import buildcraft.factory.tile.TileAutoWorkbenchItems;

/**
 * Right-click always opens the auto-workbench's GUI -- unlike {@code BlockFloodGate}, there is no wrench check at
 * all here, matching 1.12.2's own {@code onBlockActivated}. Mirrors the 26.x class of the same name; the one real
 * difference is that this target keeps a single {@link #use} (see {@code BlockFloodGate}'s own javadoc for that
 * platform note) and opens through {@link NetworkHooks#openScreen(net.minecraft.server.level.ServerPlayer,
 * net.minecraft.world.MenuProvider, BlockPos)}, which writes the {@code BlockPos} the client needs to look the
 * tile back up automatically -- unlike 26.x, which has to do that by hand in
 * {@code TileAutoWorkbenchBase#writeClientSideData}.
 */
public class BlockAutoWorkbenchItems extends BlockBCTile {

    public BlockAutoWorkbenchItems(BlockBehaviour.Properties properties) {
        super(properties);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new TileAutoWorkbenchItems(pos, state);
    }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide()) {
            return null;
        }
        return (tickLevel, pos, tickState, be) -> {
            if (be instanceof TileAutoWorkbenchItems tile) {
                tile.serverTick();
            }
        };
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (!level.isClientSide() && level.getBlockEntity(pos) instanceof TileAutoWorkbenchItems tile) {
            tile.onPlacedBy(placer);
        }
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (!level.isClientSide() && level.getBlockEntity(pos) instanceof TileAutoWorkbenchItems tile
            && player instanceof ServerPlayer serverPlayer) {
            NetworkHooks.openScreen(serverPlayer, tile, pos);
        }
        return InteractionResult.SUCCESS;
    }
}
