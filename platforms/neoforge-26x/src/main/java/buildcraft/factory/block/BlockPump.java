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
 * Drains a connected body of fluid into its own tank and pushes that fluid out to neighbours; see
 * {@link TilePump}'s own javadoc for the search/drain algorithm.
 *
 * <p>1.12.2's {@code BlockPump} (unlike {@code BlockMiningWell}, which keeps a facing property purely for
 * parity -- see that class's own javadoc) had no facing property at all, and no {@code onBlockActivated} GUI hook
 * either -- it extended {@code BlockBCTile_Neptune} directly, overriding only {@code createTileEntity}. There is
 * therefore nothing to drop here beyond what {@link BlockBCTile} already provides on its own.
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
}
