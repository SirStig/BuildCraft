/* Copyright (c) 2016 SpaceToad and the BuildCraft team
 * Copyright (c) 2026 Joshua Kac -- NeoForge port (BuildCraft 10)
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL
 * was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/. */
package buildcraft.core.client.render;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Axis;
import net.minecraft.world.phys.Vec3;

import buildcraft.lib.client.render.laser.CompiledLaser;
import buildcraft.lib.client.render.laser.LaserData_BC8;
import buildcraft.lib.client.render.laser.LaserData_BC8.LaserType;
import buildcraft.lib.client.render.laser.LaserRenderer_BC8;
import buildcraft.lib.misc.VecUtil;

import buildcraft.core.client.BuildCraftLaserManager;
import buildcraft.core.marker.VolumeConnection;
import buildcraft.core.tile.TileMarkerVolume;

/** Draws a volume marker's "signals": while {@link TileMarkerVolume#isShowingSignals()} is on (toggled by
 * right-clicking the marker), a laser runs {@link VolumeConnection#MARKER_MAX_DISTANCE} blocks out along every axis
 * the marker is not already connected along. See the 26.x copy of this class for why this, unlike the connection
 * lasers ({@link RenderMarkerConnections}), stays a block entity renderer.
 *
 * <p>This target keeps the classic immediate-mode {@code render(...)} contract, so this is a much closer analogue of
 * 1.12.2's own {@code TileEntitySpecialRenderer} than the 26.x copy. 1.12.2's hooks map as follows here (confirmed
 * against the real 1.20.1 Forge sources): {@code isGlobalRenderer} is {@link #shouldRenderOffScreen}, and
 * {@code getMaxRenderDistanceSquared} ({@code 64 * 4 * 64}) is {@link #getViewDistance()} = 128. The original's
 * {@code getRenderBoundingBox() = INFINITE_EXTENT_AABB} needs no override at all on this target: Forge's default
 * {@code IForgeBlockEntity#getRenderBoundingBox} already returns {@code INFINITE_EXTENT_AABB} for any block whose
 * collision shape is empty, and the marker blocks are {@code noCollission()} -- the box the
 * {@code LevelRenderer} frustum-tests global block entities against is therefore already infinite. */
public class RenderMarkerVolume implements BlockEntityRenderer<TileMarkerVolume> {
    private static final double SCALE = 1 / 16.2; // smaller than normal lasers

    private static final LaserType LASER_TYPE = BuildCraftLaserManager.MARKER_VOLUME_SIGNAL;

    public RenderMarkerVolume(BlockEntityRendererProvider.Context context) {}

    @Override
    public void render(TileMarkerVolume marker, float partialTicks, PoseStack poseStack, MultiBufferSource buffers,
        int packedLight, int packedOverlay) {
        if (!marker.isShowingSignals()) {
            return;
        }
        VolumeConnection volume = marker.getCurrentConnection();
        Set<Axis> taken = volume == null ? Set.of() : volume.getConnectedAxis();

        List<CompiledLaser> lasers = new ArrayList<>();
        Vec3 start = Vec3.atCenterOf(marker.getBlockPos());
        for (Direction face : Direction.values()) {
            if (taken.contains(face.getAxis())) {
                continue;
            }
            Vec3 end = VecUtil.offset(start, face, VolumeConnection.MARKER_MAX_DISTANCE);
            lasers.add(LaserRenderer_BC8.compile(makeLaser(start, end, face.getAxis())));
        }
        LaserRenderer_BC8.render(poseStack, buffers, lasers, Vec3.atLowerCornerOf(marker.getBlockPos()));
    }

    /** 1.12.2's own {@code renderLaser}: the start moves one pixel towards +axis and the end one pixel towards -axis
     * -- regardless of which way this particular beam points, exactly as in the original. */
    private static LaserData_BC8 makeLaser(Vec3 min, Vec3 max, Axis axis) {
        Vec3 one = VecUtil.offset(min, VecUtil.getFacing(axis, true), 1 / 16.0);
        Vec3 two = VecUtil.offset(max, VecUtil.getFacing(axis, false), 1 / 16.0);
        return new LaserData_BC8(LASER_TYPE, one, two, SCALE);
    }

    @Override
    public boolean shouldRenderOffScreen(TileMarkerVolume marker) {
        return true;
    }

    @Override
    public int getViewDistance() {
        return 2 * VolumeConnection.MARKER_MAX_DISTANCE;
    }
}
