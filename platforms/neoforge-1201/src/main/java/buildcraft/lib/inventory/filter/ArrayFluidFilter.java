/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * Copyright (c) 2026 Joshua Kac -- NeoForge port (BuildCraft 10)
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL
 * was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.lib.inventory.filter;

import java.util.Optional;

import net.minecraft.core.NonNullList;
import net.minecraft.world.item.ItemStack;

import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidUtil;

import buildcraft.api.core.IFluidFilter;

import buildcraft.lib.misc.StackUtil;

/** Returns true if the stack matches any one one of the filter stacks. */
public class ArrayFluidFilter implements IFluidFilter {

    protected FluidStack[] fluids;

    public ArrayFluidFilter(ItemStack... stacks) {
        this(StackUtil.listOf(stacks));
    }

    public ArrayFluidFilter(FluidStack... iFluids) {
        fluids = iFluids;
    }

    public ArrayFluidFilter(NonNullList<ItemStack> stacks) {
        fluids = new FluidStack[stacks.size()];

        for (int i = 0; i < stacks.size(); ++i) {
            // 1.12.2's FluidUtil.getFluidContained returned a nullable FluidStack directly; the 1.20.1 copy
            // wraps it in an Optional instead, otherwise unchanged.
            Optional<FluidStack> stack = FluidUtil.getFluidContained(stacks.get(i));
            if (stack.isPresent()) {
                fluids[i] = stack.get();
            }
        }
    }

    public boolean hasFilter() {
        for (FluidStack filter : fluids) {
            if (filter != null) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean matches(FluidStack fluid) {
        for (FluidStack filter : fluids) {
            if (filter != null && filter.isFluidEqual(fluid)) {
                return true;
            }
        }

        return false;
    }
}
