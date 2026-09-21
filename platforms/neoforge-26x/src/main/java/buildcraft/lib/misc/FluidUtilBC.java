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

import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;

/** Partial port. Two 1.12.2 methods are not here:
 *
 * <ul>
 * <li>{@code pushFluidAround} needed {@code buildcraft.lib.fluid.Tank} and {@code CapUtil.CAP_FLUIDS}. Neither
 *     is ported -- {@code buildcraft.lib.fluid} hasn't landed yet (it is part of the {@code factory} module,
 *     far down PORTING.md's remaining-work table), and {@code CapUtil} is the capability-token chain PORTING.md
 *     already lists as blocked pending a real {@code IItemTransactor} capability design. Nothing to port against
 *     yet.</li>
 * <li>{@code onTankActivated} needed {@code buildcraft.lib.misc.SoundUtil} (bucket fill/empty sound effects),
 *     which is not ported by anyone yet -- it isn't in this batch or the concurrent one. It would also need
 *     redesigning against the item-side half of the transfer API (item-embedded fluid handling), which is real
 *     work rather than a rename; deferred alongside it.</li>
 * </ul>
 *
 * <p>Unlike 1.20.1, {@code IFluidHandler} is genuinely gone here, but {@code FluidStack} is not -- NeoForge kept
 * {@link FluidStack} as a plain value type (fluid + amount + data components, the fluid equivalent of
 * {@code ItemStack}), it just stopped being what a handler moves. Movement goes through
 * {@link ResourceHandler}{@code <}{@link FluidResource}{@code >} and {@link Transaction} instead (see
 * {@code FluidFilters}, the worked example this class follows); {@code IFluidHandlerAdv}'s filtered-drain role
 * needs no counterpart of its own any more, because every handler is already slot-introspectable -- see
 * {@link #move}, which walks {@code from}'s slots directly rather than delegating to a filter interface. Fluid
 * equality is the static {@link FluidStack#matches(FluidStack, FluidStack)} rather than 1.20.1's instance
 * {@code isFluidEqual} (PORTING.md's "Fluid equality" divergence row). */
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
}
