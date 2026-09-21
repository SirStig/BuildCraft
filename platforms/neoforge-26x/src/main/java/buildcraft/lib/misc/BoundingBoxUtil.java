/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * Copyright (c) 2026 Joshua Kac -- NeoForge port (BuildCraft 10)
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL
 * was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.lib.misc;

import java.util.Collection;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Axis;
import net.minecraft.core.Direction.AxisDirection;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import buildcraft.api.core.IBox;

/** Various methods operating on (and creating) {@link AABB}.
 *
 * <p>{@link AABB} lost its {@code (BlockPos, BlockPos)} constructor on this target (it now only takes a single
 * {@link BlockPos}, two {@link Vec3}s, or six {@code double}s), so every corner-pair construction here goes
 * through {@link #makeFrom(BlockPos, BlockPos)} instead of the 1.12.2 {@code new AxisAlignedBB(min, max)} call
 * directly. The 1.20.1 copy of this class keeps the two-{@link BlockPos} constructor and needs no such helper.
 *
 * <p>The {@code makeFrom(BlockPos, BlockPos...)} overload no longer builds a {@code lib.misc.data.Box} (not
 * ported yet, and this is the only place in this class that would have needed it) -- it inlines the same
 * min/max walk the other overloads already do with {@link VecUtil#min}/{@link VecUtil#max}. */
public class BoundingBoxUtil {

    /** Builds the inclusive box spanning {@code min} to {@code max} (i.e. {@code max} is the last block position
     * included, not an exclusive bound). See the class javadoc for why this exists on this target. */
    private static AABB makeFrom(BlockPos min, BlockPos max) {
        return new AABB(min.getX(), min.getY(), min.getZ(), max.getX() + 1, max.getY() + 1, max.getZ() + 1);
    }

    /** Creates an {@link AABB} from a block pos and a box. Note that additional must NOT be null, but the box
     * can be. */
    public static AABB makeFrom(BlockPos additional, @Nullable IBox box) {
        if (box == null) {
            return new AABB(additional);
        } else {
            BlockPos min = VecUtil.min(box.min(), additional);
            BlockPos max = VecUtil.max(box.max(), additional);
            return makeFrom(min, max);
        }
    }

    public static AABB makeFrom(BlockPos primary, BlockPos... additional) {
        BlockPos min = primary;
        BlockPos max = primary;
        for (BlockPos a : additional) {
            min = VecUtil.min(min, a);
            max = VecUtil.max(max, a);
        }
        return makeFrom(min, max);
    }

    /** Creates an {@link AABB} from a block pos and 2 boxes Note that additional must NOT be null, but (either
     * of) the boxes can be. */
    public static AABB makeFrom(BlockPos additional, @Nullable IBox box1, @Nullable IBox box2) {
        if (box1 == null) {
            return makeFrom(additional, box2);
        } else if (box2 == null) {
            return makeFrom(additional, box1);
        } else {
            BlockPos min = VecUtil.min(box1.min(), box2.min(), additional);
            BlockPos max = VecUtil.max(box1.max(), box2.max(), additional);
            return makeFrom(min, max);
        }
    }

    public static AABB makeFrom(Vec3 from, Vec3 to) {
        return new AABB(from.x, from.y, from.z, to.x, to.y, to.z);
    }

    public static AABB makeFrom(Vec3 from, Vec3 to, double radius) {
        return makeFrom(from, to).inflate(radius);
    }

    public static AABB makeAround(Vec3 around, double radius) {
        return new AABB(around.x, around.y, around.z, around.x, around.y, around.z).inflate(radius);
    }

    public static AABB makeFrom(BlockPos pos, @Nullable IBox box, @Nullable Collection<BlockPos> additional) {
        BlockPos min = box == null ? pos : VecUtil.min(box.min(), pos);
        BlockPos max = box == null ? pos : VecUtil.max(box.max(), pos);
        if (additional != null) {
            for (BlockPos p : additional) {
                min = VecUtil.min(min, p);
                max = VecUtil.max(max, p);
            }
        }
        return makeFrom(min, max);
    }

    /** Creates a box that extrudes from the specified face of the given block position. */
    public static AABB extrudeFace(BlockPos pos, Direction face, double depth) {
        Vec3 from = new Vec3(pos);
        Vec3 to = new Vec3(pos).add(1, 1, 1);

        Axis axis = face.getAxis();
        if (face.getAxisDirection() == AxisDirection.POSITIVE) {
            from = VecUtil.replaceValue(from, axis, VecUtil.getValue(from, axis) + 1);
            to = VecUtil.replaceValue(to, axis, VecUtil.getValue(to, axis) + depth);
        } else {
            to = VecUtil.replaceValue(to, axis, VecUtil.getValue(to, axis) - 1);
            from = VecUtil.replaceValue(from, axis, VecUtil.getValue(from, axis) - depth);
        }
        return makeFrom(from, to);
    }
}
