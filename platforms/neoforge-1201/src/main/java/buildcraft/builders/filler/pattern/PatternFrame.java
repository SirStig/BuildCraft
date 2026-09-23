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

/** Ported verbatim from 1.12.2's {@code PatternFrame}: the twelve edges of the claimed box. */
public class PatternFrame extends FillerPattern {
    public PatternFrame() {
        super("frame");
    }

    @Override
    public void fill(FilledArea area, int[] params) {
        int maxX = area.maxX();
        int maxY = area.maxY();
        int maxZ = area.maxZ();

        area.setLineX(0, maxX, 0, 0, true);
        area.setLineX(0, maxX, maxY, 0, true);
        area.setLineX(0, maxX, maxY, maxZ, true);
        area.setLineX(0, maxX, 0, maxZ, true);

        area.setLineY(0, 0, maxY, 0, true);
        area.setLineY(maxX, 0, maxY, 0, true);
        area.setLineY(maxX, 0, maxY, maxZ, true);
        area.setLineY(0, 0, maxY, maxZ, true);

        area.setLineZ(0, 0, 0, maxZ, true);
        area.setLineZ(maxX, 0, 0, maxZ, true);
        area.setLineZ(maxX, maxY, 0, maxZ, true);
        area.setLineZ(0, maxY, 0, maxZ, true);
    }
}
