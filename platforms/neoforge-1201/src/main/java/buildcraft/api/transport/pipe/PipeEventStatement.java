/*
 * Copyright (c) 2026 Joshua Kac -- NeoForge port (BuildCraft 10)
 *
 * The BuildCraft API is distributed under the terms of the MIT License. Please check the contents of the
 * license, which should be located as "LICENSE.API" in the BuildCraft source code distribution.
 */
package buildcraft.api.transport.pipe;

import java.util.Collection;

import org.jetbrains.annotations.NotNull;

import net.minecraft.core.Direction;

import buildcraft.api.statements.IActionInternal;
import buildcraft.api.statements.IActionInternalSided;
import buildcraft.api.statements.ITriggerInternal;
import buildcraft.api.statements.ITriggerInternalSided;

public abstract class PipeEventStatement extends PipeEvent {
    public PipeEventStatement(IPipeHolder holder) {
        super(holder);
    }

    /** Fired when a pipe should add internal triggers to the list of all possible triggers */
    public static class AddTriggerInternal extends PipeEventStatement {
        public final Collection<ITriggerInternal> triggers;

        public AddTriggerInternal(IPipeHolder holder, Collection<ITriggerInternal> triggers) {
            super(holder);
            this.triggers = triggers;
        }
    }

    /** Fired when a pipe should add internal sided triggers to the list of all possible triggers */
    public static class AddTriggerInternalSided extends PipeEventStatement {
        public final Collection<ITriggerInternalSided> triggers;

        @NotNull
        public final Direction side;

        public AddTriggerInternalSided(IPipeHolder holder, Collection<ITriggerInternalSided> triggers, @NotNull Direction side) {
            super(holder);
            this.triggers = triggers;
            this.side = side;
        }
    }

    /** Fired when a pipe should add internal actions to the list of all possible actions */
    public static class AddActionInternal extends PipeEventStatement {
        public final Collection<IActionInternal> actions;

        public AddActionInternal(IPipeHolder holder, Collection<IActionInternal> actions) {
            super(holder);
            this.actions = actions;
        }
    }

    /** Fired when a pipe should add internal actions to the list of all possible actions */
    public static class AddActionInternalSided extends PipeEventStatement {
        public final Collection<IActionInternalSided> actions;

        @NotNull
        public final Direction side;

        public AddActionInternalSided(IPipeHolder holder, Collection<IActionInternalSided> actions, @NotNull Direction side) {
            super(holder);
            this.actions = actions;
            this.side = side;
        }
    }
}
