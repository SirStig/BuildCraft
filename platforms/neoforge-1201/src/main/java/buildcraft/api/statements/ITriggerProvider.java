/*
 * Copyright (c) 2011-2015, SpaceToad and the BuildCraft Team http://www.mod-buildcraft.com
 * Copyright (c) 2026 Joshua Kac -- NeoForge port (BuildCraft 10)
 *
 * The BuildCraft API is distributed under the terms of the MIT License. Please check the contents of the
 * license, which should be located as "LICENSE.API" in the BuildCraft source code distribution.
 */
package buildcraft.api.statements;

import java.util.Collection;

import org.jetbrains.annotations.NotNull;

import net.minecraft.core.Direction;
import net.minecraft.world.level.block.entity.BlockEntity;

/** Contributes triggers to the gates that can see it. */
public interface ITriggerProvider {
    void addInternalTriggers(Collection<ITriggerInternal> triggers, IStatementContainer container);

    void addInternalSidedTriggers(
        Collection<ITriggerInternalSided> triggers,
        IStatementContainer container,
        @NotNull Direction side
    );

    /** Adds the triggers available to a gate next to the given block. */
    void addExternalTriggers(Collection<ITriggerExternal> triggers, @NotNull Direction side, BlockEntity tile);
}
