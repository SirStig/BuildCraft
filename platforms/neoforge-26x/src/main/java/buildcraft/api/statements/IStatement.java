/*
 * Copyright (c) 2011-2015, SpaceToad and the BuildCraft Team http://www.mod-buildcraft.com
 * Copyright (c) 2026 Joshua Kac -- NeoForge port (BuildCraft 10)
 *
 * The BuildCraft API is distributed under the terms of the MIT License. Please check the contents of the
 * license, which should be located as "LICENSE.API" in the BuildCraft source code distribution.
 */
package buildcraft.api.statements;

import org.jetbrains.annotations.Nullable;

/**
 * Designates some sort of statement. Most of the time you should implement {@link ITriggerExternal},
 * {@link ITriggerInternal}, {@link IActionExternal} or {@link IActionInternal} instead.
 */
public interface IStatement extends IGuiSlot {

    /** @return The maximum number of parameters this statement can have, 0 if none. */
    int maxParameters();

    /** @return The minimum number of parameters this statement can have, 0 if none. */
    int minParameters();

    /** Creates a parameter for the statement. */
    @Nullable
    IStatementParameter createParameter(int index);

    /**
     * Creates a parameter for the given index, optionally returning the old parameter if it is still valid. By
     * default this compares the classes of the old and new parameters; it is sensible to override that check when
     * the parameters no longer match. For example if you return {@link StatementParameterItemStack} from
     * {@link #createParameter(int)} and require the stack to match a filter, the incoming stack might not.
     */
    @Nullable
    default IStatementParameter createParameter(@Nullable IStatementParameter old, int index) {
        IStatementParameter created = createParameter(index);
        if (old == null || created == null) {
            return created;
        } else if (old.getClass() == created.getClass()) {
            return old;
        }
        return created;
    }

    /** @return This statement after a left rotation. Used in particular in blueprint orientation. */
    IStatement rotateLeft();

    /**
     * @return A group of related statements. For example "redstone signal input" should probably return an array of
     *         "RS_SIGNAL_ON" and "RS_SIGNAL_OFF". It is recommended to return an array containing this object.
     */
    IStatement[] getPossible();

    default boolean isPossibleOrdered() {
        return false;
    }
}
