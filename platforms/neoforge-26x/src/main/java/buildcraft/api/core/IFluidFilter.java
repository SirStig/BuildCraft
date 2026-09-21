/*
 * Copyright (c) 2011-2015, SpaceToad and the BuildCraft Team http://www.mod-buildcraft.com
 *
 * The BuildCraft API is distributed under the terms of the MIT License. Please check the contents of the license, which
 * should be located as "LICENSE.API" in the BuildCraft source code distribution.
 */
package buildcraft.api.core;

import net.neoforged.neoforge.transfer.fluid.FluidResource;

/**
 * The fluid counterpart of {@link IStackFilter}.
 *
 * <p>This tests a {@link FluidResource} rather than a {@code FluidStack}. NeoForge 26.x splits "what the fluid is"
 * from "how much of it there is": a {@code FluidResource} is the former, and amounts are plain longs held by the
 * handler. A filter only ever cared about the former, so it now takes exactly that.
 */
@FunctionalInterface
public interface IFluidFilter {

    boolean matches(FluidResource fluid);

    default IFluidFilter and(IFluidFilter filter) {
        IFluidFilter before = this;
        return fluid -> before.matches(fluid) && filter.matches(fluid);
    }
}
