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
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;

import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidType;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.fluids.capability.IFluidHandler.FluidAction;

import buildcraft.api.core.IFluidFilter;
import buildcraft.api.core.IFluidHandlerAdv;

/** Partial port. One 1.12.2 method is not here:
 *
 * <ul>
 * <li>{@code onTankActivated} needed {@code buildcraft.lib.misc.SoundUtil} (bucket fill/empty sound effects),
 *     which is not ported by anyone yet -- it isn't in this batch or the concurrent one. It also has a real API
 *     shape change worth noting for whoever ports it: {@code FluidUtil.getFluidHandler(ItemStack)} now returns
 *     {@code LazyOptional<IFluidHandlerItem>} rather than a nullable reference.</li>
 * </ul>
 *
 * <p>{@code pushFluidAround} <em>is</em> ported now, alongside {@code TilePump} (this method's first caller):
 * the note above used to say it needed {@code buildcraft.lib.fluid.Tank} and {@code CapUtil.CAP_FLUIDS}, neither
 * of which existed yet -- both now do ({@code buildcraft.lib.fluid.Tank}, and {@link ForgeCapabilities
 * #FLUID_HANDLER}, an existing token this port needed no BuildCraft-native counterpart for -- see {@code Tank}'s
 * own class javadoc). Same stale-note correction {@code InventoryUtil#addToBestAcceptor}'s own javadoc already
 * had to make once its blocking dependency landed.
 *
 * <p>26.x also has a version of this file, but it is not a shared port: {@code IFluidHandler} is gone there,
 * replaced by {@code ResourceHandler<FluidResource>} plus {@code Transaction} (see PORTING.md's transfer-API
 * divergence note), so {@link #move} is a genuine redesign against that shape rather than a rename, walking
 * slots directly instead of going through {@link IFluidHandlerAdv}. NeoForge did keep a {@code FluidStack} value
 * type there, just not as what a handler moves -- see that copy's class javadoc.
 *
 * <p>Here on 1.20.1, {@code FluidStack}'s {@code amount} field became {@link FluidStack#getAmount()}/
 * {@link FluidStack#setAmount(int)}/{@link FluidStack#grow(int)}/{@link FluidStack#shrink(int)}, and
 * {@code IFluidHandler.fill}/{@code drain}'s {@code boolean} simulate flag became {@link FluidAction}.
 *
 * <p>{@code getFluidSource}/{@code drainBlock} are new (relocated here rather than into {@code BlockUtil}, which
 * is out of scope for this pass). Confirmed via {@code javap} plus a decompile of the real
 * {@code net.minecraftforge.fluids.FluidUtil#getFluidHandler(Level, BlockPos, Direction)}: that method only
 * resolves a handler through a neighbouring position's {@link BlockEntity}, and a plain vanilla water/lava lake
 * has none -- so it cannot see ordinary world fluid, unlike 1.12.2's own version of the same method (which
 * wrapped {@code IFluidBlock}/{@code BucketPickup} blocks directly, no block entity required). The real modern
 * path for "drain the fluid block sitting in the world" is {@link BucketPickup} directly (the same interface
 * Forge's own {@code BucketPickupHandlerWrapper} builds on for this exact case), confirmed by reading that
 * wrapper's decompiled source: simulate reads {@link FluidState} only (no world mutation), and only the execute
 * branch calls {@code pickupBlock}, which vanilla's {@code LiquidBlock} already refuses unless the state is a
 * source -- so {@link #drainBlock} needs no separate source check of its own beyond what it already does. */
public class FluidUtilBC {

