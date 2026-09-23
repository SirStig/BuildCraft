/*
 * Copyright (c) 2026 Joshua Kac -- NeoForge port (BuildCraft 10)
 *
 * This file is part of the BuildCraft 10 port and is distributed under the terms of the MIT License.
 * Please check the contents of the license, which should be located as "LICENSE.PORT" in the BuildCraft
 * source code distribution.
 */
package buildcraft.builders.filler.pattern;

import buildcraft.builders.filler.FilledArea;
import buildcraft.builders.filler.FillerPattern;

/** Ported from 1.12.2's {@code PatternClear}: every cell must be left empty -- combined with
 * {@link buildcraft.builders.tile.TileFiller}'s {@code canExcavate}, this clears the whole claimed box. Leaves
 * {@code area} entirely {@code false}, exactly like the original's empty {@code fillTemplate} body. */
public class PatternClear extends FillerPattern {
    public PatternClear() {
        super("clear");
    }

    @Override
    public void fill(FilledArea area, int[] params) {
        // Nothing to mark filled -- every cell wants to be air.
    }
}
