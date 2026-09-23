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

/**
 * Ported from 1.12.2's {@code PatternStairs}. Parameter 0 is the old {@code PatternParameterYDir} (0 = up,
 * 1 = down); parameter 1 is the old {@code PatternParameterXZDir}'s four horizontal directions (0 = east,
 * matching the original default, then south/west/north).
 */
public class PatternStairs extends FillerPattern {
    /** {@code stepX}, {@code stepZ} per value -- east, south, west, north. */
    private static final int[][] XZ_STEPS = { { 1, 0 }, { 0, 1 }, { -1, 0 }, { 0, -1 } };
    private static final String[] XZ_NAMES = { "east", "south", "west", "north" };

    public PatternStairs() {
        super("stairs");
    }

    @Override
    public int paramCount() {
        return 2;
    }

    @Override
    public int paramValueCount(int index) {
        return index == 0 ? 2 : XZ_STEPS.length;
    }

    @Override
    public String paramLabelKey(int index, int value) {
        return index == 0
            ? "buildcraft.gui.filler.param.direction." + (value == 0 ? "up" : "down")
            : "buildcraft.gui.filler.param.direction." + XZ_NAMES[Math.floorMod(value, XZ_NAMES.length)];
    }

    @Override
    public void fill(FilledArea area, int[] params) {
        boolean up = params.length < 1 || params[0] == 0;
        int dirIndex = params.length < 2 ? 0 : Math.floorMod(params[1], XZ_STEPS.length);
        int stepX = XZ_STEPS[dirIndex][0];
        int stepZ = XZ_STEPS[dirIndex][1];

        int y = up ? 0 : area.maxY();
        final int yStep = up ? 1 : -1;
        final int yEnd = up ? area.maxY() + 1 : -1;

        int fx = 0;
        int fz = 0;
        int tx = area.maxX();
        int tz = area.maxZ();

        while (y != yEnd) {
            area.setAreaXZ(fx, tx, y, fz, tz, true);

            fx += stepX > 0 ? 1 : 0;
            fz += stepZ > 0 ? 1 : 0;
            tx -= stepX < 0 ? 1 : 0;
            tz -= stepZ < 0 ? 1 : 0;
            y += yStep;

            if (fx > tx || fz > tz) {
                break;
            }
        }
    }
}
