/*
 * Copyright (c) 2011-2015, SpaceToad and the BuildCraft Team http://www.mod-buildcraft.com
 * Copyright (c) 2026 Joshua Kac -- NeoForge port (BuildCraft 10)
 *
 * The BuildCraft API is distributed under the terms of the MIT License. Please check the contents of the
 * license, which should be located as "LICENSE.API" in the BuildCraft source code distribution.
 */
package buildcraft.api.mj;

/**
 * Implemented by any block entity that wants to receive power from BuildCraft lasers.
 *
 * <p>The respective block MUST implement {@link ILaserTargetBlock}.
 */
public interface ILaserTarget {

    /** @return The amount of power required, or 0 if no power is required. */
    long getRequiredLaserPower();

    /**
     * Transfers power from the laser to the target.
     *
     * @param microJoules The number of micro Minecraft Joules to accept.
     * @return The excess power. If the input is less than or equal to {@link #getRequiredLaserPower()} then
     *         this returns 0.
     */
    long receiveLaserPower(long microJoules);

    /** @return True if this is no longer a valid target object -- for example, if it has been invalidated. */
    boolean isInvalidTarget();
}
