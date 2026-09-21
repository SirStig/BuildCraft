/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * Copyright (c) 2026 Joshua Kac -- NeoForge port (BuildCraft 10)
 *
 * The BuildCraft API is distributed under the terms of the MIT License. Please check the contents of the
 * license, which should be located as "LICENSE.API" in the BuildCraft source code distribution.
 */
package buildcraft.api;

/** Allows a mod, or an addon, to register networking packets in the message manager. */
public interface IBuildCraftMod {
    /** @return The mod id to use when registering this as a channel. */
    String getModId();
}
