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

/** Ported verbatim from 1.12.2's {@code PatternBox}: the six faces of the claimed box, nothing in between. */
public class PatternBox extends FillerPattern {
    public PatternBox() {
        super("box");
    }

    @Override
    public void fill(FilledArea area, int[] params) {
        area.setPlaneYZ(0, true);
        area.setPlaneYZ(area.maxX(), true);
        area.setPlaneXZ(0, true);
        area.setPlaneXZ(area.maxY(), true);
        area.setPlaneXY(0, true);
        area.setPlaneXY(area.maxZ(), true);
    }
}
