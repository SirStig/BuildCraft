/*
 * Copyright (c) 2026 Joshua Kac -- NeoForge port (BuildCraft 10)
 *
 * This file is part of the BuildCraft 10 port and is distributed under the terms of the MIT License.
 * Please check the contents of the license, which should be located as "LICENSE.PORT" in the BuildCraft
 * source code distribution.
 */
package buildcraft.builders.filler.pattern;

import java.util.EnumSet;
import java.util.Locale;
import java.util.Set;

import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Axis;
import net.minecraft.world.phys.Vec3;

import buildcraft.builders.filler.FilledArea;
import buildcraft.builders.filler.FillerPattern;
import buildcraft.lib.misc.VecUtil;

/**
 * Ported from 1.12.2's {@code PatternSpherePart}, one class covering all three of the original's variants
 * (eighth/quarter/half sphere), selected by {@link Part}. Same trick as the original: push {@link PatternSphere}'s
 * own ellipsoid-in-a-box centre away from each "cut" face and double its radius along that axis, so only the
 * slice of the sphere on the inner side of the cut ever falls inside the box.
 *
 * <p>Parameter 0 is the old {@code PatternParameterHollow}; parameter 1 the old {@code PatternParameterFacing}
 * (which face the sphere is cut against); parameter 2 (eighth/quarter only -- a half-sphere has only one open
 * axis, so rotating around it would be a no-op, exactly as in the original, which is why {@code HALF} drops it)
 * the old {@code PatternParameterRotation}, picking which of the remaining faces the cut also runs against.
 */
public class PatternSpherePart extends FillerPattern {
    public enum Part {
        EIGHTH(3),
        QUARTER(2),
        HALF(1);

        final int openFaces;

        Part(int openFaces) {
            this.openFaces = openFaces;
        }
    }

    private static final int FILLED_INNER = 0;
    private static final int FILLED_OUTER = 1;
    private static final int HOLLOW = 2;

    /** Matches {@code PatternParameterFacing}'s own enum order exactly (down/up/north/south/west/east), so
     * parameter 1's ordinal can be used as an index here with no remapping. */
    private static final Direction[] FACINGS =
        { Direction.DOWN, Direction.UP, Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST };
    private static final String[] FACING_NAMES = { "down", "up", "north", "south", "west", "east" };

    private final Part part;

    public PatternSpherePart(Part part) {
        super("sphere_" + part.name().toLowerCase(Locale.ROOT));
        this.part = part;
    }

    @Override
    public int paramCount() {
        return part.openFaces == 1 ? 2 : 3;
    }

    @Override
    public int paramValueCount(int index) {
        return index == 0 ? 3 : index == 1 ? FACINGS.length : 4;
    }

    @Override
    public String paramLabelKey(int index, int value) {
        if (index == 0) {
            return switch (value) {
                case FILLED_OUTER -> "buildcraft.gui.filler.param.filled_outer";
                case HOLLOW -> "buildcraft.gui.filler.param.hollow";
                default -> "buildcraft.gui.filler.param.filled_inner";
            };
        }
        if (index == 1) {
            return "buildcraft.gui.filler.param.direction." + FACING_NAMES[Math.floorMod(value, FACING_NAMES.length)];
        }
        return switch (value) {
            case 1 -> "buildcraft.gui.filler.param.rotation.quarter";
            case 2 -> "buildcraft.gui.filler.param.rotation.half";
            case 3 -> "buildcraft.gui.filler.param.rotation.three_quarters";
            default -> "buildcraft.gui.filler.param.rotation.none";
        };
    }

