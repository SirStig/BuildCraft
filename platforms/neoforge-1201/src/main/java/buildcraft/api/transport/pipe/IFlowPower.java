/*
 * Copyright (c) 2026 Joshua Kac -- NeoForge port (BuildCraft 10)
 *
 * The BuildCraft API is distributed under the terms of the MIT License. Please check the contents of the
 * license, which should be located as "LICENSE.API" in the BuildCraft source code distribution.
 */
package buildcraft.api.transport.pipe;

import net.minecraft.core.Direction;

import buildcraft.api.mj.IMjPassiveProvider;

/** A pipe flow that carries MJ. */
public interface IFlowPower extends IFlowPowerLike {
    /** Makes this pipe reconfigure itself, possibly due to the addition of new modules. */
    @Override
    void reconfigure();

    /**
     * Attempts to extract power from the {@link IMjPassiveProvider} connected to this pipe on the given side.
     *
     * @param maxPower The maximum amount of power that can be extracted.
     * @param from The side of this pipe to take power from.
     * @return The amount of power extracted.
     */
    long tryExtractPower(long maxPower, Direction from);
}
