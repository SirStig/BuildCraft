/*
 * Copyright (c) 2026 Joshua Kac -- NeoForge port (BuildCraft 10)
 *
 * This file is part of the BuildCraft 10 port and is distributed under the terms of the MIT License.
 * Please check the contents of the license, which should be located as "LICENSE.PORT" in the BuildCraft
 * source code distribution.
 */
package buildcraft.builders.filler.pattern;

import buildcraft.builders.filler.FilledArea;
import buildcraft.builders.filler.FillerPattern;

/**
 * Ported from 1.12.2's {@code PatternSphere}. Parameter 0 is the old {@code PatternParameterHollow}
 * (0 = filled inner, matching the original default; 1 = filled outer; 2 = hollow). The "filled inner" case marks
 * every cell inside the ellipsoid directly, exactly as before; the other two cases first mark the ellipsoid into
 * a scratch grid (1.12.2's local {@code BitSet}, keyed the same way {@link FilledArea} itself is), then walk each
 * axis from both ends to keep only the outer shell (optionally also filling everything outside it, for "filled
 * outer").
 */
public class PatternSphere extends FillerPattern {
    private static final int FILLED_INNER = 0;
    private static final int FILLED_OUTER = 1;
    private static final int HOLLOW = 2;

    public PatternSphere() {
        super("sphere");
    }

    @Override
    public int paramCount() {
        return 1;
    }

    @Override
    public int paramValueCount(int index) {
        return 3;
    }

    @Override
    public String paramLabelKey(int index, int value) {
        return switch (value) {
            case FILLED_OUTER -> "buildcraft.gui.filler.param.filled_outer";
            case HOLLOW -> "buildcraft.gui.filler.param.hollow";
            default -> "buildcraft.gui.filler.param.filled_inner";
        };
    }

    @Override
    public void fill(FilledArea area, int[] params) {
        int hollow = params.length < 1 ? FILLED_INNER : Math.floorMod(params[0], 3);

        int maxX = area.maxX();
        int maxY = area.maxY();
        int maxZ = area.maxZ();
        int sizeY = maxY + 1;
        int sizeZ = maxZ + 1;

        double cx = maxX / 2.0;
        double cy = maxY / 2.0;
        double cz = maxZ / 2.0;
        double rx = cx + 0.5;
        double ry = cy + 0.5;
        double rz = cz + 0.5;

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
                for (int z = 0; z <= maxZ; z++) {
                    if (inside[(x * sizeY + y) * sizeZ + z]) {
                        area.set(x, y, z, true);
                        break;
                    }
                    if (outerFilled) {
                        area.set(x, y, z, true);
                    }
                }
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

        // Y iteration
        for (int x = 0; x <= maxX; x++) {
            for (int z = 0; z <= maxZ; z++) {
                for (int y = 0; y <= maxY; y++) {
                    if (inside[(x * sizeY + y) * sizeZ + z]) {
                        area.set(x, y, z, true);
                        break;
                    }
                    if (outerFilled) {
                        area.set(x, y, z, true);
                    }
                }
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

        // X iteration
        for (int y = 0; y <= maxY; y++) {
            for (int z = 0; z <= maxZ; z++) {
                for (int x = 0; x <= maxX; x++) {
                    if (inside[(x * sizeY + y) * sizeZ + z]) {
                        area.set(x, y, z, true);
                        break;
                    }
                    if (outerFilled) {
                        area.set(x, y, z, true);
                    }
                }
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
