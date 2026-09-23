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

/** Ported from 1.12.2's {@code PatternFill}: every cell in the claimed box must hold a block. */
public class PatternFill extends FillerPattern {
    public PatternFill() {
        super("fill");
    }

    @Override
    public void fill(FilledArea area, int[] params) {
        area.setAll(true);
    }
}
