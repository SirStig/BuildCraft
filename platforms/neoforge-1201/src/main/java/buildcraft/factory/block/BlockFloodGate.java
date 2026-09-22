/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * Copyright (c) 2026 Joshua Kac -- NeoForge port (BuildCraft 10)
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL
 * was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.factory.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
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

import buildcraft.api.tools.IToolWrench;

import buildcraft.lib.block.BlockBCTile;

import buildcraft.factory.tile.TileFloodGate;

/**
 * Spreads a fluid piped into it out into the world. Mirrors the 26.x class of the same name -- see that one's
 * javadoc for the full account of what 1.12.2 dropped ({@code createTileEntity}/{@code addProperties}/
 * {@code getActualState}) and why this block's wrench interaction is actually reachable, unlike
 * {@link buildcraft.core.block.BlockEngineCreative}'s own. The one real difference from the 26.x class is that
 * 1.20.1 keeps a single {@link #use} rather than the {@code useItemOn}/{@code useWithoutItem} split.
 */
public class BlockFloodGate extends BlockBCTile {

    public BlockFloodGate(BlockBehaviour.Properties properties) {
        super(properties);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new TileFloodGate(pos, state);
    }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide()) {
            return null;
        }
        return (tickLevel, pos, tickState, be) -> {
            if (be instanceof TileFloodGate floodGate) {
                floodGate.serverTick();
            }
        };
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (!level.isClientSide() && level.getBlockEntity(pos) instanceof TileFloodGate floodGate) {
            floodGate.onPlacedBy(placer);
        }
    }

    /** Was {@code onBlockActivated}. See the 26.x copy of this class's javadoc for the wrench-toggle behaviour
     * this reproduces. */
    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        ItemStack stack = player.getItemInHand(hand);
        if (stack.getItem() instanceof IToolWrench) {
            Direction side = hit.getDirection();
            if (side != Direction.UP) {
                if (!level.isClientSide() && level.getBlockEntity(pos) instanceof TileFloodGate floodGate) {
                    floodGate.toggleOpenSide(side);
                }
                return InteractionResult.SUCCESS;
            }
            return InteractionResult.PASS;
        }
        return super.use(state, level, pos, player, hand, hit);
    }
}
