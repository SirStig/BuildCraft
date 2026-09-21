/*
 * Copyright (c) 2026 Joshua Kac -- NeoForge port (BuildCraft 10)
 *
 * The BuildCraft API is distributed under the terms of the MIT License. Please check the contents of the
 * license, which should be located as "LICENSE.API" in the BuildCraft source code distribution.
 */
package buildcraft.api.mj;

import org.jetbrains.annotations.NotNull;

/** Signifies that this should visibly connect to other Mj handling entities/tiles. This should NEVER be the block
 * entity, but an encapsulated class that refers back to it. Use {@code MjCapabilities#CONNECTOR} to access this. */
public interface IMjConnector {
    /** Checks to see if this connector can connect to the other connector. By default this should check that the other
     * connector is the same power system. */
    boolean canConnect(@NotNull IMjConnector other);
}
