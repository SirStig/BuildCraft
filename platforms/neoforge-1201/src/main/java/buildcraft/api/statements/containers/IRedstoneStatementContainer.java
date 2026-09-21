/*
 * Copyright (c) 2026 Joshua Kac -- NeoForge port (BuildCraft 10)
 *
 * The BuildCraft API is distributed under the terms of the MIT License. Please check the contents of the
 * license, which should be located as "LICENSE.API" in the BuildCraft source code distribution.
 */
package buildcraft.api.statements.containers;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.Direction;

/** A statement container that can read and drive redstone. */
public interface IRedstoneStatementContainer {
    /**
     * Gets the redstone input from a given side.
     *
     * @param side The side; use null for the maximum input across all sides.
     * @return The redstone input, from 0 to 15.
     */
    int getRedstoneInput(@Nullable Direction side);

    /**
     * Sets the redstone output for a given side.
     *
     * @param side The side; use null for all sides.
     * @return Whether the set was successful.
     */
    boolean setRedstoneOutput(@Nullable Direction side, int value);
}
