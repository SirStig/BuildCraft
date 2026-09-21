/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * Copyright (c) 2026 Joshua Kac -- NeoForge port (BuildCraft 10)
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL
 * was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.lib.misc;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Axis;
import net.minecraft.core.Direction.AxisDirection;
import net.minecraft.core.Vec3i;
import net.minecraft.world.phys.Vec3;

import org.jetbrains.annotations.NotNull;
import org.joml.Vector3f;

/**
 * Class for dealing with {@link Vec3}, {@link Vec3i}, {@link Direction}, {@link Axis} conversions and additions.
 * This is for simple functions ONLY, {@code PositionUtil} is for complex interactions.
 *
 * <p>Ported from 1.12.2. The renames applied throughout: {@code EnumFacing} -> {@link Direction},
 * {@code Vec3d} -> {@link Vec3}, {@code addVector} -> {@code add}, {@code getFrontOffsetX} ->
 * {@code getStepX}, and {@code javax.vecmath} -> JOML, which is what Minecraft now uses.
 */
public class VecUtil {
    public static final BlockPos POS_ONE = new BlockPos(1, 1, 1);
    public static final Vec3 VEC_HALF = new Vec3(0.5, 0.5, 0.5);
    public static final Vec3 VEC_ONE = new Vec3(1, 1, 1);

    public static Vec3 add(Vec3 a, Vec3i b) {
        return a.add(b.getX(), b.getY(), b.getZ());
    }

    public static Vec3 offset(Vec3 from, Direction direction, double by) {
        return from.add(direction.getStepX() * by, direction.getStepY() * by, direction.getStepZ() * by);
    }

    public static double dot(Vec3 a, Vec3 b) {
        return a.x * b.x + a.y * b.y + a.z * b.z;
    }

    public static Vec3 scale(Vec3 vec, double scale) {
        return vec.scale(scale);
    }

    public static Direction getFacing(Axis axis, boolean positive) {
        AxisDirection dir = positive ? AxisDirection.POSITIVE : AxisDirection.NEGATIVE;
        return Direction.fromAxisAndDirection(axis, dir);
    }

    public static BlockPos absolute(BlockPos val) {
        return new BlockPos(Math.abs(val.getX()), Math.abs(val.getY()), Math.abs(val.getZ()));
    }

    public static Vec3 replaceValue(Vec3 old, Axis axis, double with) {
        return new Vec3(//
            axis == Axis.X ? with : old.x,//
            axis == Axis.Y ? with : old.y,//
            axis == Axis.Z ? with : old.z//
        );
    }

    @NotNull
    public static BlockPos replaceValue(Vec3i old, Axis axis, int with) {
        return new BlockPos(//
            axis == Axis.X ? with : old.getX(),//
            axis == Axis.Y ? with : old.getY(),//
            axis == Axis.Z ? with : old.getZ()//
        );
    }

    public static double getValue(Vec3 from, Axis axis) {
        return axis == Axis.X ? from.x : axis == Axis.Y ? from.y : from.z;
    }

    public static int getValue(Vec3i from, Axis axis) {
        return axis == Axis.X ? from.getX() : axis == Axis.Y ? from.getY() : from.getZ();
    }

    public static double getValue(Vec3 negative, Vec3 positive, Direction face) {
        return switch (face) {
            case DOWN -> negative.y;
            case UP -> positive.y;
            case NORTH -> negative.z;
            case SOUTH -> positive.z;
            case WEST -> negative.x;
            case EAST -> positive.x;
        };
    }

    public static int getValue(Vec3i negative, Vec3i positive, Direction face) {
        return switch (face) {
            case DOWN -> negative.getY();
            case UP -> positive.getY();
            case NORTH -> negative.getZ();
            case SOUTH -> positive.getZ();
            case WEST -> negative.getX();
            case EAST -> positive.getX();
        };
    }

    public static Vec3 convertCenter(Vec3i pos) {
        return new Vec3(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
    }

    public static BlockPos convertFloor(Vec3 vec) {
        // BlockPos lost its double constructor; containing() floors, which is what this did.
        return BlockPos.containing(vec.x, vec.y, vec.z);
    }

    public static BlockPos convertCeiling(Vec3 vec) {
        return new BlockPos(
            (int) Math.ceil(vec.x),
            (int) Math.ceil(vec.y),
            (int) Math.ceil(vec.z)
        );
    }

    public static Vector3f convertFloat(Vec3 vec) {
        return new Vector3f((float) vec.x, (float) vec.y, (float) vec.z);
    }

    // Min/Max
    // Unlike BlockPos.min/max these tolerate nulls, which callers rely on when folding over an
    // incrementally-discovered set of positions, so they are kept rather than delegated.

    public static BlockPos min(BlockPos a, BlockPos b) {
        if (a == null) return b;
        if (b == null) return a;
        return new BlockPos(//
            Math.min(a.getX(), b.getX()),//
            Math.min(a.getY(), b.getY()),//
            Math.min(a.getZ(), b.getZ())//
        );
    }

    public static BlockPos min(BlockPos a, BlockPos b, BlockPos c) {
        return min(min(a, b), c);
    }

    public static BlockPos min(BlockPos a, BlockPos b, BlockPos c, BlockPos d) {
        return min(min(a, b), min(c, d));
    }

    public static BlockPos max(BlockPos a, BlockPos b) {
        if (a == null) return b;
        if (b == null) return a;
        return new BlockPos(//
            Math.max(a.getX(), b.getX()),//
            Math.max(a.getY(), b.getY()),//
            Math.max(a.getZ(), b.getZ())//
        );
    }

    public static BlockPos max(BlockPos a, BlockPos b, BlockPos c) {
        return max(max(a, b), c);
    }

    public static BlockPos max(BlockPos a, BlockPos b, BlockPos c, BlockPos d) {
        return max(max(a, b), max(c, d));
    }

    public static Vec3 min(Vec3 a, Vec3 b) {
        if (a == null) return b;
        if (b == null) return a;
        return new Vec3(//
            Math.min(a.x, b.x),//
            Math.min(a.y, b.y),//
            Math.min(a.z, b.z)//
        );
    }

    public static Vec3 min(Vec3 a, Vec3 b, Vec3 c) {
        return min(min(a, b), c);
    }

    public static Vec3 min(Vec3 a, Vec3 b, Vec3 c, Vec3 d) {
        return min(min(a, b), min(c, d));
    }

    public static Vec3 max(Vec3 a, Vec3 b) {
        if (a == null) return b;
        if (b == null) return a;
        return new Vec3(//
            Math.max(a.x, b.x),//
            Math.max(a.y, b.y),//
            Math.max(a.z, b.z)//
        );
    }

    public static Vec3 max(Vec3 a, Vec3 b, Vec3 c) {
        return max(max(a, b), c);
    }

    public static Vec3 max(Vec3 a, Vec3 b, Vec3 c, Vec3 d) {
        return max(max(a, b), max(c, d));
    }
}
