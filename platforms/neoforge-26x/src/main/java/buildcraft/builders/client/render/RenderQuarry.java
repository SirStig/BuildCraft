/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * Copyright (c) 2026 Joshua Kac -- NeoForge port (BuildCraft 10)
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL
 * was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.builders.client.render;

import java.util.ArrayList;
import java.util.List;

import org.jetbrains.annotations.Nullable;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import buildcraft.lib.client.render.laser.CompiledLaser;
import buildcraft.lib.client.render.laser.LaserBoxRenderer;
import buildcraft.lib.client.render.laser.LaserData_BC8;
import buildcraft.lib.client.render.laser.LaserRenderer_BC8;

import buildcraft.builders.tile.TileQuarry;
import buildcraft.core.client.BuildCraftLaserManager;

/**
 * Draws the quarry's frame outline and a "current work position" indicator -- 1.12.2's
 * {@code common/buildcraft/builders/client/render/RenderQuarry.java}, entirely laser-based there (no cube/model
 * geometry anywhere in the original file -- confirmed by reading it fully before porting), so this stays
 * laser-based too rather than reaching for {@code RenderPartCube}. Ported onto this target's
 * {@code BlockEntityRenderer<T, S>} extract/submit contract, the same shape {@code RenderMiningWell}/
 * {@code RenderPump} already established for their own tube lasers.
 *
 * <p><b>What's ported.</b> The static frame-box outline ({@link LaserBoxRenderer#makeLaserBox}, 1.12.2's own
 * {@code LaserBoxRenderer.renderLaserBoxStatic(tile.frameBox, BuildCraftLaserManager.STRIPES_WRITE, true)} fallback
 * branch -- reached whenever there is no in-progress drill animation) plus a simplified "drill" indicator: two
 * rail lasers along the frame's top edge crossing at {@link TileQuarry#getActionPos()}'s X/Z, and one vertical
 * laser dropping from the rails to the current action's Y. This reuses only already-stitched
 * {@link BuildCraftLaserManager} sprites ({@code STRIPES_WRITE} for the frame/rails, {@code POWER_LOW} for the
 * drop -- 1.12.2's own {@code RenderQuarry.LASER} constant was already {@code BuildCraftLaserManager.POWER_LOW},
 * so this is the same reuse the original made, not a new one).
 *
 * <p><b>What's not ported, and why.</b> 1.12.2's animation was driven by a client-synced, per-tick-interpolated
 * {@code clientDrillPos}/{@code prevClientDrillPos} (smoothly sliding the rail intersection between dig positions)
 * and a break-progress-driven vertical bob ({@code yOffset}, eased from {@code TaskBreakBlock}'s power ratio). The
 * ported {@code TileQuarry} carries neither -- its own javadoc explains why: with no drill-position state machine
 * left server-side (see that class's "No {@code drillPos}/{@code Task} state machine" note), there is nothing to
 * sync or interpolate. This renderer instead reads {@link TileQuarry#getActionPos()} directly and draws straight
 * to it every frame -- a real, working indicator of where the quarry is currently acting (mining, clearing an
 * obstruction, or placing a frame block), just without the smooth slide-and-bob 1.12.2 had. Also not ported: the
 * two custom {@code LaserType}s 1.12.2 built from {@code buildcraftbuilders:blocks/frame/default}/
 * {@code blocks/quarry/drill} sprites -- neither PNG has been copied into this port's {@code assets/buildcraft}
 * tree yet, and adding new laser sprites means also adding them to the block atlas's sprite source list (see
 * {@link BuildCraftLaserManager}'s own javadoc); reusing already-stitched sprites avoids that asset-pipeline work
 * this pass. The dead {@code if (... && false)} frame-item-flying-through-the-air block (1.12.2's own leftover,
 * never actually enabled) is dropped outright, matching the original's own effective behaviour.
 */
public class RenderQuarry implements BlockEntityRenderer<TileQuarry, RenderQuarry.QuarryRenderState> {

    private static final double RAIL_INSET = 4 / 16.0;
    private static final double RAIL_OUTSET = 12 / 16.0;
    private static final double LASER_WIDTH = 1 / 16.0;

    public RenderQuarry(BlockEntityRendererProvider.Context context) {}

    @Override
    public QuarryRenderState createRenderState() {
        return new QuarryRenderState();
    }

    @Override
    public void extractRenderState(TileQuarry tile, QuarryRenderState state, float partialTick, Vec3 cameraPosition,
        ModelFeatureRenderer.CrumblingOverlay breakProgress) {
        BlockEntityRenderer.super.extractRenderState(tile, state, partialTick, cameraPosition, breakProgress);

        state.lasers = null;
        if (!tile.frameBox.isInitialized()) {
            return;
        }

        List<CompiledLaser> compiled = new ArrayList<>();
        for (LaserData_BC8 data : LaserBoxRenderer.makeLaserBox(tile.frameBox, BuildCraftLaserManager.STRIPES_WRITE, true)) {
            compiled.add(LaserRenderer_BC8.compile(data));
        }

        BlockPos actionPos = tile.getActionPos();
        if (actionPos != null) {
            BlockPos min = tile.frameBox.min();
            BlockPos max = tile.frameBox.max();
            double railY = max.getY() + 0.5;
            double ax = actionPos.getX() + 0.5;
            double az = actionPos.getZ() + 0.5;

            compiled.add(LaserRenderer_BC8.compile(new LaserData_BC8(BuildCraftLaserManager.STRIPES_WRITE,
                new Vec3(min.getX() + RAIL_INSET, railY, az), new Vec3(max.getX() + RAIL_OUTSET, railY, az), LASER_WIDTH)));
            compiled.add(LaserRenderer_BC8.compile(new LaserData_BC8(BuildCraftLaserManager.STRIPES_WRITE,
                new Vec3(ax, railY, min.getZ() + RAIL_INSET), new Vec3(ax, railY, max.getZ() + RAIL_OUTSET), LASER_WIDTH)));
            double dropTo = Math.min(railY, actionPos.getY() + 1.0);
            if (dropTo < railY) {
                compiled.add(LaserRenderer_BC8.compile(new LaserData_BC8(BuildCraftLaserManager.POWER_LOW,
                    new Vec3(ax, railY, az), new Vec3(ax, dropTo, az), LASER_WIDTH)));
            }
        }
        state.lasers = compiled;
    }

    @Override
    public void submit(QuarryRenderState state, PoseStack poseStack, SubmitNodeCollector submitNodeCollector,
        CameraRenderState camera) {
        if (state.lasers != null) {
            LaserRenderer_BC8.submit(submitNodeCollector, poseStack, state.lasers, Vec3.atLowerCornerOf(state.blockPos));
        }
    }

    @Override
    public boolean shouldRenderOffScreen() {
        return true;
    }

    @Override
    public AABB getRenderBoundingBox(TileQuarry tile) {
        BlockPos pos = tile.getBlockPos();
        if (!tile.frameBox.isInitialized()) {
            return new AABB(pos.getX(), pos.getY(), pos.getZ(), pos.getX() + 1, pos.getY() + 1, pos.getZ() + 1);
        }
        BlockPos min = tile.frameBox.min();
        BlockPos max = tile.frameBox.max();
        return new AABB(min.getX(), min.getY(), min.getZ(), max.getX() + 1, max.getY() + 1, max.getZ() + 1);
    }

    /** Everything {@link #submit} needs, captured once per frame by {@link #extractRenderState}. */
    public static final class QuarryRenderState extends BlockEntityRenderState {
        @Nullable
        List<CompiledLaser> lasers;
    }
}
