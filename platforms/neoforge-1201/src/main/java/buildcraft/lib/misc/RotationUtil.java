/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * Copyright (c) 2026 Joshua Kac -- NeoForge port (BuildCraft 10)
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL
 * was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.lib.misc;

import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Ported from 1.12.2 with renames only, no behaviour change: {@code EnumFacing} -> {@link Direction},
 * {@code AxisAlignedBB} -> {@link AABB}, {@code Vec3d} -> {@link Vec3}, and {@code Rotation} moving
 * from {@code net.minecraft.util} to {@code net.minecraft.world.level.block}.
 */
public class RotationUtil {

    public static AABB rotateAABB(AABB aabb, Direction facing) {
        return switch (facing) {
            case DOWN -> new AABB(aabb.minX, aabb.maxY, aabb.minZ, aabb.maxX, aabb.minY, aabb.maxZ);
            case UP -> new AABB(aabb.minX, 1 - aabb.maxY, aabb.minZ, aabb.maxX, 1 - aabb.minY, aabb.maxZ);
            case NORTH -> new AABB(aabb.minX, aabb.minZ, aabb.minY, aabb.maxX, aabb.maxZ, aabb.maxY);
            case SOUTH -> new AABB(aabb.minX, aabb.minZ, 1 - aabb.maxY, aabb.maxX, aabb.maxZ, 1 - aabb.minY);
            case WEST -> new AABB(aabb.minY, aabb.minZ, aabb.minX, aabb.maxY, aabb.maxZ, aabb.maxX);
            case EAST -> new AABB(1 - aabb.maxY, aabb.minZ, aabb.minX, 1 - aabb.minY, aabb.maxZ, aabb.maxX);
        };
    }

    public static Vec3 rotateVec3(Vec3 vec, Rotation rotation) {
        return switch (rotation) {
            case NONE -> vec;
            case CLOCKWISE_90 -> new Vec3(1 - vec.z, vec.y, vec.x);
            case CLOCKWISE_180 -> new Vec3(1 - vec.x, vec.y, 1 - vec.z);
            case COUNTERCLOCKWISE_90 -> new Vec3(vec.z, vec.y, 1 - vec.x);
        };
    }

    public static Direction rotateAll(Direction facing) {
        return switch (facing) {
            case NORTH -> Direction.EAST;
            case EAST -> Direction.SOUTH;
            case SOUTH -> Direction.WEST;
            case WEST -> Direction.UP;
            case UP -> Direction.DOWN;
            case DOWN -> Direction.NORTH;
        };
    }

    public static Rotation invert(Rotation rotation) {
        return switch (rotation) {
            case NONE -> Rotation.NONE;
            case CLOCKWISE_90 -> Rotation.COUNTERCLOCKWISE_90;
            case CLOCKWISE_180 -> Rotation.CLOCKWISE_180;
            case COUNTERCLOCKWISE_90 -> Rotation.CLOCKWISE_90;
        };
    }
}
