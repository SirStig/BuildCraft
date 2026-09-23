/*
 * Copyright (c) 2026 Joshua Kac -- NeoForge port (BuildCraft 10)
 *
 * This file is part of the BuildCraft 10 port and is distributed under the terms of the MIT License.
 * Please check the contents of the license, which should be located as "LICENSE.PORT" in the BuildCraft
 * source code distribution.
 */
package buildcraft.lib.block;

import java.util.EnumMap;
import java.util.Map;

import net.minecraft.core.Direction;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * The real per-facing collision/outline shape for every engine block (Wood/Stone/Iron/RF/Creative), all of which
 * share {@code buildcraftlib:models/block/engine_base}'s geometry: an 8-thick mounting slab flush against the
 * powered machine, and a 12-long, 8x8 trunk protruding from the opposite (output) face. Every engine block model
 * in this port was, until now, a placeholder full cube with no shape override at all -- Minecraft's default
 * {@code Shapes.block()} full-cube collision/outline, which visibly disagreed with the real shaped model once one
 * existed. This is the fix: a real shape per facing, derived by rotating the model's own native "facing up"
 * geometry (base 0,0,0-16,8,16; trunk 4,4,4-12,16,12, matching {@code engine_base.json}'s elements at rest,
 * {@code progress=0}) by the same 90-degree-multiple rotations the blockstate JSON applies to the model itself, so
 * the collision box and the visible model always agree.
 */
public final class EngineShapes {

    private static final Map<Direction, VoxelShape> SHAPES = new EnumMap<>(Direction.class);

    static {
        SHAPES.put(Direction.UP, union(box(0, 0, 0, 16, 8, 16), box(4, 4, 4, 12, 16, 12)));
        SHAPES.put(Direction.DOWN, union(box(0, 8, 0, 16, 16, 16), box(4, 0, 4, 12, 12, 12)));
        SHAPES.put(Direction.NORTH, union(box(0, 0, 8, 16, 16, 16), box(4, 4, 0, 12, 12, 12)));
        SHAPES.put(Direction.SOUTH, union(box(0, 0, 0, 16, 16, 8), box(4, 4, 4, 12, 12, 16)));
        SHAPES.put(Direction.EAST, union(box(0, 0, 0, 8, 16, 16), box(4, 4, 4, 16, 12, 12)));
        SHAPES.put(Direction.WEST, union(box(8, 0, 0, 16, 16, 16), box(0, 4, 4, 12, 12, 12)));
    }

    private EngineShapes() {}

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
