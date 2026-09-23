/*
 * Copyright (c) 2026 Joshua Kac -- NeoForge port (BuildCraft 10)
 *
 * This file is part of the BuildCraft 10 port and is distributed under the terms of the MIT License.
 * Please check the contents of the license, which should be located as "LICENSE.PORT" in the BuildCraft
 * source code distribution.
 */
package buildcraft.factory.block;

import java.util.EnumMap;
import java.util.Map;

import net.minecraft.core.Direction;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * The real per-facing Chute shape -- until now {@link BlockChute} had no {@code getShape}/
 * {@code getCollisionShape} override, so a chute (a hopper-like funnel in the real model, already ported as
 * seven real elements: a full-width top box plus six progressively narrower rings tapering to a small opening)
 * collided as a full cube. Not a per-ring cutout -- a two-box approximation (the full-width top box, plus one
 * box spanning the funnel's overall footprint) -- but real, not a full cube: a hopper-style block you can now
 * actually reach an arm through, matching the model's own actual shape far more closely.
 *
 * <p>Rotated the same way {@code blockstates/chute.json} rotates the model: {@code UP} unrotated, then
 * {@code x=180} for {@code DOWN}, {@code x=90} for {@code NORTH}, {@code x=90,y=180} for {@code SOUTH},
 * {@code x=90,y=90} for {@code EAST}, {@code x=90,y=270} for {@code WEST} -- confirmed by reading that
 * blockstate file, not assumed.
 */
public final class ChuteShapes {

    private static final Map<Direction, VoxelShape> SHAPES = new EnumMap<>(Direction.class);

    static {
        SHAPES.put(Direction.UP, union(box(0, 9, 0, 16, 16, 16), box(4, 3, 4, 12, 9, 12)));
        SHAPES.put(Direction.DOWN, union(box(0, 0, 0, 16, 7, 16), box(4, 7, 4, 12, 13, 12)));
        SHAPES.put(Direction.NORTH, union(box(0, 0, 9, 16, 16, 16), box(4, 4, 3, 12, 12, 9)));
        SHAPES.put(Direction.SOUTH, union(box(0, 0, 0, 16, 16, 7), box(4, 4, 7, 12, 12, 13)));
        SHAPES.put(Direction.EAST, union(box(9, 0, 0, 16, 16, 16), box(3, 4, 4, 9, 12, 12)));
        SHAPES.put(Direction.WEST, union(box(0, 0, 0, 7, 16, 16), box(7, 4, 4, 13, 12, 12)));
    }

    private ChuteShapes() {}

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
