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
 * Ported from 1.12.2's {@code PatternPyramid}. Parameter 0 is the old {@code PatternParameterYDir} (0 = up,
 * 1 = down); parameter 1 is the old {@code PatternParameterCenter}'s nine {@code offsetX}/{@code offsetZ} pairs,
 * in the same order as that enum's {@code POSSIBLE_ORDER} (0 = {@code CENTER}, matching the original default).
 */
public class PatternPyramid extends FillerPattern {
    /** {@code offsetX}, {@code offsetZ} per value, matching {@code PatternParameterCenter.POSSIBLE_ORDER}. */
    private static final int[][] CENTER_OFFSETS = {
        { 0, 0 }, // CENTER
        { -1, -1 }, // NORTH_WEST
        { 0, -1 }, // NORTH
        { 1, -1 }, // NORTH_EAST
        { 1, 0 }, // EAST
        { 1, 1 }, // SOUTH_EAST
        { 0, 1 }, // SOUTH
        { -1, 1 }, // SOUTH_WEST
        { -1, 0 }, // WEST
    };

    public PatternPyramid() {
        super("pyramid");
    }

    @Override
    public int paramCount() {
        return 2;
    }

    @Override
    public int paramValueCount(int index) {
        return index == 0 ? 2 : CENTER_OFFSETS.length;
    }

    @Override
    public String paramLabelKey(int index, int value) {
        return index == 0
            ? "buildcraft.gui.filler.param.direction." + (value == 0 ? "up" : "down")
            : "buildcraft.gui.filler.param.center." + value;
    }

    @Override
    public void fill(FilledArea area, int[] params) {
        boolean up = params.length < 1 || params[0] == 0;
        int centerIndex = params.length < 2 ? 0 : Math.floorMod(params[1], CENTER_OFFSETS.length);
        int offsetX = CENTER_OFFSETS[centerIndex][0];
        int offsetZ = CENTER_OFFSETS[centerIndex][1];

        int xLowerDiff = offsetX >= 0 ? 1 : 0;
        int xUpperDiff = offsetX <= 0 ? -1 : 0;
        int zLowerDiff = offsetZ >= 0 ? 1 : 0;
        int zUpperDiff = offsetZ <= 0 ? -1 : 0;

        int stepY = up ? 1 : -1;
        int y = up ? 0 : area.maxY();

        int xLower = 0;
        int xUpper = area.maxX();
        int zLower = 0;
        int zUpper = area.maxZ();

        while (y >= 0 && y <= area.maxY()) {
            area.setAreaXZ(xLower, xUpper, y, zLower, zUpper, true);

            xLower += xLowerDiff;
            xUpper += xUpperDiff;
            zLower += zLowerDiff;
            zUpper += zUpperDiff;
            y += stepY;

            if (xLower > xUpper || zLower > zUpper) {
                break;
            }
        }
    }
}
