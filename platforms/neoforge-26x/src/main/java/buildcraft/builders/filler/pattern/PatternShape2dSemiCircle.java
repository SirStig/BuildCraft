/*
 * Copyright (c) 2026 Joshua Kac -- NeoForge port (BuildCraft 10)
 *
 * This file is part of the BuildCraft 10 port and is distributed under the terms of the MIT License.
 * Please check the contents of the license, which should be located as "LICENSE.PORT" in the BuildCraft
 * source code distribution.
 */
package buildcraft.builders.filler.pattern;

/** Ported verbatim (algorithm-wise) from 1.12.2's {@code PatternShape2dSemiCircle}: half an ellipse, flat edge
 * along {@code b = maxB}. */
public class PatternShape2dSemiCircle extends PatternShape2d {
    public PatternShape2dSemiCircle() {
        super("2d_semi_circle");
    }

    @Override
    protected void genShape(int maxA, int maxB, LineList list) {
        if (maxA == 0 || maxB == 0) {
            list.moveTo(0, 0);
            list.lineTo(maxA, maxB);
            return;
        }
        int halfA = maxA / 2;
        int halfAUpper = maxA - halfA;
        list.setFillPoint(halfA, maxB);
        list.arc(halfA, maxB, maxA / 2.0, maxB, halfAUpper - halfA, 0, ArcType.SEMI_CIRCLE);
    }
}
