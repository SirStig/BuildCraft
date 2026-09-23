/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * Copyright (c) 2026 Joshua Kac -- NeoForge port (BuildCraft 10)
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL
 * was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.factory.client.render;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

import net.minecraftforge.fluids.FluidStack;

import buildcraft.lib.fluid.Tank;

import buildcraft.factory.tile.TileHeatExchange;
import buildcraft.factory.tile.TileHeatExchange.EnumProgressState;
import buildcraft.factory.tile.TileHeatExchange.ExchangeSectionEnd;
import buildcraft.factory.tile.TileHeatExchange.ExchangeSectionStart;

/** 1.12.2's {@code RenderHeatExchange} -- see the 26.x copy for the geometry and the simplified flow. The start
 * tile widens its own {@code getRenderBoundingBox} so the whole line stays visible. */
public class RenderHeatExchange implements BlockEntityRenderer<TileHeatExchange> {

    public RenderHeatExchange(BlockEntityRendererProvider.Context context) {}

    private static int turnsFromEast(Direction dir) {
        return switch (dir) {
            case SOUTH -> 1;
            case WEST -> 2;
            case NORTH -> 3;
            default -> 0;
        };
    }

    @Override
    public void render(TileHeatExchange tile, float partialTick, PoseStack poseStack, MultiBufferSource buffer, int light,
        int overlay) {
        if (!(tile.getSection() instanceof ExchangeSectionStart section)) {
            return;
        }
        Direction facing = tile.getFacing();
        if (facing == null) {
            return;
        }
        Direction face = facing.getCounterClockWise();
        int turns = turnsFromEast(face);
        double s = 1 / 64.0;
        tank(section.tankInput, FluidBoxRenderer.tankSize(2, 0, 2, 14, 2, 14, s, 0, s, 0), BlockPos.ZERO, poseStack, buffer,
            light);
        tank(section.tankOutput, FluidBoxRenderer.tankSize(0, 4, 4, 2, 12, 12, 0, s, s, turns), BlockPos.ZERO, poseStack,
            buffer, light);

        ExchangeSectionEnd end = tile.findEndSectionClient();
        if (end == null) {
            return;
        }
        BlockPos diff = end.getTile().getBlockPos().subtract(tile.getBlockPos());
        tank(end.tankOutput, FluidBoxRenderer.tankSize(2, 14, 2, 14, 16, 14, s, 0, s, 0), diff, poseStack, buffer, light);
        tank(end.tankInput, FluidBoxRenderer.tankSize(14, 4, 4, 16, 12, 12, 0, s, s, turns), diff, poseStack, buffer, light);

        int middles = section.middleCount;
        double progress = section.getProgress(partialTick);
        if (middles > 0 && progress > 0 && section.getProgressState() != EnumProgressState.OFF) {
            double p0 = 2 / 16.0 + 0.01;
            double length = middles + 2 - 4 / 16.0 - 0.02;
            flow(section.tankInput.getFluid(), face, p0, p0 + length * progress, 2, poseStack, buffer, light);
            flow(end.tankInput.getFluid(), face, p0 + length * (1 - progress), p0 + length, 4, poseStack, buffer, light);
        }
    }

    private static void tank(Tank tank, double[][] size, BlockPos d, PoseStack poseStack, MultiBufferSource buffer,
        int light) {
        FluidBoxRenderer.tankBox(tank, new double[] { size[0][0] + d.getX(), size[0][1] + d.getY(), size[0][2] + d.getZ() },
            new double[] { size[1][0] + d.getX(), size[1][1] + d.getY(), size[1][2] + d.getZ() }, poseStack, buffer, light);
    }

    private static void flow(FluidStack fluid, Direction dirToEnd, double t0, double t1, int point, PoseStack poseStack,
        MultiBufferSource buffer, int light) {
        if (fluid.isEmpty() || t1 <= t0) {
            return;
        }
        double in0 = (point + 0.1) / 16.0;
        double in1 = 1 - in0;
        double a0, a1;
        if (dirToEnd.getAxisDirection() == Direction.AxisDirection.POSITIVE) {
            a0 = t0;
            a1 = t1;
        } else {
            a0 = 1 - t1;
            a1 = 1 - t0;
        }
        if (dirToEnd.getAxis() == Direction.Axis.X) {
            FluidBoxRenderer.box(fluid, a0, in0, in0, a1, in1, in1, poseStack, buffer, light);
        } else {
            FluidBoxRenderer.box(fluid, in0, in0, a0, in1, in1, a1, poseStack, buffer, light);
        }
    }

    @Override
    public boolean shouldRenderOffScreen(TileHeatExchange tile) {
        return tile.isStart();
    }
}
