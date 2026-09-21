/*
 * Copyright (c) 2026 Joshua Kac -- NeoForge port (BuildCraft 10)
 *
 * The BuildCraft API is distributed under the terms of the MIT License. Please check the contents of the
 * license, which should be located as "LICENSE.API" in the BuildCraft source code distribution.
 */
package buildcraft.api.statements;

/** An action that can ask to fire only on the tick it becomes active, rather than every tick. */
public interface IActionSingle extends IAction {
    /** @return True if this action should only be fired for the first tick of it being active. */
    boolean singleActionTick();
}
