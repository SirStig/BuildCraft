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
import buildcraft.builders.filler.pattern.PatternShape2dArc;
import buildcraft.builders.filler.pattern.PatternShape2dCircle;
import buildcraft.builders.filler.pattern.PatternShape2dHexagon;
import buildcraft.builders.filler.pattern.PatternShape2dOctagon;
import buildcraft.builders.filler.pattern.PatternShape2dPentagon;
import buildcraft.builders.filler.pattern.PatternShape2dSemiCircle;
import buildcraft.builders.filler.pattern.PatternShape2dSquare;
import buildcraft.builders.filler.pattern.PatternShape2dTriangle;
import buildcraft.builders.filler.pattern.PatternSphere;
import buildcraft.builders.filler.pattern.PatternSpherePart;
import buildcraft.builders.filler.pattern.PatternSpherePart.Part;
import buildcraft.builders.filler.pattern.PatternStairs;

/**
 * Every {@link FillerPattern} ported so far, in GUI cycling order. Stands in for 1.12.2's
 * {@code FillerManager.registry}/{@code FillerStatementContext} (the registry every {@code Pattern} subclass
 * added itself to, plus the widget that grouped and sorted them for the drag-and-drop gate GUI) -- neither the
 * registry indirection nor the grouping-by-shape-family is needed for a plain "click to cycle" GUI, so this is
 * just a fixed array.
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
    public static final FillerPattern SPHERE_EIGHTH = new PatternSpherePart(Part.EIGHTH);
    public static final FillerPattern SPHERE_QUARTER = new PatternSpherePart(Part.QUARTER);
    public static final FillerPattern SPHERE_HALF = new PatternSpherePart(Part.HALF);
    public static final FillerPattern STAIRS = new PatternStairs();
    public static final FillerPattern SHAPE_2D_ARC = new PatternShape2dArc();
    public static final FillerPattern SHAPE_2D_CIRCLE = new PatternShape2dCircle();
    public static final FillerPattern SHAPE_2D_HEXAGON = new PatternShape2dHexagon();
    public static final FillerPattern SHAPE_2D_OCTAGON = new PatternShape2dOctagon();
    public static final FillerPattern SHAPE_2D_PENTAGON = new PatternShape2dPentagon();
    public static final FillerPattern SHAPE_2D_SEMI_CIRCLE = new PatternShape2dSemiCircle();
    public static final FillerPattern SHAPE_2D_SQUARE = new PatternShape2dSquare();
    public static final FillerPattern SHAPE_2D_TRIANGLE = new PatternShape2dTriangle();

    /** Cycling order for the GUI's pattern button; {@link #NONE} is first, matching 1.12.2's own default. */
    public static final FillerPattern[] VALUES = {
        NONE, BOX, CLEAR, FILL, FRAME, PYRAMID, SPHERE, SPHERE_EIGHTH, SPHERE_QUARTER, SPHERE_HALF, STAIRS,
        SHAPE_2D_ARC, SHAPE_2D_CIRCLE, SHAPE_2D_HEXAGON, SHAPE_2D_OCTAGON, SHAPE_2D_PENTAGON, SHAPE_2D_SEMI_CIRCLE,
        SHAPE_2D_SQUARE, SHAPE_2D_TRIANGLE
    };

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
