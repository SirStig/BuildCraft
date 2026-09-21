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
 * <p>{@code AxisAlignedBB#grow} is {@link AABB#inflate} here. This target still has {@link AABB}'s
 * {@code (BlockPos, BlockPos)} constructor, unlike 26.x -- see that copy of this class for why it needs an
 * extra helper method that this one doesn't. */
public class BoundingBoxUtil {

    /** Creates an {@link AABB} from a block pos and a box. Note that additional must NOT be null, but the box
     * can be. */
    public static AABB makeFrom(BlockPos additional, @Nullable IBox box) {
        if (box == null) {
            return new AABB(additional);
        } else {
            BlockPos min = VecUtil.min(box.min(), additional);
            BlockPos max = VecUtil.max(box.max(), additional);
            return new AABB(min, max.offset(VecUtil.POS_ONE));
        }
    }

    /** The 1.12.2 original built this from {@code lib.misc.data.Box}, which is not ported yet; this inlines the
     * same min/max walk the other overloads here already do instead. */
    public static AABB makeFrom(BlockPos primary, BlockPos... additional) {
        BlockPos min = primary;
        BlockPos max = primary;
        for (BlockPos a : additional) {
            min = VecUtil.min(min, a);
            max = VecUtil.max(max, a);
        }
        return new AABB(min, max.offset(VecUtil.POS_ONE));
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
            return new AABB(min, max.offset(VecUtil.POS_ONE));
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
        return new AABB(min, max.offset(VecUtil.POS_ONE));
    }

    /** Creates a box that extrudes from the specified face of the given block position. */
    public static AABB extrudeFace(BlockPos pos, Direction face, double depth) {
        // Vec3 lost its Vec3i constructor on this target (still has it on 26.x), so the BlockPos -> Vec3
        // conversion has to spell out the three components.
        Vec3 from = new Vec3(pos.getX(), pos.getY(), pos.getZ());
        Vec3 to = from.add(1, 1, 1);

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
