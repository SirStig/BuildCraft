/*
 * Copyright (c) 2011-2015, SpaceToad and the BuildCraft Team http://www.mod-buildcraft.com
 * Copyright (c) 2026 Joshua Kac -- NeoForge port (BuildCraft 10)
 *
 * The BuildCraft API is distributed under the terms of the MIT License. Please check the contents of the
 * license, which should be located as "LICENSE.API" in the BuildCraft source code distribution.
 */
package buildcraft.api.core;

import net.minecraftforge.fluids.FluidStack;

/**
 * The fluid counterpart of {@link IStackFilter}.
 *
 * <p>Unlike the 26.x copy, this still tests a {@link FluidStack}: 1.20.1 predates the {@code transfer} resource
 * API, so there is no separate "what the fluid is" type to filter on.
 */
@FunctionalInterface
public interface IFluidFilter {

    boolean matches(FluidStack fluid);

    default IFluidFilter and(IFluidFilter filter) {
        IFluidFilter before = this;
        return fluid -> before.matches(fluid) && filter.matches(fluid);
    }
}
