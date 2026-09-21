/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * Copyright (c) 2026 Joshua Kac -- NeoForge port (BuildCraft 10)
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL
 * was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.lib.inventory.filter;

import net.minecraft.core.NonNullList;
import net.minecraft.world.item.ItemStack;

import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.fluid.FluidUtil;

import buildcraft.api.core.IFluidFilter;

import buildcraft.lib.misc.StackUtil;

/**
 * Returns true if the fluid matches any one of the filter fluids.
 *
 * <p>{@code FluidStack}-as-a-key is gone (see {@link IFluidFilter}'s javadoc): what changes here is that each
 * filter entry is a {@link FluidResource} -- what the fluid is, with no amount attached -- rather than a
 * {@code FluidStack}. {@code FluidUtil.getFluidContained} becomes
 * {@link FluidUtil#getFirstStackContained}, which still hands back an amount-bearing {@code FluidStack}; only
 * {@link FluidResource#of} is kept from it.
 */
public class ArrayFluidFilter implements IFluidFilter {

    protected final FluidResource[] fluids;

    public ArrayFluidFilter(ItemStack... stacks) {
        this(StackUtil.listOf(stacks));
    }

    public ArrayFluidFilter(FluidResource... iFluids) {
        fluids = iFluids;
    }

    public ArrayFluidFilter(NonNullList<ItemStack> stacks) {
        fluids = new FluidResource[stacks.size()];

        for (int i = 0; i < stacks.size(); ++i) {
            FluidStack contained = FluidUtil.getFirstStackContained(stacks.get(i));
            if (!contained.isEmpty()) {
                fluids[i] = FluidResource.of(contained);
            }
        }
    }

    public boolean hasFilter() {
        for (FluidResource filter : fluids) {
            if (filter != null && !filter.isEmpty()) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean matches(FluidResource fluid) {
        for (FluidResource filter : fluids) {
            if (filter != null && filter.equals(fluid)) {
                return true;
            }
        }

        return false;
    }
}
