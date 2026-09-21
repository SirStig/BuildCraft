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

public class InvertedFluidFilter implements IFluidFilter {

    public final IFluidFilter delegate;

    public InvertedFluidFilter(IFluidFilter delegate) {
        this.delegate = delegate;
    }

    @Override
    public boolean matches(FluidResource fluid) {
        return !delegate.matches(fluid);
    }
}
