/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * Copyright (c) 2026 Joshua Kac -- NeoForge port (BuildCraft 10)
 *
 * The BuildCraft API is distributed under the terms of the MIT License. Please check the contents of the
 * license, which should be located as "LICENSE.API" in the BuildCraft source code distribution.
 */
package buildcraft.api.fuels;

import org.jetbrains.annotations.Nullable;

/** Holds the fuel and coolant managers, which the energy module fills in on startup. */
public final class BuildcraftFuelRegistry {

    @Nullable
    public static IFuelManager fuel;

    @Nullable
    public static ICoolantManager coolant;

    private BuildcraftFuelRegistry() {
    }
}
