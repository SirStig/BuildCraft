/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * Copyright (c) 2026 Joshua Kac -- NeoForge port (BuildCraft 10)
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL
 * was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.silicon.client.render;

import java.util.List;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import buildcraft.api.core.SafeTimeTracker;
import buildcraft.api.properties.BuildCraftProperties;

import buildcraft.lib.client.render.laser.CompiledLaser;
import buildcraft.lib.client.render.laser.LaserData_BC8;
import buildcraft.lib.client.render.laser.LaserRenderer_BC8;
import buildcraft.lib.misc.VecUtil;

import buildcraft.core.client.BuildCraftLaserManager;
import buildcraft.silicon.tile.TileLaser;

/**
 * Draws a {@link TileLaser}'s beam to its current target, reusing the same {@code lib.client.render.laser}
 * pipeline {@code RenderMarkerVolume} already established as this port's block-entity-renderer laser precedent
 * (see that class's own javadoc for why a beam genuinely owned by one loaded tile stays a
 * {@code BlockEntityRenderer} rather than a level-wide event like the marker connection lasers).
 *
 * <p>1.12.2's {@code RenderLaser} picked one of {@code BuildCraftLaserManager.POWERS} (four {@code LaserType}s,
 * red/yellow/green/blue) by the beam's recent average throughput -- exactly the four types already declared on
 * this port (see {@code BuildCraftLaserManager}'s own javadoc: "the ... silicon-laser renderers still to come"),
 * so no new sprite or {@code LaserType} is needed here at all.
 *
 * <p><b>Dropped from 1.12.2:</b> the {@code BCSiliconConfig.renderLaserBeams}/goggles gate. No config system is
 * ported yet (see {@code buildcraft.factory.tile.TileMiner}'s own javadoc on this point), and the config's
 * unconfigured default was already {@code true} -- i.e. beams already drew unconditionally for a player without
 * goggles in a real 1.12.2 install -- so this renderer simply always draws while the beam is actually carrying
 * power, which reproduces that real default behaviour exactly rather than gating on a setting that cannot yet be
 * turned off. {@code ItemGoggles}'s bypass-the-config special case is therefore moot and not reproduced.
 *
 * <p><b>The beam's jittering endpoint</b> ({@code laserPos} in 1.12.2, recomputed on a client tick) is recomputed
 * here instead, on the tile's own {@link #laserMoveInterval}, since nothing ticks a block entity client-side on
 * this target -- see {@link TileLaser}'s own javadoc.
 */
public class RenderLaser implements BlockEntityRenderer<TileLaser, RenderLaser.LaserRenderState> {
    private static final int MAX_POWER = BuildCraftLaserManager.POWERS.length - 1;
    /** 1.12.2's own threshold: below 0.2 MJ/tick average, the beam is considered off and is not drawn at all. */
    private static final long MIN_VISIBLE_FLOW = 200_000;

    private final SafeTimeTracker laserMoveInterval = new SafeTimeTracker(5, 10);

    public RenderLaser(BlockEntityRendererProvider.Context context) {}

    @Override
    public LaserRenderState createRenderState() {
        return new LaserRenderState();
    }

    @Override
    public void extractRenderState(TileLaser tile, LaserRenderState state, float partialTicks, Vec3 cameraPosition,
        ModelFeatureRenderer.CrumblingOverlay breakProgress) {
        BlockEntityRenderer.super.extractRenderState(tile, state, partialTicks, cameraPosition, breakProgress);

        long avg = tile.getAverageClient();
        if (avg <= MIN_VISIBLE_FLOW || tile.getLevel() == null) {
            state.laser = null;
            return;
        }

        if (laserMoveInterval.markTimeIfDelay(tile.getLevel()) || tile.laserPos == null) {
            updateLaserPos(tile);
        }
        if (tile.laserPos == null) {
            state.laser = null;
            return;
        }

        BlockState blockState = tile.getLevel().getBlockState(tile.getBlockPos());
        if (!blockState.hasProperty(BuildCraftProperties.BLOCK_FACING_6)) {
            state.laser = null;
            return;
        }
        Direction side = blockState.getValue(BuildCraftProperties.BLOCK_FACING_6);
        Vec3 offset = VecUtil.offset(new Vec3(0.5, 0.5, 0.5), side, 4 / 16D);

        long biased = avg + MIN_VISIBLE_FLOW;
        int index = (int) (biased * MAX_POWER / tile.getMaxPowerPerTick());
        if (index > MAX_POWER) {
            index = MAX_POWER;
        }
        LaserData_BC8 data = new LaserData_BC8(BuildCraftLaserManager.POWERS[index],
            Vec3.atLowerCornerOf(tile.getBlockPos()).add(offset), tile.laserPos, 1 / 16D);
        state.laser = LaserRenderer_BC8.compile(data);
    }

    /** 1.12.2's own {@code updateLaser}: jitters the beam's business end to a random point in the target table's
     * top face. Recomputed here (rather than on a tick) purely for render smoothing -- see this class's own
     * javadoc. */
    private static void updateLaserPos(TileLaser tile) {
        BlockPos target = tile.getTargetPos();
        if (target == null) {
            tile.laserPos = null;
            return;
        }
        RandomSource rand = tile.getLevel().getRandom();
        tile.laserPos = Vec3.atLowerCornerOf(target).add(
            (5 + rand.nextInt(6) + 0.5) / 16D,
            9 / 16D,
            (5 + rand.nextInt(6) + 0.5) / 16D);
    }

    @Override
    public void submit(LaserRenderState state, PoseStack poseStack, SubmitNodeCollector submitNodeCollector,
        CameraRenderState camera) {
        if (state.laser == null) {
            return;
        }
        LaserRenderer_BC8.submit(submitNodeCollector, poseStack, List.of(state.laser),
            Vec3.atLowerCornerOf(state.blockPos));
    }

    @Override
    public boolean shouldRenderOffScreen() {
        return true;
    }

    @Override
    public AABB getRenderBoundingBox(TileLaser tile) {
        return new AABB(tile.getBlockPos()).inflate(TileLaser.TARGETING_RANGE + 1);
    }

    /** Everything {@link #submit} needs -- the already-compiled beam, or {@code null} while off -- captured by
     * {@link #extractRenderState}, matching {@code RenderMarkerVolume}'s own {@code SignalRenderState}. */
    public static final class LaserRenderState extends BlockEntityRenderState {
        CompiledLaser laser;
    }
}
