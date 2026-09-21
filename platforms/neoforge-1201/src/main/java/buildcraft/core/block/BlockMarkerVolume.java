/*
 * Copyright (c) 2016 SpaceToad and the BuildCraft team
 * Copyright (c) 2026 Joshua Kac -- NeoForge port (BuildCraft 10)
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL
 * was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.core.block;

import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

import buildcraft.lib.block.BlockMarkerBase;

import buildcraft.core.tile.TileMarkerVolume;

/** The corner marker block for volume boxes.
 *
 * <p>Mirrors the 26.x class of the same name -- see that one for the full reasoning ({@code World
 * #isBlockPowered} -> {@code Level#hasNeighborSignal}, the dropped periodic {@code updateTick} re-check,
 * {@link #neighborChanged} deliberately not calling the base class' self-destruct check, and
 * {@code BlockEntity} losing {@code onPlacedBy}). The one real difference on this target is the interaction
 * split: 1.20.1 keeps a single {@link #use} rather than 26.x's {@code useWithoutItem}/{@code useItemOn} pair
 * (see PORTING.md's divergence table), and {@code neighborChanged} still takes {@code fromPos}/{@code isMoving}
 * rather than an {@code Orientation}. */
public class BlockMarkerVolume extends BlockMarkerBase {
    public BlockMarkerVolume(BlockBehaviour.Properties properties) {
        super(properties);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new TileMarkerVolume(pos, state);
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (!level.isClientSide() && level.getBlockEntity(pos) instanceof TileMarkerVolume volume) {
            volume.onPlacedBy(placer, stack);
        }
    }

    @Override
    public void neighborChanged(BlockState state, Level level, BlockPos pos, Block neighborBlock, BlockPos fromPos, boolean isMoving) {
        checkSignalState(level, pos);
    }

    private static void checkSignalState(Level level, BlockPos pos) {
        if (level.isClientSide()) {
            return;
        }
        if (level.getBlockEntity(pos) instanceof TileMarkerVolume volume) {
            boolean powered = level.hasNeighborSignal(pos);
            if (volume.isShowingSignals() != powered) {
                volume.switchSignals();
            }
        }
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (!level.isClientSide() && level.getBlockEntity(pos) instanceof TileMarkerVolume volume) {
            volume.onManualConnectionAttempt(player);
        }
        return InteractionResult.SUCCESS;
    }
}
