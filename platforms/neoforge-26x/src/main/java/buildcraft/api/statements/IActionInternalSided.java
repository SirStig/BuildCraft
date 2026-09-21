/*
 * Copyright (c) 2026 Joshua Kac -- NeoForge port (BuildCraft 10)
 *
 * The BuildCraft API is distributed under the terms of the MIT License. Please check the contents of the
 * license, which should be located as "LICENSE.API" in the BuildCraft source code distribution.
 */
package buildcraft.api.statements;

import net.minecraft.core.Direction;

/** An action that acts on the container holding it, for one particular side of it. */
public interface IActionInternalSided extends IAction {
    void actionActivate(Direction side, IStatementContainer source, IStatementParameter[] parameters);
}