    @Override
    public void fill(FilledArea area, int[] params) {
        int hollow = params.length > 0 ? Math.floorMod(params[0], 3) : FILLED_INNER;
        Direction facing = params.length > 1 ? FACINGS[Math.floorMod(params[1], FACINGS.length)] : Direction.DOWN;
        int rotationCount = paramCount() > 2 && params.length > 2 ? Math.floorMod(params[2], 4) : 0;

        Set<Direction> innerSides = EnumSet.noneOf(Direction.class);

        Vec3 max = new Vec3(area.maxX(), area.maxY(), area.maxZ());
        Vec3 center = VecUtil.scale(max, 0.5);
        Vec3 radius = center.add(0.5, 0.5, 0.5);

        innerSides.add(facing);

        Axis axis = facing.getAxis();
        Vec3 offset = VecUtil.offset(Vec3.ZERO, facing, VecUtil.getValue(radius, axis));
        center = center.add(offset);
        radius = VecUtil.replaceValue(radius, axis, VecUtil.getValue(radius, axis) * 2);

        if (part.openFaces > 1) {
            Axis secondaryAxis = rotationOffsetAxis(axis, rotationCount);
            Direction secondaryFace = VecUtil.getFacing(secondaryAxis, rotationCount >= 2);
            innerSides.add(secondaryFace);

            offset = VecUtil.offset(Vec3.ZERO, secondaryFace, VecUtil.getValue(radius, secondaryAxis));
            center = center.add(offset);
            radius = VecUtil.replaceValue(radius, secondaryAxis, VecUtil.getValue(radius, secondaryAxis) * 2);

            if (part.openFaces > 2) {
                int tertiaryRotation = (rotationCount + 1) & 3;
                Axis tertiaryAxis = rotationOffsetAxis(axis, tertiaryRotation);
                Direction tertiaryFace = VecUtil.getFacing(tertiaryAxis, tertiaryRotation >= 2);
                innerSides.add(tertiaryFace);

                offset = VecUtil.offset(Vec3.ZERO, tertiaryFace, VecUtil.getValue(radius, tertiaryAxis));
                center = center.add(offset);
                radius = VecUtil.replaceValue(radius, tertiaryAxis, VecUtil.getValue(radius, tertiaryAxis) * 2);
            }
        }

        double cx = center.x, cy = center.y, cz = center.z;
        double rx = radius.x, ry = radius.y, rz = radius.z;

        int maxX = area.maxX();
        int maxY = area.maxY();
        int maxZ = area.maxZ();
        int sizeY = maxY + 1;
        int sizeZ = maxZ + 1;

        boolean doShell = hollow != FILLED_INNER;
        boolean[] inside = doShell ? new boolean[(maxX + 1) * sizeY * sizeZ] : null;

        for (int x = 0; x <= maxX; x++) {
            double dx = Math.abs(x - cx) / rx;
            double dxx = dx * dx;
            for (int y = 0; y <= maxY; y++) {
                double dy = Math.abs(y - cy) / ry;
                double dyy = dy * dy;
                for (int z = 0; z <= maxZ; z++) {
                    double dz = Math.abs(z - cz) / rz;
                    double dzz = dz * dz;
                    if (dxx + dyy + dzz < 1) {
                        if (doShell) {
                            inside[(x * sizeY + y) * sizeZ + z] = true;
                        } else {
                            area.set(x, y, z, true);
                        }
                    }
                }
            }
        }

        if (!doShell) {
            return;
        }
        boolean outerFilled = hollow == FILLED_OUTER;

        // Z iteration
        for (int x = 0; x <= maxX; x++) {
            for (int y = 0; y <= maxY; y++) {
                if (!innerSides.contains(Direction.NORTH)) {
                    for (int z = 0; z <= maxZ; z++) {
                        if (inside[(x * sizeY + y) * sizeZ + z]) {
                            area.set(x, y, z, true);
                            break;
                        }
                        if (outerFilled) {
                            area.set(x, y, z, true);
                        }
                    }
                }
                if (!innerSides.contains(Direction.SOUTH)) {
                    for (int z = maxZ; z >= 0; z--) {
                        if (inside[(x * sizeY + y) * sizeZ + z]) {
                            area.set(x, y, z, true);
                            break;
                        }
                        if (outerFilled) {
                            area.set(x, y, z, true);
                        }
                    }
                }
            }
        }

        // Y iteration
        for (int x = 0; x <= maxX; x++) {
            for (int z = 0; z <= maxZ; z++) {
                if (!innerSides.contains(Direction.DOWN)) {
                    for (int y = 0; y <= maxY; y++) {
                        if (inside[(x * sizeY + y) * sizeZ + z]) {
                            area.set(x, y, z, true);
                            break;
                        }
                        if (outerFilled) {
                            area.set(x, y, z, true);
                        }
                    }
                }
                if (!innerSides.contains(Direction.UP)) {
                    for (int y = maxY; y >= 0; y--) {
                        if (inside[(x * sizeY + y) * sizeZ + z]) {
                            area.set(x, y, z, true);
                            break;
                        }
                        if (outerFilled) {
                            area.set(x, y, z, true);
                        }
                    }
                }
            }
        }

        // X iteration
        for (int y = 0; y <= maxY; y++) {
            for (int z = 0; z <= maxZ; z++) {
                if (!innerSides.contains(Direction.WEST)) {
                    for (int x = 0; x <= maxX; x++) {
                        if (inside[(x * sizeY + y) * sizeZ + z]) {
                            area.set(x, y, z, true);
                            break;
                        }
                        if (outerFilled) {
                            area.set(x, y, z, true);
                        }
                    }
                }
                if (!innerSides.contains(Direction.EAST)) {
                    for (int x = maxX; x >= 0; x--) {
                        if (inside[(x * sizeY + y) * sizeZ + z]) {
                            area.set(x, y, z, true);
                            break;
                        }
                        if (outerFilled) {
                            area.set(x, y, z, true);
                        }
                    }
                }
            }
        }
    }

    /** Ported from 1.12.2's inline switch: the axis perpendicular to {@code axis}, alternating which of the two
     * remaining axes is picked as {@code rotationCount} increases. */
    private static Axis rotationOffsetAxis(Axis axis, int rotationCount) {
        if (rotationCount % 2 == 1) {
            return axis == Axis.X ? Axis.Y : axis == Axis.Y ? Axis.Z : Axis.X;
        }
        return axis == Axis.X ? Axis.Z : axis == Axis.Y ? Axis.X : Axis.Y;
    }
}
