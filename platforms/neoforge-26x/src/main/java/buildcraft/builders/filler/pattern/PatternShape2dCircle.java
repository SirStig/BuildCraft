/*
 * Copyright (c) 2026 Joshua Kac -- NeoForge port (BuildCraft 10)
 *
 * This file is part of the BuildCraft 10 port and is distributed under the terms of the MIT License.
 * Please check the contents of the license, which should be located as "LICENSE.PORT" in the BuildCraft
 * source code distribution.
 */
package buildcraft.builders.filler.pattern;

/** Ported verbatim (algorithm-wise) from 1.12.2's {@code PatternShape2dCircle}: a full ellipse centred on the
 * slice. */
public class PatternShape2dCircle extends PatternShape2d {
    public PatternShape2dCircle() {
        super("2d_circle");
    }

    @Override
    protected void genShape(int maxA, int maxB, LineList list) {
        if (maxA == 0 || maxB == 0) {
            list.moveTo(0, 0);
            list.lineTo(maxA, maxB);
            return;
        }
        int halfA = maxA / 2;
        int halfB = maxB / 2;
        int halfAUpper = maxA - halfA;
        int halfBUpper = maxB - halfB;
        list.setFillPoint(halfA, halfB);
        list.arc(halfA, halfB, maxA / 2.0, maxB / 2.0, halfAUpper - halfA, halfBUpper - halfB, ArcType.FULL_CIRCLE);
    }
}
