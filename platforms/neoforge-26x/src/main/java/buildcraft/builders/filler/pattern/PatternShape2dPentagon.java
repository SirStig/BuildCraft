/*
 * Copyright (c) 2026 Joshua Kac -- NeoForge port (BuildCraft 10)
 *
 * This file is part of the BuildCraft 10 port and is distributed under the terms of the MIT License.
 * Please check the contents of the license, which should be located as "LICENSE.PORT" in the BuildCraft
 * source code distribution.
 */
package buildcraft.builders.filler.pattern;

/** Ported verbatim (algorithm-wise) from 1.12.2's {@code PatternShape2dPentagon}, including its regular-pentagon
 * trigonometry constants. */
public class PatternShape2dPentagon extends PatternShape2d {
    private static final double DIST_HORIZONTAL = StrictMath.sin(Math.toRadians(108 - 90));
    private static final double DIST_VERTICAL;

    static {
        double cos54 = StrictMath.cos(Math.toRadians(108 / 2));
        double cos18 = StrictMath.cos(Math.toRadians(108 - 90));
        DIST_VERTICAL = cos54 / cos18;
    }

    public PatternShape2dPentagon() {
        super("2d_pentagon");
    }

    @Override
    protected void genShape(int maxA, int maxB, LineList list) {
        int halfA = maxA / 2;
        int indentA = (int) Math.round(maxA * DIST_HORIZONTAL);
        int indentB = (int) Math.round(maxB * DIST_VERTICAL);
        list.moveTo(indentA, 0);
        list.lineTo(maxA - indentA, 0);
        list.lineFrom(maxA, indentB);
        list.lineTo(maxA - halfA, maxB);
        list.moveTo(halfA, maxB);
        list.lineFrom(0, indentB);
        list.lineTo(indentA, 0);
        list.setFillPoint(halfA, maxB / 2);
    }
}
