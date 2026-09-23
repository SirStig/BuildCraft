/*
 * Copyright (c) 2026 Joshua Kac -- NeoForge port (BuildCraft 10)
 *
 * This file is part of the BuildCraft 10 port and is distributed under the terms of the MIT License.
 * Please check the contents of the license, which should be located as "LICENSE.PORT" in the BuildCraft
 * source code distribution.
 */
package buildcraft.builders.filler.pattern;

/** Ported verbatim (algorithm-wise) from 1.12.2's {@code PatternShape2dHexagon}. Like the original, this never
 * calls {@link LineList#setFillPoint}, so the hollow/filled parameter has no effect on this shape -- only the
 * outline is ever drawn, matching 8.0.1's own behaviour exactly. */
public class PatternShape2dHexagon extends PatternShape2d {
    public PatternShape2dHexagon() {
        super("2d_hexagon");
    }

    @Override
    protected void genShape(int maxA, int maxB, LineList list) {
        int indent = maxA / 4;
        int halfB = maxB / 2;
        list.moveTo(indent, 0);
        list.lineTo(maxA - indent, 0);
        list.lineFrom(maxA, halfB);
        list.moveTo(maxA, maxB - halfB);
        list.lineTo(maxA - indent, maxB);
        list.lineFrom(indent, maxB);
        list.lineFrom(0, maxB - halfB);
        list.moveTo(0, halfB);
        list.lineTo(indent, 0);
    }
}
