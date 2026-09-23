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

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
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
 * Draws a {@link TileLaser}'s beam to its current target. Mirrors the 26.x class of the same name -- see that
 * one's own javadoc for why {@link BuildCraftLaserManager#POWERS} needs no new {@code LaserType}/sprite, and why
 * the 1.12.2 config/goggles gate is dropped. This target keeps the classic immediate-mode
 * {@code BlockEntityRenderer#render} contract, so compile and draw happen in the same call, matching
 * {@code RenderMarkerVolume}'s (1.20.1) own precedent.
 */
public class RenderLaser implements BlockEntityRenderer<TileLaser> {
    private static final int MAX_POWER = BuildCraftLaserManager.POWERS.length - 1;
    /** 1.12.2's own threshold: below 0.2 MJ/tick average, the beam is considered off and is not drawn at all. */
    private static final long MIN_VISIBLE_FLOW = 200_000;

    private final SafeTimeTracker laserMoveInterval = new SafeTimeTracker(5, 10);

    public RenderLaser(BlockEntityRendererProvider.Context context) {}

    @Override
    public void render(TileLaser tile, float partialTicks, PoseStack poseStack, MultiBufferSource buffers,
        int packedLight, int packedOverlay) {
        long avg = tile.getAverageClient();
        if (avg <= MIN_VISIBLE_FLOW || tile.getLevel() == null) {
            return;
        }

        if (laserMoveInterval.markTimeIfDelay(tile.getLevel()) || tile.laserPos == null) {
            updateLaserPos(tile);
        }
        if (tile.laserPos == null) {
            return;
        }

        BlockState state = tile.getLevel().getBlockState(tile.getBlockPos());
        if (!state.hasProperty(BuildCraftProperties.BLOCK_FACING_6)) {
            return;
        }
        Direction side = state.getValue(BuildCraftProperties.BLOCK_FACING_6);
        Vec3 offset = VecUtil.offset(new Vec3(0.5, 0.5, 0.5), side, 4 / 16D);

        long biased = avg + MIN_VISIBLE_FLOW;
        int index = (int) (biased * MAX_POWER / tile.getMaxPowerPerTick());
        if (index > MAX_POWER) {
            index = MAX_POWER;
        }
        LaserData_BC8 data = new LaserData_BC8(BuildCraftLaserManager.POWERS[index],
            Vec3.atLowerCornerOf(tile.getBlockPos()).add(offset), tile.laserPos, 1 / 16D);
        CompiledLaser compiled = LaserRenderer_BC8.compile(data);
        LaserRenderer_BC8.render(poseStack, buffers, List.of(compiled), Vec3.atLowerCornerOf(tile.getBlockPos()));
    }

    /** 1.12.2's own {@code updateLaser}: jitters the beam's business end to a random point in the target table's
     * top face. See the 26.x copy of this class's own javadoc for why this is recomputed here rather than on a
     * tick. */
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
    public boolean shouldRenderOffScreen(TileLaser tile) {
        return true;
    }

    @Override
    public int getViewDistance() {
        return 2 * (TileLaser.TARGETING_RANGE + 1);
    }
}
