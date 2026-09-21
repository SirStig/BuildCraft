/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * Copyright (c) 2026 Joshua Kac -- NeoForge port (BuildCraft 10)
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL
 * was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.lib.inventory.filter;

import net.neoforged.neoforge.transfer.fluid.FluidResource;

import buildcraft.api.core.IFluidFilter;

/** Returns true if the fluid matches any one of the filter fluids.
 *
 * <p>1.12.2 tested {@code fluid != null}, since a {@code FluidStack} could be a null reference standing in for
 * "nothing". {@link FluidResource} is never null -- {@link IFluidFilter#matches} doesn't accept one -- so
 * {@link FluidResource#isEmpty()} is the equivalent "nothing here" case. */
public class PassThroughFluidFilter implements IFluidFilter {

    @Override
    public boolean matches(FluidResource fluid) {
        return !fluid.isEmpty();
    }

}
