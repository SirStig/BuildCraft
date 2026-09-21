/*
 * Copyright (c) 2011-2015, SpaceToad and the BuildCraft Team http://www.mod-buildcraft.com
 * Copyright (c) 2026 Joshua Kac -- NeoForge port (BuildCraft 10)
 *
 * The BuildCraft API is distributed under the terms of the MIT License. Please check the contents of the
 * license, which should be located as "LICENSE.API" in the BuildCraft source code distribution.
 */
package buildcraft.api.statements;

import java.util.List;

import org.jetbrains.annotations.Nullable;

/** Implemented by a block entity that wants to replace, rather than add to, the statements gates see on it. */
public interface IOverrideDefaultStatements {
    /** @return The triggers to use instead of the default set, or null to keep the default. */
    @Nullable
    List<ITriggerExternal> overrideTriggers();

    /** @return The actions to use instead of the default set, or null to keep the default. */
    @Nullable
    List<IActionExternal> overrideActions();
}
