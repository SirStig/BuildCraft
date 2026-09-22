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
 * {@code buildcraft.factory.tile.TileMiner}/{@code TileMiningWell} actually call, matching the "port only what's
 * called" discipline {@code ItemTransactorHelper} already used this session.
 *
 * <p>{@code breakBlockAndGetDrops} loses its {@code GameProfile owner}/{@code boolean grabAll} parameters
 * entirely, along with the {@code FakePlayer}/{@code BreakEvent} machinery 1.12.2 built them for
 * ({@code harvestBlock}/{@code destroyBlock}/{@code getFakePlayerWithTool}, none of which are ported). 1.12.2
 * needed a fake player only because {@code Block#harvestBlock}/{@code Block#canHarvestBlock} are player-shaped
 * APIs; the loot itself was never computed from anything about the player beyond the tool in their hand. Modern
 * Minecraft exposes that computation directly -- {@link Block#getDrops(BlockState, ServerLevel, BlockPos,
 * BlockEntity, net.minecraft.world.entity.Entity, net.minecraft.world.item.ItemInstance)} (confirmed by
 * decompiling the real {@code LevelChunk}/{@code Level} classes out of the merged jar: it builds a
 * {@code LootParams} straight from the position, tool and an optional breaking entity and asks the block's own
 * loot table for the result -- no player object of any kind required) -- so there is no fake player to
 * construct, no {@code GameProfile} to turn a bare {@code UUID} into, and consequently no {@code BreakEvent}
 * either (nothing in 1.12.2's version fired one on this path to begin with; it only existed as a side effect of
 * routing through {@code Block#harvestBlock}/{@code canHarvestBlock}, which this port bypasses entirely by going
 * straight to the loot computation). One real behavioural difference follows from skipping that path: protection
 * mods that only listen for a break event will not see (or be able to cancel) a mining well digging through a
 * claim. That is an accepted simplification for this pass, not an oversight -- wiring mod-compat event plumbing
 * into a first-pass machine is exactly the kind of unneeded machinery the porting guidelines warn against
 * building ahead of a real need. {@code ItemStack} implements the new {@code ItemInstance} interface directly, so
 * the tool argument below needs no wrapping.
 *
 * <p>{@code getFluidWithFlowing} returns a {@link FluidState} rather than 1.12.2's Forge {@code Fluid}: fluid-block
 * detection is simpler on this target, because vanilla now tracks whether a position holds a fluid (and whether
 * it is a source or flowing) directly on {@link BlockState#getFluidState()}, with no separate
 * {@code IFluidBlock}/{@code BlockFluidBase} abstraction to reconstruct. Only the {@code (World, BlockPos)}
 * overload is ported -- 1.12.2's {@code (Block)} overload existed only for {@code getFluid}/{@code canChangeBlock},
 * neither of which is in this trimmed set's call graph. The caller ({@code TileMiningWell#canBreak}) wants
 * viscosity, which 1.12.2 read off {@code Fluid#getViscosity()}; the modern equivalent lives on NeoForge's own
 * {@code FluidType} (confirmed via {@code javap}: {@code Fluid#getFluidType()} exists on this target, and
 * {@code FluidType#getViscosity(FluidState, BlockAndLightGetter, BlockPos)} gives the position-aware number
 * 1.12.2's flat {@code Fluid#getViscosity()} approximated), which the caller reaches through
 * {@code fluidState.getType().getFluidType()}.
 *
 * <p>{@code isUnbreakableBlock} drops its {@code GameProfile owner} parameter along with the
 * player-relative-hardness detour it existed for ({@code getBlockHardnessMining}'s
 * {@code state.getPlayerRelativeBlockHardness(fakePlayer, ...)} call, which only ever mattered for a real
 * player's held tool/status effects -- irrelevant to a machine breaking blocks with a fixed diamond pickaxe).
 * What is left is exactly {@code BlockState#getDestroySpeed(BlockGetter, BlockPos) < 0} -- the same
 * "hardness is negative" check 1.12.2 ultimately bottomed out on for bedrock and friends, just without the
 * unnecessary fake-player detour to reach it.
 *
 * <p>{@code computeBlockBreakPower} carries over unchanged in spirit -- {@code IBlockState#getBlockHardness} is
 * {@link BlockState#getDestroySpeed(net.minecraft.world.level.BlockGetter, BlockPos)} now -- except for its
 * {@code BCCoreConfig.miningMultiplier} factor, which is inlined as a constant: no config system is ported yet
 * (see PORTING.md), so this keeps 1.12.2's unconfigured default (1.0) rather than standing up a settings file
 * for one number.
 */
public final class BlockUtil {

    /** {@code BCCoreConfig.miningMultiplier}'s unconfigured default; see the class javadoc. */
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
