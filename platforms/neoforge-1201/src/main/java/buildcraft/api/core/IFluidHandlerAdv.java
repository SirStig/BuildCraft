/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * The BuildCraft API is distributed under the terms of the MIT License. Please check the contents of the license, which
 * should be located as "LICENSE.API" in the BuildCraft source code distribution.
 */
package buildcraft.api.core;

import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;

/**
 * A version of {@link IFluidHandler} that can drain whichever fluid a filter accepts.
 *
 * <p>This has no 26.x counterpart. 26.x's {@code ResourceHandler} exposes the resource held in each slot, so
 * filtered extraction can be done from outside against any handler -- see {@code FluidFilters} in the 26.x tree.
 * 1.20.1's {@link IFluidHandler} has {@code getFluidInTank}, but no transaction scoping, so BuildCraft keeps the
 * original interface-based approach here.
 *
 * <p>The one change from 1.12.2 is that {@code boolean doDrain} became {@link IFluidHandler.FluidAction}, which is
 * how 1.20.1's own {@code IFluidHandler} spells the same thing, and that an empty {@link FluidStack} is returned
 * rather than null.
 */
public interface IFluidHandlerAdv extends IFluidHandler {
    /**
     * Drains fluid out of internal tanks; distribution is left entirely to the handler.
     *
     * @param filter Filters the fluids that may be extracted.
     * @param maxDrain The maximum amount of fluid to drain.
     * @param action Whether to actually drain, or only simulate.
     * @return The fluid and amount that was (or would have been) drained, or {@link FluidStack#EMPTY}.
     */
    FluidStack drain(IFluidFilter filter, int maxDrain, FluidAction action);
}
