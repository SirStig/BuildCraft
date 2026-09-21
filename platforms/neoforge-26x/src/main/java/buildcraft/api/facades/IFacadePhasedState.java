/*
 * Copyright (c) 2026 Joshua Kac -- NeoForge port (BuildCraft 10)
 *
 * The BuildCraft API is distributed under the terms of the MIT License. Please check the contents of the
 * license, which should be located as "LICENSE.API" in the BuildCraft source code distribution.
 */
package buildcraft.api.facades;

import org.jetbrains.annotations.Nullable;

import net.minecraft.world.item.DyeColor;

/** One state of a phased facade, and the wire colour that selects it. */
public interface IFacadePhasedState {
    IFacadeState getState();

    /** @return The wire colour this state is shown for, or null for the default state. */
    @Nullable
    DyeColor getActiveColor();
}
