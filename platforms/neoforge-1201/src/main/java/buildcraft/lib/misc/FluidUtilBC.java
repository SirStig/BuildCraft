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

import net.minecraft.world.level.material.Fluid;

import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.fluids.capability.IFluidHandler.FluidAction;

import buildcraft.api.core.IFluidFilter;
import buildcraft.api.core.IFluidHandlerAdv;

/** Partial port. Two 1.12.2 methods are not here:
 *
 * <ul>
 * <li>{@code pushFluidAround} needed {@code buildcraft.lib.fluid.Tank} and {@code CapUtil.CAP_FLUIDS}. Neither
 *     is ported -- {@code buildcraft.lib.fluid} hasn't landed yet (it is part of the {@code factory} module,
 *     far down PORTING.md's remaining-work table), and {@code CapUtil} is the capability-token chain PORTING.md
 *     already lists as blocked pending a real {@code IItemTransactor} capability design. Nothing to port against
 *     yet.</li>
 * <li>{@code onTankActivated} needed {@code buildcraft.lib.misc.SoundUtil} (bucket fill/empty sound effects),
 *     which is not ported by anyone yet -- it isn't in this batch or the concurrent one. It also has a real API
 *     shape change worth noting for whoever ports it: {@code FluidUtil.getFluidHandler(ItemStack)} now returns
 *     {@code LazyOptional<IFluidHandlerItem>} rather than a nullable reference.</li>
 * </ul>
 *
 * <p>26.x also has a version of this file, but it is not a shared port: {@code IFluidHandler} is gone there,
 * replaced by {@code ResourceHandler<FluidResource>} plus {@code Transaction} (see PORTING.md's transfer-API
 * divergence note), so {@link #move} is a genuine redesign against that shape rather than a rename, walking
 * slots directly instead of going through {@link IFluidHandlerAdv}. NeoForge did keep a {@code FluidStack} value
 * type there, just not as what a handler moves -- see that copy's class javadoc.
 *
 * <p>Here on 1.20.1, {@code FluidStack}'s {@code amount} field became {@link FluidStack#getAmount()}/
 * {@link FluidStack#setAmount(int)}/{@link FluidStack#grow(int)}/{@link FluidStack#shrink(int)}, and
 * {@code IFluidHandler.fill}/{@code drain}'s {@code boolean} simulate flag became {@link FluidAction}. */
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
}
