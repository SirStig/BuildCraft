/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * Copyright (c) 2026 Joshua Kac -- NeoForge port (BuildCraft 10)
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL
 * was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.lib.client.render.laser;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Axis;
import net.minecraft.world.phys.Vec3;

import buildcraft.lib.client.render.laser.LaserData_BC8.LaserType;
import buildcraft.lib.misc.VecUtil;
import buildcraft.lib.misc.data.Box;

/** Turns a box into the (up to) 12 lasers along its edges -- 1.12.2's {@code LaserBoxRenderer#makeLaserBox}, the
 * geometry every "outline a region" consumer (volume markers, the quarry/filler/builder frame preview, map
 * locations) shares.
 *
 * <p>1.12.2 also cached the resulting {@code LaserData_BC8[]} on the {@code Box} itself ({@code laserData}/
 * {@code lastMin}/{@code lastMax}/{@code lastType}) and drew it in the same call. Both are split out here: this
 * class only <em>builds</em> the list (the ported {@link Box} deliberately carries no client rendering state, and
 * building 12 small value objects is not worth caching), and {@link LaserRenderer_BC8}'s compiled-geometry cache --
 * keyed by the resulting {@link LaserData_BC8} values -- is what actually avoids rebuilding geometry frame to
 * frame. */
public final class LaserBoxRenderer {
    private static final double RENDER_SCALE = 1 / 16.05;

    private LaserBoxRenderer() {}

    /** @return An empty list if {@code box} isn't initialised. */
    public static List<LaserData_BC8> makeLaserBox(Box box, LaserType type, boolean center) {
        if (box == null || box.min() == null || box.max() == null) {
            return List.of();
        }
        return makeLaserBox(box.min(), box.max(), type, center);
    }

    /** @param center If true, the lasers run through the centres of the corner blocks (and an axis the box is only
     *            one block thick along gets no lasers at all); if false, they run along the outer faces of the
     *            corner blocks. */
    public static List<LaserData_BC8> makeLaserBox(BlockPos minPos, BlockPos maxPos, LaserType type, boolean center) {
        boolean renderX = !center || maxPos.getX() - minPos.getX() + 1 > 1;
        boolean renderY = !center || maxPos.getY() - minPos.getY() + 1 > 1;
        boolean renderZ = !center || maxPos.getZ() - minPos.getZ() + 1 > 1;

        Vec3 min = Vec3.atLowerCornerOf(minPos).add(center ? VecUtil.VEC_HALF : Vec3.ZERO);
        Vec3 max = Vec3.atLowerCornerOf(maxPos).add(center ? VecUtil.VEC_HALF : VecUtil.VEC_ONE);

        List<LaserData_BC8> datas = new ArrayList<>();

        Vec3[][][] vecs = new Vec3[2][2][2];
        vecs[0][0][0] = new Vec3(min.x, min.y, min.z);
        vecs[1][0][0] = new Vec3(max.x, min.y, min.z);
        vecs[0][1][0] = new Vec3(min.x, max.y, min.z);
        vecs[1][1][0] = new Vec3(max.x, max.y, min.z);
        vecs[0][0][1] = new Vec3(min.x, min.y, max.z);
        vecs[1][0][1] = new Vec3(max.x, min.y, max.z);
        vecs[0][1][1] = new Vec3(min.x, max.y, max.z);
        vecs[1][1][1] = new Vec3(max.x, max.y, max.z);

        if (renderX) {
            datas.add(makeLaser(type, vecs[0][0][0], vecs[1][0][0], Axis.X));
            if (renderY) {
                datas.add(makeLaser(type, vecs[0][1][0], vecs[1][1][0], Axis.X));
                if (renderZ) {
                    datas.add(makeLaser(type, vecs[0][1][1], vecs[1][1][1], Axis.X));
                }
            }
            if (renderZ) {
                datas.add(makeLaser(type, vecs[0][0][1], vecs[1][0][1], Axis.X));
            }
        }

        if (renderY) {
            datas.add(makeLaser(type, vecs[0][0][0], vecs[0][1][0], Axis.Y));
            if (renderX) {
                datas.add(makeLaser(type, vecs[1][0][0], vecs[1][1][0], Axis.Y));
                if (renderZ) {
                    datas.add(makeLaser(type, vecs[1][0][1], vecs[1][1][1], Axis.Y));
                }
            }
            if (renderZ) {
                datas.add(makeLaser(type, vecs[0][0][1], vecs[0][1][1], Axis.Y));
            }
        }

        if (renderZ) {
            datas.add(makeLaser(type, vecs[0][0][0], vecs[0][0][1], Axis.Z));
            if (renderX) {
                datas.add(makeLaser(type, vecs[1][0][0], vecs[1][0][1], Axis.Z));
                if (renderY) {
                    datas.add(makeLaser(type, vecs[1][1][0], vecs[1][1][1], Axis.Z));
                }
            }
            if (renderY) {
                datas.add(makeLaser(type, vecs[0][1][0], vecs[0][1][1], Axis.Z));
            }
        }

        return datas;
    }

    /** 1.12.2's own end offset, kept exactly: the min end moves one pixel towards +axis and the max end one pixel
     * towards -axis, i.e. each edge is pulled one pixel <em>inwards</em> at both ends. */
    private static LaserData_BC8 makeLaser(LaserType type, Vec3 min, Vec3 max, Axis axis) {
        Direction faceForMin = VecUtil.getFacing(axis, true);
        Direction faceForMax = VecUtil.getFacing(axis, false);
        Vec3 one = VecUtil.offset(min, faceForMin, 1 / 16D);
        Vec3 two = VecUtil.offset(max, faceForMax, 1 / 16D);
        return new LaserData_BC8(type, one, two, RENDER_SCALE);
    }
}
