/*
 * Copyright (c) 2026 Joshua Kac -- NeoForge port (BuildCraft 10)
 *
 * This file is part of the BuildCraft 10 port and is distributed under the terms of the MIT License.
 * Please check the contents of the license, which should be located as "LICENSE.PORT" in the BuildCraft
 * source code distribution.
 */
package buildcraft.builders.filler;

import buildcraft.builders.filler.pattern.PatternBox;
import buildcraft.builders.filler.pattern.PatternClear;
import buildcraft.builders.filler.pattern.PatternFill;
import buildcraft.builders.filler.pattern.PatternFrame;
import buildcraft.builders.filler.pattern.PatternNone;
import buildcraft.builders.filler.pattern.PatternPyramid;
import buildcraft.builders.filler.pattern.PatternSphere;
import buildcraft.builders.filler.pattern.PatternStairs;

/**
 * Every {@link FillerPattern} ported so far, in GUI cycling order. Stands in for 1.12.2's
 * {@code FillerManager.registry}/{@code FillerStatementContext} (the registry every {@code Pattern} subclass
 * added itself to, plus the widget that grouped and sorted them for the drag-and-drop gate GUI) -- neither the
 * registry indirection nor the grouping-by-shape-family is needed for a plain "click to cycle" GUI, so this is
 * just a fixed array.
 *
 * <p><b>Not ported this pass</b> (see {@link TileFiller}'s own javadoc): {@code PatternSpherePart} (eighth/
 * quarter/half sphere -- three more variants of {@link PatternSphere}'s own algorithm, restricted to one octant/
 * quadrant/hemisphere) and {@code PatternShape2d} and its nine concrete 2D-outline subclasses (arc, circle,
 * hexagon, octagon, pentagon, semicircle, square, triangle, plus the base rectangle) -- both pull in
 * {@code PositionUtil.PathIterator2d}/{@code forAllOnPath2d}, a Bresenham-style line/arc walker this port has no
 * equivalent of yet. A follow-up pass can port that walker once one of these shapes is actually needed.
 */
public final class FillerPatterns {
    private FillerPatterns() {}

    public static final FillerPattern NONE = new PatternNone();
    public static final FillerPattern BOX = new PatternBox();
    public static final FillerPattern CLEAR = new PatternClear();
    public static final FillerPattern FILL = new PatternFill();
    public static final FillerPattern FRAME = new PatternFrame();
    public static final FillerPattern PYRAMID = new PatternPyramid();
    public static final FillerPattern SPHERE = new PatternSphere();
    public static final FillerPattern STAIRS = new PatternStairs();

    /** Cycling order for the GUI's pattern button; {@link #NONE} is first, matching 1.12.2's own default. */
    public static final FillerPattern[] VALUES = { NONE, BOX, CLEAR, FILL, FRAME, PYRAMID, SPHERE, STAIRS };

    public static int indexOf(FillerPattern pattern) {
        for (int i = 0; i < VALUES.length; i++) {
            if (VALUES[i] == pattern) {
                return i;
            }
        }
        return 0;
    }

    public static FillerPattern byId(String id) {
        for (FillerPattern pattern : VALUES) {
            if (pattern.id.equals(id)) {
                return pattern;
            }
        }
        return NONE;
    }
}
