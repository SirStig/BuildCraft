/*
 * Copyright (c) 2026 Joshua Kac -- NeoForge port (BuildCraft 10)
 *
 * This file is part of the BuildCraft 10 port and is distributed under the terms of the MIT License.
 * Please check the contents of the license, which should be located as "LICENSE.PORT" in the BuildCraft
 * source code distribution.
 */
package buildcraft.builders.filler.pattern;

/** Ported from 1.12.2's {@code PatternShape2dOctagon} -- whose {@code genShape} body was already empty in the
 * original 8.0.1 release (an unfinished pattern shipped as-is: it draws nothing and fills nothing). Kept empty
 * here too, to stay faithful to what the original actually did rather than "fixing" a shape this port has no
 * spec for. */
public class PatternShape2dOctagon extends PatternShape2d {
    public PatternShape2dOctagon() {
        super("2d_octagon");
    }

    /** No rotation parameter, matching the original -- see {@link PatternShape2dSquare} for why. */
    @Override
    public int paramCount() {
        return 2;
    }

    @Override
    protected void genShape(int maxA, int maxB, LineList list) {
        // Intentionally empty -- see class javadoc.
    }
}
