/*
 * Copyright (c) 2026 Joshua Kac -- NeoForge port (BuildCraft 10)
 *
 * This file is part of the BuildCraft 10 port and is distributed under the terms of the MIT License.
 * Please check the contents of the license, which should be located as "LICENSE.PORT" in the BuildCraft
 * source code distribution.
 */
package buildcraft.builders.filler.pattern;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import buildcraft.builders.filler.FilledArea;
import buildcraft.builders.filler.FillerPattern;
import buildcraft.lib.misc.PositionUtil;
import buildcraft.lib.misc.PositionUtil.PathIterator2d;

/**
 * Base for every "outline traced across one 2D slice of the box, then optionally flood-filled" pattern, ported
 * from 1.12.2's {@code PatternShape2d}. Concrete subclasses only implement {@link #genShape}, which draws the
 * shape's outline with {@link LineList} in local {@code (a, b)} space; {@link #fill} repeats that outline over
 * every layer along the chosen axis, applies the rotation parameter, and flood-fills the interior if asked.
 *
 * <p>The original pulled its line/arc walker from {@code PositionUtil.forAllOnPath2d} -- by the time this pass
 * started that had already been ported verbatim (identical on both platforms) as part of unrelated earlier work,
 * so this class only needed to adapt {@code IFilledTemplate} calls to {@link FilledArea} ones, and
 * {@code IStatementParameter} enum params to the plain {@code int} params {@link FillerPattern} uses. Parameter
 * indices match the original enums' own ordinals exactly (axis 0/1/2 = X/Y/Z, hollow 0/1/2 =
 * filled-inner/filled-outer/hollow, rotation 0/1/2/3 = none/quarter/half/three-quarters), so no remapping table is
 * needed anywhere else in this class.
 */
public abstract class PatternShape2d extends FillerPattern {
    private static final int AXIS_X = 0;
    private static final int AXIS_Y = 1;
    private static final int AXIS_Z = 2;

    private static final int FILLED_INNER = 0;
    private static final int FILLED_OUTER = 1;
    private static final int HOLLOW = 2;

    private static final int ROT_NONE = 0;
    private static final int ROT_QUARTER = 1;
    private static final int ROT_HALF = 2;
    private static final int ROT_THREE_QUARTERS = 3;

    protected PatternShape2d(String id) {
        super(id);
    }

    /** 3 parameters by default (axis, hollow, rotation) -- {@link PatternShape2dSquare} and
     * {@link PatternShape2dOctagon} drop the rotation parameter, exactly like the original, since both shapes are
     * rotationally symmetric. */
    @Override
    public int paramCount() {
        return 3;
    }

    @Override
    public int defaultParam(int index) {
        return index == 0 ? AXIS_Y : index == 1 ? HOLLOW : ROT_NONE;
    }

    @Override
    public int paramValueCount(int index) {
        return index == 2 ? 4 : 3;
    }

    @Override
    public String paramLabelKey(int index, int value) {
        if (index == 0) {
            return "buildcraft.gui.filler.param.axis." + (value == AXIS_X ? "x" : value == AXIS_Z ? "z" : "y");
        }
        if (index == 1) {
            return switch (value) {
                case FILLED_OUTER -> "buildcraft.gui.filler.param.filled_outer";
                case HOLLOW -> "buildcraft.gui.filler.param.hollow";
                default -> "buildcraft.gui.filler.param.filled_inner";
            };
        }
        return switch (value) {
            case ROT_QUARTER -> "buildcraft.gui.filler.param.rotation.quarter";
            case ROT_HALF -> "buildcraft.gui.filler.param.rotation.half";
            case ROT_THREE_QUARTERS -> "buildcraft.gui.filler.param.rotation.three_quarters";
            default -> "buildcraft.gui.filler.param.rotation.none";
        };
    }

