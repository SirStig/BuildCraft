/*
 * Copyright (c) 2026 Joshua Kac -- NeoForge port (BuildCraft 10)
 *
 * This file is part of the BuildCraft 10 port and is distributed under the terms of the MIT License.
 * Please check the contents of the license, which should be located as "LICENSE.PORT" in the BuildCraft
 * source code distribution.
 */
package buildcraft.builders.filler.pattern;

/** Ported verbatim (algorithm-wise) from 1.12.2's {@code PatternShape2dSquare}: the rectangle bounding the whole
 * slice. */
public class PatternShape2dSquare extends PatternShape2d {
    public PatternShape2dSquare() {
        super("2d_square");
    }

    /** No rotation parameter, matching the original -- a square is rotationally symmetric, so cycling it would
     * do nothing. */
    @Override
    public int paramCount() {
        return 2;
    }

    @Override
    protected void genShape(int maxA, int maxB, LineList list) {
        list.lineTo(maxA, 0);
        list.lineTo(maxA, maxB);
        list.lineTo(0, maxB);
        list.lineTo(0, 0);
        list.setFillPoint(maxA / 2, maxB / 2);
    }
}
