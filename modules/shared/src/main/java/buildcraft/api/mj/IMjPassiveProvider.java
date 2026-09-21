/*
 * Copyright (c) 2026 Joshua Kac -- NeoForge port (BuildCraft 10)
 *
 * The BuildCraft API is distributed under the terms of the MIT License. Please check the contents of the
 * license, which should be located as "LICENSE.API" in the BuildCraft source code distribution.
 */
package buildcraft.api.mj;

/** Designates a machine that power can be pulled out of, rather than one that pushes power to its neighbours. */
public interface IMjPassiveProvider extends IMjConnector {
    /** Attempts to extract power from this provider.
     *
     * @return Either 0, min, max, or a value between min and max. */
    long extractPower(long min, long max, boolean simulate);
}
