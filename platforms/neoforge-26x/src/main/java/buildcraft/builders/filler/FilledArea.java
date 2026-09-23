/*
 * Copyright (c) 2026 Joshua Kac -- NeoForge port (BuildCraft 10)
 *
 * This file is part of the BuildCraft 10 port and is distributed under the terms of the MIT License.
 * Please check the contents of the license, which should be located as "LICENSE.PORT" in the BuildCraft
 * source code distribution.
 */
package buildcraft.builders.filler;

/**
 * A flat {@code boolean[]} grid over one claimed {@link buildcraft.lib.misc.data.Box}, in the box's own local
 * coordinates ({@code 0..sizeX-1, 0..sizeY-1, 0..sizeZ-1}). Every {@code true} cell is a position
 * {@link TileFiller} wants to hold a block; every {@code false} cell is one it wants left empty.
 *
 * <p>This is this port's stand-in for 1.12.2's {@code IFilledTemplate} (implemented there by
 * {@code Template.FilledTemplate}, a {@code BitSet} wrapped around the much larger snapshot {@code Template}/
 * {@code TemplateBuilder}/{@code SnapshotBuilder} machinery that also backs the Blueprint builder). That whole
 * subsystem is not ported yet -- it is a separate, large body of work on its own, out of scope for this pass --
 * so each {@link FillerPattern} below writes straight into this plain grid instead of an {@code IFilledTemplate},
 * with the same method names/semantics {@code IFilledTemplate} used ({@code setPlaneXY} etc.) so the pattern
 * math ported from {@code buildcraft.builders.snapshot.pattern.Pattern*} needed the least possible adjustment.
 */
public final class FilledArea {
    public final int sizeX, sizeY, sizeZ;
    private final boolean[] data;

    public FilledArea(int sizeX, int sizeY, int sizeZ) {
        this.sizeX = Math.max(1, sizeX);
        this.sizeY = Math.max(1, sizeY);
        this.sizeZ = Math.max(1, sizeZ);
        this.data = new boolean[this.sizeX * this.sizeY * this.sizeZ];
    }

    public int maxX() {
        return sizeX - 1;
    }

    public int maxY() {
        return sizeY - 1;
    }

    public int maxZ() {
        return sizeZ - 1;
    }

    private int index(int x, int y, int z) {
        return (x * sizeY + y) * sizeZ + z;
    }

    private boolean inBounds(int x, int y, int z) {
        return x >= 0 && x < sizeX && y >= 0 && y < sizeY && z >= 0 && z < sizeZ;
    }

    public boolean get(int x, int y, int z) {
        return inBounds(x, y, z) && data[index(x, y, z)];
    }

    public void set(int x, int y, int z, boolean value) {
        if (inBounds(x, y, z)) {
            data[index(x, y, z)] = value;
        }
    }

    public void setAll(boolean value) {
        java.util.Arrays.fill(data, value);
    }

    public void setPlaneYZ(int x, boolean value) {
        for (int y = 0; y < sizeY; y++) {
            for (int z = 0; z < sizeZ; z++) {
                set(x, y, z, value);
            }
        }
    }

    public void setPlaneXZ(int y, boolean value) {
        for (int x = 0; x < sizeX; x++) {
            for (int z = 0; z < sizeZ; z++) {
                set(x, y, z, value);
            }
        }
    }

    public void setPlaneXY(int z, boolean value) {
        for (int x = 0; x < sizeX; x++) {
            for (int y = 0; y < sizeY; y++) {
                set(x, y, z, value);
            }
        }
    }

    public void setLineX(int x0, int x1, int y, int z, boolean value) {
        for (int x = x0; x <= x1; x++) {
            set(x, y, z, value);
        }
    }

    public void setLineY(int x, int y0, int y1, int z, boolean value) {
        for (int y = y0; y <= y1; y++) {
            set(x, y, z, value);
        }
    }

    public void setLineZ(int x, int y, int z0, int z1, boolean value) {
        for (int z = z0; z <= z1; z++) {
            set(x, y, z, value);
        }
    }

    public void setAreaXZ(int x0, int x1, int y, int z0, int z1, boolean value) {
        for (int x = x0; x <= x1; x++) {
            for (int z = z0; z <= z1; z++) {
                set(x, y, z, value);
            }
        }
    }
}
