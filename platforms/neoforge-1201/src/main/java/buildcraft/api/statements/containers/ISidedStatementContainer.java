/*
 * Copyright (c) 2026 Joshua Kac -- NeoForge port (BuildCraft 10)
 *
 * The BuildCraft API is distributed under the terms of the MIT License. Please check the contents of the
 * license, which should be located as "LICENSE.API" in the BuildCraft source code distribution.
 */
package buildcraft.api.statements.containers;

import net.minecraft.core.Direction;

import buildcraft.api.statements.IStatementContainer;

/** A statement container that sits on one face of a block, such as a gate on a pipe. */
public interface ISidedStatementContainer extends IStatementContainer {
    Direction getSide();
}
