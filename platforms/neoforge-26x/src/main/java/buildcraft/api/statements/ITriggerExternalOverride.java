/*
 * Copyright (c) 2026 Joshua Kac -- NeoForge port (BuildCraft 10)
 *
 * The BuildCraft API is distributed under the terms of the MIT License. Please check the contents of the
 * license, which should be located as "LICENSE.API" in the BuildCraft source code distribution.
 */
package buildcraft.api.statements;

import net.minecraft.core.Direction;

/**
 * Implemented by block entities that want to override external trigger behaviour.
 *
 * <p>Please use wisely.
 */
public interface ITriggerExternalOverride {
    enum Result {
        TRUE,
        FALSE,
        IGNORE
    }

    Result override(
        Direction side,
        IStatementContainer source,
        ITriggerExternal trigger,
        IStatementParameter[] parameters
    );
}
