/*
 * Copyright (c) 2026 Joshua Kac -- NeoForge port (BuildCraft 10)
 *
 * The BuildCraft API is distributed under the terms of the MIT License. Please check the contents of the
 * license, which should be located as "LICENSE.API" in the BuildCraft source code distribution.
 */
package buildcraft.api.statements;

/**
 * Marker interface that designates a class as being an action. Note that you <em>must</em> implement ONE of the
 * following to be recognised as an action: {@link IActionInternal}, {@link IActionInternalSided}, or
 * {@link IActionExternal}.
 */
public interface IAction extends IStatement {
}
