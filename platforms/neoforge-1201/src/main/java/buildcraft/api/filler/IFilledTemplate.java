/*
 * Copyright (c) 2026 Joshua Kac -- NeoForge port (BuildCraft 10)
 *
 * The BuildCraft API is distributed under the terms of the MIT License. Please check the contents of the
 * license, which should be located as "LICENSE.API" in the BuildCraft source code distribution.
 */
package buildcraft.api.filler;

import net.minecraft.core.BlockPos;

/**
 * A cuboid of booleans: which positions within a filler's box the pattern wants filled.
 *
 * <p>Use the interface's own methods as much as possible -- an implementation can optimise them, and the bulk
 * setters below exist so a pattern does not have to loop a position at a time.
 *
 * <p>{@code getMax()} used {@code subtract(new BlockPos(1, 1, 1))}; {@link BlockPos} no longer allocates for that,
 * so it is {@code offset(-1, -1, -1)}.
 */
public interface IFilledTemplate {
    BlockPos getSize();

    default BlockPos getMax() {
        return getSize().offset(-1, -1, -1);
    }

    boolean get(int x, int y, int z);

    void set(int x, int y, int z, boolean value);

    default void setLineX(int fromX, int toX, int y, int z, boolean value) {
        for (int x = fromX; x <= toX; x++) {
            set(x, y, z, value);
        }
    }

    default void setLineY(int x, int fromY, int toY, int z, boolean value) {
        for (int y = fromY; y <= toY; y++) {
            set(x, y, z, value);
        }
    }

    default void setLineZ(int x, int y, int fromZ, int toZ, boolean value) {
        for (int z = fromZ; z <= toZ; z++) {
            set(x, y, z, value);
        }
    }

    default void setAreaYZ(int x, int fromY, int toY, int fromZ, int toZ, boolean value) {
        for (int y = fromY; y <= toY; y++) {
            for (int z = fromZ; z <= toZ; z++) {
                set(x, y, z, value);
            }
        }
    }

    default void setAreaXZ(int fromX, int toX, int y, int fromZ, int toZ, boolean value) {
        for (int x = fromX; x <= toX; x++) {
            for (int z = fromZ; z <= toZ; z++) {
                set(x, y, z, value);
            }
        }
    }

    default void setAreaXY(int fromX, int toX, int fromY, int toY, int z, boolean value) {
        for (int y = fromY; y <= toY; y++) {
            for (int x = fromX; x <= toX; x++) {
                set(x, y, z, value);
            }
        }
    }

    default void setPlaneYZ(int x, boolean value) {
        setAreaYZ(x, 0, getMax().getY(), 0, getMax().getZ(), value);
    }

    default void setPlaneXZ(int y, boolean value) {
        setAreaXZ(0, getMax().getX(), y, 0, getMax().getZ(), value);
    }

    default void setPlaneXY(int z, boolean value) {
        setAreaXY(0, getMax().getX(), 0, getMax().getY(), z, value);
    }

    default void setAll(boolean value) {
        BlockPos size = getSize();
        for (int z = 0; z < size.getZ(); z++) {
            for (int y = 0; y < size.getY(); y++) {
                for (int x = 0; x < size.getX(); x++) {
                    set(x, y, z, value);
                }
            }
        }
    }
}