    @Override
    public final void fill(FilledArea area, int[] params) {
        int axis = params.length > 0 ? Math.floorMod(params[0], 3) : AXIS_Y;
        int hollow = params.length > 1 ? Math.floorMod(params[1], 3) : HOLLOW;
        int rotation = paramCount() > 2 && params.length > 2 ? Math.floorMod(params[2], 4) : ROT_NONE;

        PathIterator2d iterator = getIterator(area, axis);

        int maxA = axis == AXIS_X ? area.maxY() : area.maxX();
        int maxB = axis == AXIS_Z ? area.maxY() : area.maxZ();

        int normMaxA = maxA;
        int normMaxB = maxB;

        if (rotation % 2 == 1) {
            int maxT = maxA;
            maxA = maxB;
            maxB = maxT;
            final int max_b = maxB;
            final PathIterator2d old = iterator;
            iterator = (a, b) -> old.iterate(max_b - b, a);
        }
        if (rotation > 1) {
            final PathIterator2d old = iterator;
            final int max_a = maxA;
            final int max_b = maxB;
            iterator = (a, b) -> old.iterate(max_a - a, max_b - b);
        }

        LineList list = new LineList(iterator);
        genShape(maxA, maxB, list);

        if (hollow != HOLLOW) {
            int fillA = list.fillInA;
            int fillB = list.fillInB;
            if (fillA != -1 && fillB != -1) {
                maxA = normMaxA;
                maxB = normMaxB;

                if (rotation % 2 == 1) {
                    int fillT = fillA;
                    fillA = maxB - fillB;
                    fillB = fillT;
                }
                if (rotation > 1) {
                    fillA = maxA - fillA;
                    fillB = maxB - fillB;
                }
                PathIterator2d fillIterator = getIterator(area, axis);
                PositionGetter getter = getFillGetter(area, axis);

                boolean outerFilled = hollow == FILLED_OUTER;
                PathIterator2d expandIterator = outerFilled ? (a, b) -> {} : fillIterator;

                // Flood-fill outwards from the known-inside point, same 4-neighbour expansion as the original.
                Set<Point> visited = new HashSet<>();
                List<Point> open = new ArrayList<>();
                open.add(new Point(fillA, fillB));
                while (!open.isEmpty()) {
                    List<Point> next = new ArrayList<>();
                    for (Point p : open) {
                        if (p.a < 0 || p.a > maxA) {
                            continue;
                        }
                        if (p.b < 0 || p.b > maxB) {
                            continue;
                        }
                        if (!visited.add(p)) {
                            continue;
                        }
                        if (getter.isFilled(p.a, p.b)) {
                            continue;
                        }
                        expandIterator.iterate(p.a, p.b);
                        next.add(new Point(p.a + 1, p.b));
                        next.add(new Point(p.a - 1, p.b));
                        next.add(new Point(p.a, p.b + 1));
                        next.add(new Point(p.a, p.b - 1));
                    }
                    open = next;
                }

                if (outerFilled) {
                    // "Filled outer" means everything the flood-fill couldn't reach (i.e. outside the outline).
                    for (int a = 0; a <= maxA; a++) {
                        for (int b = 0; b <= maxB; b++) {
                            if (!visited.contains(new Point(a, b))) {
                                fillIterator.iterate(a, b);
                            }
                        }
                    }
                }
            }
        }
    }

    private static PathIterator2d getIterator(FilledArea area, int axis) {
        if (axis == AXIS_X) {
            return (y, z) -> area.setLineX(0, area.maxX(), y, z, true);
        }
        if (axis == AXIS_Z) {
            return (x, y) -> area.setLineZ(x, y, 0, area.maxZ(), true);
        }
        return (x, z) -> area.setLineY(x, 0, area.maxY(), z, true);
    }

    private static PositionGetter getFillGetter(FilledArea area, int axis) {
        if (axis == AXIS_X) {
            return (a, b) -> area.get(0, a, b);
        }
        if (axis == AXIS_Z) {
            return (a, b) -> area.get(a, b, 0);
        }
        return (a, b) -> area.get(a, 0, b);
    }

