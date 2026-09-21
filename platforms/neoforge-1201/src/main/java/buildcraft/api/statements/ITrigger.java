/*
 * Copyright (c) 2026 Joshua Kac -- NeoForge port (BuildCraft 10)
 *
 * The BuildCraft API is distributed under the terms of the MIT License. Please check the contents of the
 * license, which should be located as "LICENSE.API" in the BuildCraft source code distribution.
 */
package buildcraft.api.statements;

/**
 * Marker interface that designates a class as being a trigger. Note that you <em>must</em> implement ONE of the
 * following to be recognised as a trigger: {@link ITriggerInternal}, {@link ITriggerInternalSided}, or
 * {@link ITriggerExternal}.
 */
public interface ITrigger extends IStatement {
}
