/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * Copyright (c) 2026 Joshua Kac -- NeoForge port (BuildCraft 10)
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL
 * was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.lib.misc;

import java.util.List;
import java.util.Optional;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;

import buildcraft.api.mj.MjAPI;

/**
 * Trimmed port of 1.12.2's 555-line {@code BlockUtil}: only the four methods
 * {@code buildcraft.factory.tile.TileMiner}/{@code TileMiningWell} actually call. Mirrors the 26.x copy of this
 * class -- see that one's javadoc for the full account of why {@code breakBlockAndGetDrops} needs no
 * {@code GameProfile}/{@code FakePlayer}/{@code BreakEvent} any more, why {@code getFluidWithFlowing} returns a
 * {@link FluidState} instead of a Forge {@code Fluid}, and why {@code isUnbreakableBlock} drops its owner
 * parameter. This file differs only in the one place the two platforms' {@code Block.getDrops} overload itself
 * differs: 1.20.1 still takes a plain {@code ItemStack} for the tool argument (26.x takes the new
 * {@code ItemInstance} interface, which {@code ItemStack} also implements there) -- confirmed via {@code javap}
 * against both merged jars.
 */
public final class BlockUtil {

    /** {@code BCCoreConfig.miningMultiplier}'s unconfigured default; see the 26.x copy of this class's javadoc. */
    private static final double MINING_MULTIPLIER = 1.0;

    private BlockUtil() {}

    public static long computeBlockBreakPower(Level level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        float hardness = state.getDestroySpeed(level, pos);
        return (long) Math.floor(16 * MjAPI.MJ * ((hardness + 1) * 2) * MINING_MULTIPLIER);
    }

    public static boolean isUnbreakableBlock(Level level, BlockPos pos) {
        return isUnbreakableBlock(level, pos, level.getBlockState(pos));
    }

    public static boolean isUnbreakableBlock(Level level, BlockPos pos, BlockState state) {
        return state.getDestroySpeed(level, pos) < 0;
    }

    /** {@code null} if there is no fluid (source or flowing) at {@code pos} at all. */
    @Nullable
    public static FluidState getFluidWithFlowing(Level level, BlockPos pos) {
        FluidState fluidState = level.getFluidState(pos);
        return fluidState.isEmpty() ? null : fluidState;
    }

    /** Breaks the block at {@code pos}, computing its drops from {@code tool} directly rather than letting them
     * spawn (and then re-collecting them) as item entities the way 1.12.2 had to. Empty if {@code pos} was
     * already air, or if the block refused to be removed (only possible here if a caller skipped
     * {@link #isUnbreakableBlock} first). */
    public static Optional<List<ItemStack>> breakBlockAndGetDrops(ServerLevel level, BlockPos pos, ItemStack tool) {
        BlockState state = level.getBlockState(pos);
        if (state.isAir()) {
            return Optional.empty();
        }
        BlockEntity blockEntity = level.getBlockEntity(pos);
        List<ItemStack> drops = Block.getDrops(state, level, pos, blockEntity, null, tool);
        if (!level.destroyBlock(pos, false)) {
            return Optional.empty();
        }
        return Optional.of(drops);
    }
}
