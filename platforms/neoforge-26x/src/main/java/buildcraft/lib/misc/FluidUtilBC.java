/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * Copyright (c) 2026 Joshua Kac -- NeoForge port (BuildCraft 10)
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL
 * was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.lib.misc;

import java.util.ArrayList;
import java.util.List;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BucketPickup;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;

import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.FluidType;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;

/** Partial port. One 1.12.2 method is not here:
 *
 * <ul>
 * <li>{@code onTankActivated} needed {@code buildcraft.lib.misc.SoundUtil} (bucket fill/empty sound effects),
 *     which is not ported by anyone yet -- it isn't in this batch or the concurrent one. It would also need
 *     redesigning against the item-side half of the transfer API (item-embedded fluid handling), which is real
 *     work rather than a rename; deferred alongside it.</li>
 * </ul>
 *
 * <p>{@code pushFluidAround} <em>is</em> ported now, alongside {@code TilePump} (this method's first caller):
 * the note above used to say it needed {@code buildcraft.lib.fluid.Tank} and {@code CapUtil.CAP_FLUIDS}, neither
 * of which existed yet -- both now do ({@code buildcraft.lib.fluid.Tank}, and the vanilla-interop
 * {@link Capabilities.Fluid#BLOCK} capability NeoForge already ships, confirmed via {@code javap} to need no
 * BuildCraft-native token of its own; see {@code Tank}'s own class javadoc for the same "no counterpart needed"
 * finding {@code ItemTransactorCapabilities} already established for the item side). This is the stale-note
 * correction {@code InventoryUtil#addToBestAcceptor}'s own javadoc already had to make once its blocking
 * dependency landed; same situation here.
 *
 * <p>Unlike 1.20.1, {@code IFluidHandler} is genuinely gone here, but {@code FluidStack} is not -- NeoForge kept
 * {@link FluidStack} as a plain value type (fluid + amount + data components, the fluid equivalent of
 * {@code ItemStack}), it just stopped being what a handler moves. Movement goes through
 * {@link ResourceHandler}{@code <}{@link FluidResource}{@code >} and {@link Transaction} instead (see
 * {@code FluidFilters}, the worked example this class follows); {@code IFluidHandlerAdv}'s filtered-drain role
 * needs no counterpart of its own any more, because every handler is already slot-introspectable -- see
 * {@link #move}, which walks {@code from}'s slots directly rather than delegating to a filter interface. Fluid
 * equality is the static {@link FluidStack#matches(FluidStack, FluidStack)} rather than 1.20.1's instance
 * {@code isFluidEqual} (PORTING.md's "Fluid equality" divergence row).
 *
 * <p>{@code getFluidSource}/{@code drainBlock} are new (relocated here rather than into {@code BlockUtil}, which
 * is out of scope for this pass): 1.12.2's {@code BlockUtil} had {@code getFluid}/{@code getFluidWithoutFlowing}/
 * {@code drainBlock} for exactly this "is there a drainable source fluid at this position" question, built on
 * {@code IFluidBlock}/{@code FluidUtil.getFluidHandler}. Both no longer exist as concepts: fluid-block detection
 * is a direct {@link Level#getFluidState(BlockPos)} read now (already established by {@code BlockUtil
 * #getFluidWithFlowing}, which this reuses for "any fluid, flowing or not" and leaves untouched), and the actual
 * removal goes through vanilla's {@link BucketPickup} interface directly -- confirmed via a decompile of
 * {@code LiquidBlock#pickupBlock}, which already refuses to act unless the fluid state is a source, so there is
 * no need to gate on {@code isSource()} twice. {@code FluidUtil#tryPickupFluid} (NeoForge's own generic version
 * of this, in {@code net.neoforged.neoforge.transfer.fluid.FluidUtil}) was considered and rejected: its own
 * javadoc warns it can mutate the world even when the {@link Transaction} it was given is never committed, since
 * {@code pickupBlock} itself isn't transaction-aware -- unsuitable for {@code TilePump#mine}'s simulate-then-
 * commit two-step. {@link #drainBlock} sidesteps that by reading {@link FluidState} for the simulate case
 * (genuinely side-effect-free) and only calling {@code pickupBlock} for the real one. */
public class FluidUtilBC {

    public static List<FluidStack> mergeSameFluids(List<FluidStack> fluids) {
        List<FluidStack> stacks = new ArrayList<>();
        fluids.forEach(toAdd -> {
            boolean found = false;
            for (FluidStack stack : stacks) {
                if (FluidStack.matches(stack, toAdd)) {
                    stack.grow(toAdd.getAmount());
                    found = true;
                }
            }
            if (!found) {
                stacks.add(toAdd.copy());
            }
        });
        return stacks;
    }

    public static boolean areFluidStackEqual(@Nullable FluidStack a, @Nullable FluidStack b) {
        return (a == null && b == null)
            || (a != null && b != null && FluidStack.matches(a, b) && a.getAmount() == b.getAmount());
    }

    /** 1.12.2 compared {@code Fluid#getName()}. {@link Fluid} registry entries are singletons -- there is only
     * ever one instance per registered id -- so identity comparison is equivalent and does not need a registry
     * lookup to do it. */
    public static boolean areFluidsEqual(@Nullable Fluid a, @Nullable Fluid b) {
        return a == b;
    }

