/*
 * Copyright (c) 2011-2015, SpaceToad and the BuildCraft Team http://www.mod-buildcraft.com
 * Copyright (c) 2026 Joshua Kac -- NeoForge port (BuildCraft 10)
 *
 * The BuildCraft API is distributed under the terms of the MIT License. Please check the contents of the
 * license, which should be located as "LICENSE.API" in the BuildCraft source code distribution.
 */
package buildcraft.api.statements;

import net.minecraft.core.Direction;
import net.minecraft.world.level.block.entity.BlockEntity;

/** A trigger that tests a neighbouring block entity. */
public interface ITriggerExternal extends ITrigger {

    boolean isTriggerActive(
        BlockEntity target,
        Direction side,
        IStatementContainer source,
        IStatementParameter[] parameters
    );
}
