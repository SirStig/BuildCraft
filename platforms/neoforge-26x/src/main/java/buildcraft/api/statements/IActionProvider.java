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

/** Contributes actions to the gates that can see it. */
public interface IActionProvider {
    void addInternalActions(Collection<IActionInternal> actions, IStatementContainer container);

    void addInternalSidedActions(
        Collection<IActionInternalSided> actions,
        IStatementContainer container,
        @NotNull Direction side
    );

    void addExternalActions(Collection<IActionExternal> actions, @NotNull Direction side, BlockEntity tile);
}
