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

import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Axis;
import net.minecraft.world.phys.AABB;
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
 * the marker is not already connected along, showing where a partner marker could go.
 *
 * <p>Deliberately still a {@code BlockEntityRenderer}, exactly like 1.12.2's own {@code RenderMarkerVolume}
 * ({@code TileEntitySpecialRenderer} with {@code isGlobalRenderer = true}), unlike the connection lasers (see
 * {@link RenderMarkerConnections}): these lasers genuinely belong to one loaded tile and exist only while it does,
 * which is precisely the case a block entity renderer is for -- and it is the first real consumer of the laser
 * utility through this target's {@code BlockEntityRenderer<T, S>} contract, the shape every later laser consumer
 * (quarry, mining well, silicon laser) will use. 1.12.2's global-renderer flag and infinite render box map to real,
 * {@code javap}/decompiled-source-confirmed 26.3 hooks: {@link #shouldRenderOffScreen()} (the dispatcher's
 * "globally rendered" block-entity list, so the beams don't vanish when the marker's own chunk section is off
 * screen), {@link #getRenderBoundingBox} (NeoForge's {@code IBlockEntityRendererExtension}, checked against the
 * frustum in {@code BlockEntityRenderDispatcher#tryExtractRenderState}) sized to the beams' real reach rather than
 * infinite, and {@link #getViewDistance()} = 128, 1.12.2's own {@code getMaxRenderDistanceSquared}
 * ({@code markerMaxDistance * 4 * markerMaxDistance}, i.e. a distance of {@code 2 * 64}). */
public class RenderMarkerVolume implements BlockEntityRenderer<TileMarkerVolume, RenderMarkerVolume.SignalRenderState> {
    private static final double SCALE = 1 / 16.2; // smaller than normal lasers

    private static final LaserType LASER_TYPE = BuildCraftLaserManager.MARKER_VOLUME_SIGNAL;

    public RenderMarkerVolume(BlockEntityRendererProvider.Context context) {}

    @Override
    public SignalRenderState createRenderState() {
        return new SignalRenderState();
    }

    @Override
    public void extractRenderState(TileMarkerVolume marker, SignalRenderState state, float partialTicks,
        Vec3 cameraPosition, ModelFeatureRenderer.CrumblingOverlay breakProgress) {
        BlockEntityRenderer.super.extractRenderState(marker, state, partialTicks, cameraPosition, breakProgress);
        if (!marker.isShowingSignals()) {
            state.lasers = List.of();
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
        state.lasers = lasers;
    }

    /** 1.12.2's own {@code renderLaser}: the start moves one pixel towards +axis and the end one pixel towards -axis
     * -- regardless of which way this particular beam points, exactly as in the original. */
    private static LaserData_BC8 makeLaser(Vec3 min, Vec3 max, Axis axis) {
        Vec3 one = VecUtil.offset(min, VecUtil.getFacing(axis, true), 1 / 16.0);
        Vec3 two = VecUtil.offset(max, VecUtil.getFacing(axis, false), 1 / 16.0);
        return new LaserData_BC8(LASER_TYPE, one, two, SCALE);
    }

    @Override
    public void submit(SignalRenderState state, PoseStack poseStack, SubmitNodeCollector submitNodeCollector,
        CameraRenderState camera) {
        LaserRenderer_BC8.submit(submitNodeCollector, poseStack, state.lasers, Vec3.atLowerCornerOf(state.blockPos));
    }

    @Override
    public boolean shouldRenderOffScreen() {
        return true;
    }

    @Override
    public int getViewDistance() {
        return 2 * VolumeConnection.MARKER_MAX_DISTANCE;
    }

    @Override
    public AABB getRenderBoundingBox(TileMarkerVolume marker) {
        return new AABB(marker.getBlockPos()).inflate(VolumeConnection.MARKER_MAX_DISTANCE + 1);
    }

    /** Everything {@link #submit} needs -- the already-compiled signal lasers -- captured by
     * {@link #extractRenderState}, matching {@code RenderTileTank}'s own {@code TankRenderState}. */
    public static final class SignalRenderState extends BlockEntityRenderState {
        List<CompiledLaser> lasers = List.of();
    }
}
