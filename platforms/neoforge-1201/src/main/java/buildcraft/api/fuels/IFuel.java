/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * Copyright (c) 2026 Joshua Kac -- NeoForge port (BuildCraft 10)
 *
 * The BuildCraft API is distributed under the terms of the MIT License. Please check the contents of the
 * license, which should be located as "LICENSE.API" in the BuildCraft source code distribution.
 */
package buildcraft.api.fuels;

import net.minecraftforge.fluids.FluidStack;

/** A fluid a combustion engine can burn. */
public interface IFuel {
    /**
     * @return The input fluid. The amount is ignored.
     *
     *         <p>The 26.x copy returns a {@code FluidResource}, which is a fluid without an amount and states
     *         that in the type. 1.20.1 has no such type, so the contract stays a comment here.
     */
    FluidStack getFluid();

    /** @return The number of ticks that a single bucket (1000mB) of this fuel will burn for. */
    int getTotalBurningTime();

    /** @return The amount, in micro MJ, of power that this fuel will give off in 1 tick. */
    long getPowerPerCycle();
}