    /** Draws this shape's outline into {@code list}, in local {@code (a, b)} space {@code 0..maxA, 0..maxB} (one
     * axis's own two remaining axes, already adjusted for the axis/rotation parameters by {@link #fill}). Calls
     * {@link LineList#setFillPoint} with a point known to be inside the outline, for every shape that supports
     * the hollow/filled parameter ({@link PatternShape2dHexagon} does not, matching the original). */
    protected abstract void genShape(int maxA, int maxB, LineList list);

    @FunctionalInterface
    interface PositionGetter {
        boolean isFilled(int a, int b);
    }

    private static final class Point {
        final int a, b;

        Point(int a, int b) {
            this.a = a;
            this.b = b;
        }

        @Override
        public int hashCode() {
            return 31 * a + b;
        }

        @Override
        public boolean equals(Object obj) {
            return obj instanceof Point other && other.a == a && other.b == b;
        }
    }

    /** Ported from 1.12.2's {@code PatternShape2d.LineList}: a tiny turtle-graphics helper built on
     * {@link PositionUtil#forAllOnPath2d} for straight-edged shapes, plus {@link #arc} (a Bresenham ellipse walk)
     * for curved ones. */
    public static final class LineList {
        private final PathIterator2d iterator;
        private int lastA, lastB;
        private int fillInA = -1, fillInB = -1;

        LineList(PathIterator2d iterator) {
            this.iterator = iterator;
        }

        public void setFillPoint(int a, int b) {
            fillInA = a;
            fillInB = b;
        }

        public void moveTo(int a, int b) {
            this.lastA = a;
            this.lastB = b;
        }

        public void lineTo(int a, int b) {
            PositionUtil.forAllOnPath2d(lastA, lastB, a, b, iterator);
            moveTo(a, b);
        }

        public void lineFrom(int a, int b) {
            int a2 = lastA;
            int b2 = lastB;
            moveTo(a, b);
            lineTo(a2, b2);
            moveTo(a, b);
        }

        public void arc(int ca, int cb, double ra, double rb) {
            arc(ca, cb, ra, rb, 0, 0, ArcType.ARC);
        }

        public void arc(int ca, int cb, double ra, double rb, int da, int db, ArcType type) {
            if (ra <= 0) {
                throw new IllegalArgumentException("'ra' was less than or equal to 0! (Was " + ra + ")");
            }
            if (rb <= 0) {
                throw new IllegalArgumentException("'rb' was less than or equal to 0! (Was " + rb + ")");
            }
            double ra2 = ra * ra;
            double rb2 = rb * rb;

            double sigma = 2 * rb2 + ra2 * (1 - 2 * rb);
            for (int a = 0, b = (int) rb; rb2 * a <= ra2 * b; a++) {
                iterator.iterate(ca - a, cb - b);
                if (type.second) {
                    iterator.iterate(ca + a + da, cb - b);
                    if (type.all) {
                        iterator.iterate(ca - a, cb + b + db);
                        iterator.iterate(ca + a + da, cb + b + db);
                    }
                }
                if (sigma >= 0) {
                    sigma += 4 * ra2 * (1 - b);
                    b--;
                }
                sigma += rb2 * ((4 * a) + 6);
            }

            sigma = 2 * ra2 + rb2 * (1 - 2 * ra);
            for (int a = (int) ra, b = 0; ra2 * b <= rb2 * a; b++) {
                iterator.iterate(ca - a, cb - b);
                if (type.second) {
                    iterator.iterate(ca + a + da, cb - b);
                    if (type.all) {
                        iterator.iterate(ca - a, cb + b + db);
                        iterator.iterate(ca + a + da, cb + b + db);
                    }
                }
                if (sigma >= 0) {
                    sigma += 4 * rb2 * (1 - a);
                    a--;
                }
                sigma += ra2 * ((4 * b) + 6);
            }
        }
    }

    public enum ArcType {
        ARC(false, false),
        SEMI_CIRCLE(true, false),
        FULL_CIRCLE(true, true);

        final boolean second, all;

        ArcType(boolean second, boolean all) {
            this.second = second;
            this.all = all;
        }
    }
}
