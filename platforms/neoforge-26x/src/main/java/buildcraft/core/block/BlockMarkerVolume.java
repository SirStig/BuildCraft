/*
 * Copyright (c) 2016 SpaceToad and the BuildCraft team
 * Copyright (c) 2026 Joshua Kac -- NeoForge port (BuildCraft 10)
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL
 * was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.core.block;

import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.redstone.Orientation;
import net.minecraft.world.phys.BlockHitResult;

import org.jetbrains.annotations.Nullable;

import buildcraft.lib.block.BlockMarkerBase;

import buildcraft.core.tile.TileMarkerVolume;

/** The corner marker block for volume boxes.
 *
 * <p>{@code World#isBlockPowered(pos)} is {@link Level#hasNeighborSignal(BlockPos)} now (confirmed via
 * {@code javap} -- inherited through {@code SignalGetter}, which {@code LevelReader} extends), used unchanged
 * here in {@link #checkSignalState}.
 *
 * <p>1.12.2's {@code updateTick} (a randomly-ticked periodic re-check of {@code isBlockPowered}, on top of the
 * {@code neighborChanged}-triggered check) is dropped, not reproduced with a scheduled tick. A redstone power
 * change always fires a neighbour-changed callback on every adjacent block -- vanilla's own redstone components
 * (e.g. {@code DiodeBlock}) rely on exactly that to schedule their own re-checks, rather than random ticking --
 * so {@link #checkSignalState} being wired only into {@link #neighborChanged} is already sufficient in practice;
 * the periodic re-check was a defensive backstop against nothing this class actually needs backstopped.
 *
 * <p>{@link #neighborChanged} deliberately does <em>not</em> call {@link BlockMarkerBase#neighborChanged}
 * (the self-destruct-if-unsupported check) -- this preserves a 1.12.2 quirk, not a new decision: the original
 * {@code BlockMarkerVolume#neighborChanged} fully overrode its base class' version the same way, so volume
 * markers never actually self-destroyed when their support block was removed, unlike path markers (which don't
 * override {@code neighborChanged} at all, and so keep the base behaviour). Carried over unchanged rather than
 * "fixed", since nothing about this port depends on changing it.
 *
 * <p>1.12.2's {@code onBlockActivated} (a single method for every right-click, regardless of held item) is
 * {@link #useWithoutItem} here -- the half of the modern split that fires once {@link ItemMarkerConnector}'s own
 * {@code useOn} (which handles the *other*, unrelated wrench-connector interaction on empty air, not a block)
 * and this block's (non-existent) {@code useItemOn} have both passed. See PORTING.md's divergence table for the
 * full {@code useWithoutItem}/{@code useItemOn}/{@code use} three-way split between this target and 1.20.1.
 *
 * <p>{@code TileEntity#onPlacedBy} no longer exists on {@code BlockEntity}; {@link #setPlacedBy} calls
 * {@link TileMarkerVolume#onPlacedBy} explicitly instead -- see that method's own javadoc. */
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
    protected void neighborChanged(
        BlockState state, Level level, BlockPos pos, Block neighborBlock, @Nullable Orientation orientation, boolean movedByPiston
    ) {
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
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide() && level.getBlockEntity(pos) instanceof TileMarkerVolume volume) {
            volume.onManualConnectionAttempt(player);
        }
        return InteractionResult.SUCCESS;
    }
}
