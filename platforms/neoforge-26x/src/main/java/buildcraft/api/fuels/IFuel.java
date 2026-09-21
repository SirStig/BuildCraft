/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * Copyright (c) 2026 Joshua Kac -- NeoForge port (BuildCraft 10)
 *
 * The BuildCraft API is distributed under the terms of the MIT License. Please check the contents of the
 * license, which should be located as "LICENSE.API" in the BuildCraft source code distribution.
 */
package buildcraft.api.fuels;

import net.neoforged.neoforge.transfer.fluid.FluidResource;

/** A fluid a combustion engine can burn. */
public interface IFuel {
    /**
     * @return The input fluid.
     *
     *         <p>1.12.2 returned a {@code FluidStack} and documented that its amount was ignored. 26.x has a type
     *         for exactly that -- {@link FluidResource} is a fluid without an amount -- so the contract is now in
     *         the signature rather than in a comment.
     */
    FluidResource getFluid();

    /** @return The number of ticks that a single bucket (1000mB) of this fuel will burn for. */
    int getTotalBurningTime();

    /** @return The amount, in micro MJ, of power that this fuel will give off in 1 tick. */
    long getPowerPerCycle();
}
