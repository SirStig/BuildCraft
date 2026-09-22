/*
 * Copyright (c) 2016 SpaceToad and the BuildCraft team
 * Copyright (c) 2026 Joshua Kac -- NeoForge port (BuildCraft 10)
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL
 * was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.factory.block;

import net.minecraft.core.BlockPos;
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

import buildcraft.lib.block.BlockBCTile;

import buildcraft.factory.tile.TileAutoWorkbenchItems;

/**
 * Right-click always opens the auto-workbench's GUI -- unlike {@code BlockFloodGate}, there is no wrench check at
 * all here, matching 1.12.2's own {@code onBlockActivated}, which never inspected the held item either.
 *
 * <p>Opens through {@link Player#openMenu}, since a right click on this block is handled without regard to what
 * is in hand -- the modern split between a stack-aware {@code useItemOn} and a stack-agnostic
 * {@code useWithoutItem} means this overrides the latter (confirmed {@code protected} via {@code javap} against
 * this target's {@code BlockBehaviour}, unlike 1.20.1's still-unified {@code use} -- see the 1.20.1 copy of this
 * class, and PORTING.md's divergence table).
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
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide() && level.getBlockEntity(pos) instanceof TileAutoWorkbenchItems tile) {
            player.openMenu(tile);
        }
        return InteractionResult.SUCCESS;
    }
}
