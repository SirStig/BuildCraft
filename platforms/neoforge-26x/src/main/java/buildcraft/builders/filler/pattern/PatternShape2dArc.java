/*
 * Copyright (c) 2026 Joshua Kac -- NeoForge port (BuildCraft 10)
 *
 * This file is part of the BuildCraft 10 port and is distributed under the terms of the MIT License.
 * Please check the contents of the license, which should be located as "LICENSE.PORT" in the BuildCraft
 * source code distribution.
 */
package buildcraft.builders.filler.pattern;

/** Ported verbatim (algorithm-wise) from 1.12.2's {@code PatternShape2dArc}: a quarter-ellipse arc from one
 * corner to the opposite one. */
public class PatternShape2dArc extends PatternShape2d {
    public PatternShape2dArc() {
        super("2d_arc");
    }

    @Override
    protected void genShape(int maxA, int maxB, LineList list) {
        if (maxA == 0 || maxB == 0) {
            list.moveTo(0, 0);
            list.lineTo(maxA, maxB);
            return;
        }
        list.setFillPoint(maxA, maxB);
        list.arc(maxA, maxB, maxA, maxB);
    }
}