    /** @return The fluidstack that was moved, or null if no fluid was moved. */
    @Nullable
    public static FluidStack move(ResourceHandler<FluidResource> from, ResourceHandler<FluidResource> to) {
        return move(from, to, Integer.MAX_VALUE);
    }

    /** @param max The maximum amount of fluid to move.
     * @return The fluidstack that was moved, or null if no fluid was moved. */
    @Nullable
    public static FluidStack move(ResourceHandler<FluidResource> from, ResourceHandler<FluidResource> to, int max) {
        if (from == null || to == null || max <= 0) {
            return null;
        }
        for (int slot = 0; slot < from.size(); slot++) {
            FluidResource resource = from.getResource(slot);
            if (resource.isEmpty()) {
                continue;
            }

            // First simulate the whole thing to find out how much `to` will actually accept, without touching
            // `from` -- this transaction is never committed, so it always rolls back on close.
            int accepted;
            try (Transaction simulate = Transaction.openRoot()) {
                int extractable = from.extract(slot, resource, max, simulate);
                if (extractable <= 0) {
                    continue;
                }
                accepted = to.insert(resource, extractable, simulate);
            }
            if (accepted <= 0) {
                continue;
            }

            // Now do the real move, at exactly the amount the simulation says `to` will accept -- extracting
            // more than that from `from` and only inserting part of it into `to` would destroy the remainder.
            try (Transaction real = Transaction.openRoot()) {
                int extracted = from.extract(slot, resource, accepted, real);
                if (extracted <= 0) {
                    continue;
                }
                int inserted = to.insert(resource, extracted, real);
                if (inserted != extracted) {
                    throw new IllegalStateException("Mismatched ResourceHandler implementations! (extracted "
                        + extracted + ", but only accepted " + inserted + ")");
                }
                real.commit();
                return resource.toStack(inserted);
            }
        }
        return null;
    }

    /** Pushes as much of {@code from}'s contents as possible into every neighbouring block that exposes
     * {@link Capabilities.Fluid#BLOCK}, one side at a time -- the fluid-side sibling of
     * {@link InventoryUtil#addToBestAcceptor}, built directly on {@link #move} rather than reimplementing its
     * simulate-then-commit dance. Unlike {@code addToBestAcceptor}, there is no "drop the remainder" fallback:
     * fluid that nothing around it will accept simply stays in {@code from}, matching 1.12.2's own version (which
     * only ever drained what neighbouring tanks could actually take). */
    public static void pushFluidAround(Level level, BlockPos pos, ResourceHandler<FluidResource> from) {
        for (Direction side : Direction.values()) {
            ResourceHandler<FluidResource> to = level.getCapability(Capabilities.Fluid.BLOCK, pos.relative(side), side.getOpposite());
            if (to != null) {
                move(from, to);
            }
        }
    }

    /** {@code null} if there is no <em>source</em> fluid block at {@code pos} -- unlike
     * {@link BlockUtil#getFluidWithFlowing}, a flowing (non-source) block returns {@code null} here. Was
     * 1.12.2's {@code BlockUtil#getFluid(World, BlockPos)}; see the class javadoc for why it lives here now. */
    @Nullable
    public static Fluid getFluidSource(Level level, BlockPos pos) {
        FluidState state = level.getFluidState(pos);
        return state.isSource() ? state.getType() : null;
    }

    /** As {@link #getFluidSource(Level, BlockPos)}, but for a {@link BlockState} the caller already has in hand
     * (used to check the block directly below a candidate infinite-water-source position, without a second world
     * lookup). Was 1.12.2's {@code BlockUtil#getFluidWithoutFlowing(IBlockState)}. */
    @Nullable
    public static Fluid getFluidSource(BlockState state) {
        FluidState fluid = state.getFluidState();
        return fluid.isSource() ? fluid.getType() : null;
    }

    /** Drains (or, with {@code doDrain = false}, only inspects) the source fluid block at {@code pos}, mirroring
     * what an empty bucket would pick up there. Was 1.12.2's {@code BlockUtil#drainBlock}; see the class javadoc
     * for why this reads {@link FluidState} directly for the simulate case rather than routing through NeoForge's
     * own {@code FluidUtil#tryPickupFluid}. Returns {@code null} if {@code pos} holds no source fluid, or its
     * block doesn't implement {@link BucketPickup} at all (nothing vanilla ships doesn't, but a modded fluid
     * block need not). */
    @Nullable
    public static FluidStack drainBlock(Level level, BlockPos pos, boolean doDrain) {
        BlockState state = level.getBlockState(pos);
        FluidState fluidState = level.getFluidState(pos);
        if (!fluidState.isSource() || !(state.getBlock() instanceof BucketPickup pickup)) {
            return null;
        }
        Fluid fluid = fluidState.getType();
        if (doDrain) {
            // The returned ItemStack (nominally a filled bucket) is discarded: TilePump drains straight into its
            // own tank, never via an actual bucket item, and the fluid type/amount is already known from the
            // FluidState read above.
            pickup.pickupBlock(null, level, pos, state);
        }
        return new FluidStack(fluid, FluidType.BUCKET_VOLUME);
    }
}
