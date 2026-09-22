/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * Copyright (c) 2026 Joshua Kac -- NeoForge port (BuildCraft 10)
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL
 * was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.factory.block;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;

import buildcraft.lib.block.BlockBCTile;

import buildcraft.factory.tile.TilePump;

/**
 * Drains a connected body of fluid into its own tank and pushes that fluid out to neighbours. Mirrors the 26.x
 * class of the same name -- see that one's javadoc for why there is no facing property or GUI hook to drop here.
 * This file differs only in the usual place: {@link #onRemove} exists here at all specifically because this
 * target has no {@code BlockEntity#preRemoveSideEffects} hook for {@code TilePump} to reach
 * {@code onMinerRemoved()} from directly -- see {@code TileMiner}'s own javadoc, and {@code BlockMiningWell}'s
 * identical precedent.
 */
public class BlockPump extends BlockBCTile {

    public BlockPump(BlockBehaviour.Properties properties) {
        super(properties);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new TilePump(pos, state);
    }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide()) {
            return null;
        }
        return (tickLevel, pos, tickState, be) -> {
            if (be instanceof TilePump pump) {
                pump.serverTick();
            }
        };
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (!level.isClientSide() && level.getBlockEntity(pos) instanceof TilePump pump) {
            pump.onPlacedBy(placer);
        }
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock()) && level.getBlockEntity(pos) instanceof TilePump pump) {
            pump.onMinerRemoved();
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }
}
