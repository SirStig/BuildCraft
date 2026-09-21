/*
 * Copyright (c) 2026 Joshua Kac -- NeoForge port (BuildCraft 10)
 *
 * The BuildCraft API is distributed under the terms of the MIT License. Please check the contents of the
 * license, which should be located as "LICENSE.API" in the BuildCraft source code distribution.
 */
package buildcraft.api.facades;

/** A facade as held by an item or attached to a pipe. */
public interface IFacade {
    FacadeType getType();

    boolean isHollow();

    IFacadePhasedState[] getPhasedStates();
}
