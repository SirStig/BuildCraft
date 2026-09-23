/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * Copyright (c) 2026 Joshua Kac -- NeoForge port (BuildCraft 10)
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL
 * was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.factory.client.render;

import java.util.ArrayList;
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
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import net.neoforged.neoforge.transfer.fluid.FluidResource;

import buildcraft.lib.fluid.Tank;

import buildcraft.factory.tile.TileHeatExchange;
import buildcraft.factory.tile.TileHeatExchange.EnumProgressState;
import buildcraft.factory.tile.TileHeatExchange.ExchangeSectionEnd;
import buildcraft.factory.tile.TileHeatExchange.ExchangeSectionStart;

/**
 * 1.12.2's {@code RenderHeatExchange}, drawn from the start block only (1.12.2: {@code isGlobalRenderer(tile) ->
 * tile.isStart()}; here {@link #getRenderBoundingBox} widens the start block's box to cover the whole line). The
 * four tank boxes are 1.12.2's own: the start's input at the bottom {@code (2,0,2)-(14,2,14)}, the start's output
 * in its outer side {@code (0,4,4)-(2,12,12)}, the end's output at its top {@code (2,14,2)-(14,16,14)} and the end's
 * input in its outer side {@code (14,4,4)-(16,12,12)} -- the side boxes rotated from 1.12.2's east-based
 * {@code TANK_SIDES} table to {@code facing.getCounterClockWise()}.
 *
 * <p><b>Flow (simplified):</b> while {@code progress > 0} 1.12.2 drew the two streams along the pipe as scrolling
 * {@code FROZEN}-sprite segments, one block at a time, with the anchored end flipping between warm-up and
 * cool-down. This draws each stream as one straight box across the whole line, from 2/16 into the start block to
 * 2/16 short of the end block's far side (1.12.2's {@code p0}/{@code length}), grown by the same
 * {@code getProgress(partialTicks)} fraction: the heated fluid (outer pipe, inset 2/16) from the start end, the
 * coolant (inner pipe, inset 4/16) from the end end. No texture scrolling and no warm-up/cool-down anchor flip.
 */
public class RenderHeatExchange implements BlockEntityRenderer<TileHeatExchange, RenderHeatExchange.HeatExchangeRenderState> {

    public RenderHeatExchange(BlockEntityRendererProvider.Context context) {}

    @Override
    public HeatExchangeRenderState createRenderState() {
        return new HeatExchangeRenderState();
    }

    @Override
    public AABB getRenderBoundingBox(TileHeatExchange tile) {
        if (tile.isStart()) {
            return new AABB(tile.getBlockPos()).inflate(6);
        }
        return new AABB(tile.getBlockPos());
    }

    /** East = 0 turns -- 1.12.2's {@code TANK_SIDES} loop started from {@code EnumFacing.EAST}. */
    private static int turnsFromEast(Direction dir) {
        return switch (dir) {
            case SOUTH -> 1;
            case WEST -> 2;
            case NORTH -> 3;
            default -> 0;
        };
    }

    @Override
    public void extractRenderState(TileHeatExchange tile, HeatExchangeRenderState state, float partialTick,
        Vec3 cameraPosition, ModelFeatureRenderer.CrumblingOverlay breakProgress) {
        BlockEntityRenderer.super.extractRenderState(tile, state, partialTick, cameraPosition, breakProgress);
        state.boxes.clear();
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
        addTank(state, section.tankInput, FluidBoxRenderer.tankSize(2, 0, 2, 14, 2, 14, s, 0, s, 0), 0, 0, 0);
        addTank(state, section.tankOutput, FluidBoxRenderer.tankSize(0, 4, 4, 2, 12, 12, 0, s, s, turns), 0, 0, 0);

        ExchangeSectionEnd end = tile.findEndSectionClient();
        if (end == null) {
            return;
        }
        BlockPos diff = end.getTile().getBlockPos().subtract(tile.getBlockPos());
        addTank(state, end.tankOutput, FluidBoxRenderer.tankSize(2, 14, 2, 14, 16, 14, s, 0, s, 0), diff.getX(), diff.getY(),
            diff.getZ());
        addTank(state, end.tankInput, FluidBoxRenderer.tankSize(14, 4, 4, 16, 12, 12, 0, s, s, turns), diff.getX(),
            diff.getY(), diff.getZ());

        int middles = section.middleCount;
        double progress = section.getProgress(partialTick);
        if (middles > 0 && progress > 0 && section.getProgressState() != EnumProgressState.OFF) {
            double p0 = 2 / 16.0 + 0.01;
            double length = middles + 2 - 4 / 16.0 - 0.02;
            // Heated fluid: from the start end towards the end block.
            addFlow(state, section.tankInput.getFluidType(), face, p0, p0 + length * progress, 2);
            // Coolant: from the end block back towards the start.
            addFlow(state, end.tankInput.getFluidType(), face, p0 + length * (1 - progress), p0 + length, 4);
        }
    }

    private static void addTank(HeatExchangeRenderState state, Tank tank, double[][] size, int dx, int dy, int dz) {
        FluidBoxRenderer.Box box = FluidBoxRenderer.tankBox(tank, new double[] { size[0][0] + dx, size[0][1] + dy,
            size[0][2] + dz }, new double[] { size[1][0] + dx, size[1][1] + dy, size[1][2] + dz });
        if (box != null) {
            state.boxes.add(box);
        }
    }

    /** A flow box from {@code t0} to {@code t1} blocks along {@code dirToEnd}, measured from the start block's far
     * side, inset {@code point}/16 (+0.1 px) on the other two axes -- 1.12.2's {@code renderFlow} geometry. */
    private static void addFlow(HeatExchangeRenderState state, FluidResource fluid, Direction dirToEnd, double t0,
        double t1, int point) {
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
        FluidBoxRenderer.Box box = dirToEnd.getAxis() == Direction.Axis.X
            ? FluidBoxRenderer.box(fluid, a0, in0, in0, a1, in1, in1)
            : FluidBoxRenderer.box(fluid, in0, in0, a0, in1, in1, a1);
        if (box != null) {
            state.boxes.add(box);
        }
    }

    @Override
    public void submit(HeatExchangeRenderState state, PoseStack poseStack, SubmitNodeCollector collector,
        CameraRenderState camera) {
        FluidBoxRenderer.submit(state.boxes, state.lightCoords, poseStack, collector);
    }

    public static final class HeatExchangeRenderState extends BlockEntityRenderState {
        final List<FluidBoxRenderer.Box> boxes = new ArrayList<>();
    }
}
