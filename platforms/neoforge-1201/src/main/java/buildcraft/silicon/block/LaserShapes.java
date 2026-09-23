/*
 * Copyright (c) 2026 Joshua Kac -- NeoForge port (BuildCraft 10)
 *
 * This file is part of the BuildCraft 10 port and is distributed under the terms of the MIT License.
 * Please check the contents of the license, which should be located as "LICENSE.PORT" in the BuildCraft
 * source code distribution.
 */
package buildcraft.silicon.block;

import java.util.EnumMap;
import java.util.Map;

import net.minecraft.core.Direction;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * The real per-facing collision/outline shape for {@code BlockLaser}. See the 26.x copy of this class for the
 * full derivation from {@code buildcraft_resources/assets/buildcraftsilicon/models/block/laser.json}'s own
 * geometry and {@code blockstates/laser.json}'s own rotation set -- identical on both targets.
 */
public final class LaserShapes {

    private static final Map<Direction, VoxelShape> SHAPES = new EnumMap<>(Direction.class);

    static {
        SHAPES.put(Direction.UP, union(box(0, 0, 0, 16, 4, 16), box(5, 4, 5, 11, 13, 11)));
        SHAPES.put(Direction.DOWN, union(box(0, 12, 0, 16, 16, 16), box(5, 3, 5, 11, 12, 11)));
        SHAPES.put(Direction.NORTH, union(box(0, 0, 12, 16, 16, 16), box(5, 5, 3, 11, 11, 12)));
        SHAPES.put(Direction.SOUTH, union(box(0, 0, 0, 16, 16, 4), box(5, 5, 4, 11, 11, 13)));
        SHAPES.put(Direction.EAST, union(box(0, 0, 0, 4, 16, 16), box(4, 5, 5, 13, 11, 11)));
        SHAPES.put(Direction.WEST, union(box(12, 0, 0, 16, 16, 16), box(3, 5, 5, 12, 11, 11)));
    }

    private LaserShapes() {}

    public static VoxelShape get(Direction facing) {
        return SHAPES.get(facing);
    }

    private static VoxelShape union(VoxelShape a, VoxelShape b) {
        return Shapes.join(a, b, BooleanOp.OR);
    }

    private static VoxelShape box(double x1, double y1, double z1, double x2, double y2, double z2) {
        return Shapes.box(x1 / 16, y1 / 16, z1 / 16, x2 / 16, y2 / 16, z2 / 16);
    }
}
