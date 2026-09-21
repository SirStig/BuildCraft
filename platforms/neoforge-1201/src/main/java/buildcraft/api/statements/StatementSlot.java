/*
 * Copyright (c) 2011-2015, SpaceToad and the BuildCraft Team http://www.mod-buildcraft.com
 * Copyright (c) 2026 Joshua Kac -- NeoForge port (BuildCraft 10)
 *
 * The BuildCraft API is distributed under the terms of the MIT License. Please check the contents of the
 * license, which should be located as "LICENSE.API" in the BuildCraft source code distribution.
 */
package buildcraft.api.statements;

import java.util.Arrays;
import java.util.Objects;

import buildcraft.api.core.EnumPipePart;

/** One statement as configured in a gate: the statement itself, its parameters, and which pipe face it is on. */
public class StatementSlot {
    public IStatement statement;
    public IStatementParameter[] parameters;
    public EnumPipePart part = EnumPipePart.CENTER;

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof StatementSlot other)) {
            return false;
        }
        if (other.statement != statement || parameters.length != other.parameters.length) {
            return false;
        }
        return Arrays.equals(parameters, other.parameters);
    }

    @Override
    public int hashCode() {
        return Objects.hash(statement, Arrays.deepHashCode(parameters));
    }
}