    public static List<FluidStack> mergeSameFluids(List<FluidStack> fluids) {
        List<FluidStack> stacks = new ArrayList<>();
        fluids.forEach(toAdd -> {
            boolean found = false;
            for (FluidStack stack : stacks) {
                if (stack.isFluidEqual(toAdd)) {
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
            || (a != null && b != null && a.isFluidEqual(b) && a.getAmount() == b.getAmount());
    }

    /** 1.12.2 compared {@code Fluid#getName()}. {@link Fluid} registry entries are singletons -- there is only
     * ever one instance per registered id -- so identity comparison is equivalent and does not need a registry
     * lookup to do it. */
    public static boolean areFluidsEqual(@Nullable Fluid a, @Nullable Fluid b) {
        return a == b;
    }

    /** @return The fluidstack that was moved, or null if no fluid was moved. */
    @Nullable
    public static FluidStack move(IFluidHandler from, IFluidHandler to) {
        return move(from, to, Integer.MAX_VALUE);
    }

    /** @param max The maximum amount of fluid to move.
     * @return The fluidstack that was moved, or null if no fluid was moved. */
    @Nullable
    public static FluidStack move(IFluidHandler from, IFluidHandler to, int max) {
        if (from == null || to == null) {
            return null;
        }
        FluidStack toDrainPotential;
        if (from instanceof IFluidHandlerAdv) {
            IFluidFilter filter = f -> to.fill(f, FluidAction.SIMULATE) > 0;
            toDrainPotential = ((IFluidHandlerAdv) from).drain(filter, max, FluidAction.SIMULATE);
        } else {
            toDrainPotential = from.drain(max, FluidAction.SIMULATE);
        }
        if (toDrainPotential == null || toDrainPotential.isEmpty()) {
            return null;
        }
        int accepted = to.fill(toDrainPotential.copy(), FluidAction.SIMULATE);
        if (accepted <= 0) {
            return null;
        }
        FluidStack toDrain = new FluidStack(toDrainPotential, accepted);
        if (accepted < toDrainPotential.getAmount()) {
            toDrainPotential = from.drain(toDrain, FluidAction.SIMULATE);
            if (toDrainPotential == null || toDrainPotential.getAmount() < accepted) {
                return null;
            }
        }
        FluidStack drained = from.drain(toDrain.copy(), FluidAction.EXECUTE);
        if (drained == null || toDrain.getAmount() != drained.getAmount() || !toDrain.isFluidEqual(drained)) {
            String detail = "(To Drain = " + StringUtilBC.fluidToString(toDrain);
            detail += ",\npotential drain = " + StringUtilBC.fluidToString(toDrainPotential) + ")";
            detail += ",\nactually drained = " + StringUtilBC.fluidToString(drained) + ")";
            detail += ",\nIFluidHandler (from) = " + from.getClass() + "(" + from + ")";
            detail += ",\nIFluidHandler (to) = " + to.getClass() + "(" + to + ")";
            throw new IllegalStateException("Drained fluid did not equal expected fluid!\n" + detail);
        }
        int actuallyAccepted = to.fill(drained, FluidAction.EXECUTE);
        if (actuallyAccepted != accepted) {
            String detail = "(actually accepted = " + actuallyAccepted + ", accepted = " + accepted + ")";
            throw new IllegalStateException("Mismatched IFluidHandler implementations!\n" + detail);
        }
        return new FluidStack(drained, accepted);
    }

    /** Pushes as much of {@code from}'s contents as possible into every neighbouring block entity that exposes
     * {@link ForgeCapabilities#FLUID_HANDLER}, one side at a time -- the fluid-side sibling of
     * {@link InventoryUtil#addToBestAcceptor}, built directly on {@link #move}. See the 26.x copy of this class
     * for why there is no "drop the remainder" fallback the way {@code addToBestAcceptor} has one. */
    public static void pushFluidAround(Level level, BlockPos pos, IFluidHandler from) {
        for (Direction side : Direction.values()) {
            BlockEntity neighbor = level.getBlockEntity(pos.relative(side));
            if (neighbor == null) {
                continue;
            }
            IFluidHandler to = neighbor.getCapability(ForgeCapabilities.FLUID_HANDLER, side.getOpposite()).orElse(null);
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
        return state.isEmpty() || !state.isSource() ? null : state.getType();
    }

    /** As {@link #getFluidSource(Level, BlockPos)}, but for a {@link BlockState} the caller already has in hand.
     * Was 1.12.2's {@code BlockUtil#getFluidWithoutFlowing(IBlockState)}. */
    @Nullable
    public static Fluid getFluidSource(BlockState state) {
        FluidState fluid = state.getFluidState();
        return fluid.isEmpty() || !fluid.isSource() ? null : fluid.getType();
    }

    /** Drains (or, with {@code doDrain = false}, only inspects) the source fluid block at {@code pos}, mirroring
     * what an empty bucket would pick up there. Was 1.12.2's {@code BlockUtil#drainBlock}; see the class javadoc
     * for why this goes through {@link BucketPickup} directly rather than
     * {@code net.minecraftforge.fluids.FluidUtil#getFluidHandler}. */
    @Nullable
    public static FluidStack drainBlock(Level level, BlockPos pos, boolean doDrain) {
        BlockState state = level.getBlockState(pos);
        FluidState fluidState = level.getFluidState(pos);
        if (fluidState.isEmpty() || !fluidState.isSource() || !(state.getBlock() instanceof BucketPickup pickup)) {
            return null;
        }
        Fluid fluid = fluidState.getType();
        if (doDrain) {
            // The returned ItemStack (nominally a filled bucket) is discarded: TilePump drains straight into its
            // own tank, never via an actual bucket item, and the fluid type/amount is already known above.
            pickup.pickupBlock(level, pos, state);
        }
        return new FluidStack(fluid, FluidType.BUCKET_VOLUME);
    }
}
