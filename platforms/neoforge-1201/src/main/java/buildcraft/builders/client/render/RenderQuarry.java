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

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

import buildcraft.lib.client.render.laser.CompiledLaser;
import buildcraft.lib.client.render.laser.LaserBoxRenderer;
import buildcraft.lib.client.render.laser.LaserData_BC8;
import buildcraft.lib.client.render.laser.LaserRenderer_BC8;

import buildcraft.builders.tile.TileQuarry;
import buildcraft.core.client.BuildCraftLaserManager;

/**
 * Mirrors the 26.x class of the same name -- see that one's javadoc for the full account of what 1.12.2's
 * {@code RenderQuarry} looked like, what is ported (the static frame outline plus a simplified rail/drop
 * indicator at the quarry's current action position) and why the client-interpolated drill-carriage animation
 * and the two quarry-specific laser sprites are not. This file differs only in the usual 1.20.1 places: the
 * classic immediate-mode {@code render(...)} contract instead of the extract/{@code submit} split, and
 * {@code LaserRenderer_BC8.render} in place of {@code submit}. {@link TileQuarry#getRenderBoundingBox()} (widened
 * to the frame box, added alongside this class) is this target's equivalent of 26.x's renderer-level
 * {@code getRenderBoundingBox} override -- see that tile's own javadoc note for why it lives there on this
 * target.
 */
public class RenderQuarry implements BlockEntityRenderer<TileQuarry> {

    private static final double RAIL_INSET = 4 / 16.0;
    private static final double RAIL_OUTSET = 12 / 16.0;
    private static final double LASER_WIDTH = 1 / 16.0;

    public RenderQuarry(BlockEntityRendererProvider.Context context) {}

    @Override
    public void render(TileQuarry tile, float partialTick, PoseStack poseStack, MultiBufferSource buffers,
        int packedLight, int packedOverlay) {
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

        LaserRenderer_BC8.render(poseStack, buffers, compiled, Vec3.atLowerCornerOf(tile.getBlockPos()));
    }

    @Override
    public boolean shouldRenderOffScreen(TileQuarry tile) {
        return true;
    }
}
