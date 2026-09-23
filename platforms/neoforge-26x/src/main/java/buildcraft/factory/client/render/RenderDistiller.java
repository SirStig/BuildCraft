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
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import buildcraft.api.properties.BuildCraftProperties;

import buildcraft.lib.fluid.Tank;

import buildcraft.factory.block.BlockDistiller;
import buildcraft.factory.tile.TileDistiller;

/**
 * 1.12.2's {@code RenderDistiller}: the three tanks' fluid, drawn inside the machine's three glass tanks. The tank
 * boxes are 1.12.2's own ({@code TankSize}s for a west-facing distiller, shrunk by 1/64, rotated to the block's
 * facing with {@code TankSize#rotateY}): input {@code (0,0,4)-(8,16,12)}, gas output {@code (8,8,0)-(16,16,16)},
 * liquid output {@code (8,0,0)-(16,8,16)}. Drawn through {@link FluidBoxRenderer}.
 *
 * <p><b>Not ported:</b> the other half of 1.12.2's renderer, the two animated "power" pistons, which were an
 * expression-driven {@code ModelHolderVariable} ({@code models/tiles/distiller.json}: bob height and texture
 * picked per frame from {@code active}/{@code power_average}). No variable-model system exists in this port; the
 * pistons are instead part of the static block model at their resting position with the "off" texture -- exactly
 * what 1.12.2's own item model showed.
 */
public class RenderDistiller implements BlockEntityRenderer<TileDistiller, RenderDistiller.DistillerRenderState> {

    public RenderDistiller(BlockEntityRendererProvider.Context context) {}

    @Override
    public DistillerRenderState createRenderState() {
        return new DistillerRenderState();
    }

    /** West = 0 turns, then each {@code rotateY} (clockwise from above) -- 1.12.2's {@code TANK_SIZES} loop. */
    static int turnsFromWest(Direction facing) {
        return switch (facing) {
            case NORTH -> 1;
            case EAST -> 2;
            case SOUTH -> 3;
            default -> 0;
        };
    }

    @Override
    public void extractRenderState(TileDistiller tile, DistillerRenderState state, float partialTick, Vec3 cameraPosition,
        ModelFeatureRenderer.CrumblingOverlay breakProgress) {
        BlockEntityRenderer.super.extractRenderState(tile, state, partialTick, cameraPosition, breakProgress);
        state.boxes.clear();
        BlockState blockState = tile.getBlockState();
        if (!(blockState.getBlock() instanceof BlockDistiller)) {
            return;
        }
        int turns = turnsFromWest(blockState.getValue(BuildCraftProperties.BLOCK_FACING));
        double s = 1 / 64.0;
        add(state, tile.tankIn, FluidBoxRenderer.tankSize(0, 0, 4, 8, 16, 12, s, s, s, turns));
        add(state, tile.tankGasOut, FluidBoxRenderer.tankSize(8, 8, 0, 16, 16, 16, s, s, s, turns));
        add(state, tile.tankLiquidOut, FluidBoxRenderer.tankSize(8, 0, 0, 16, 8, 16, s, s, s, turns));
    }

    private static void add(DistillerRenderState state, Tank tank, double[][] size) {
        FluidBoxRenderer.Box box = FluidBoxRenderer.tankBox(tank, size[0], size[1]);
        if (box != null) {
            state.boxes.add(box);
        }
    }

    @Override
    public void submit(DistillerRenderState state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
        FluidBoxRenderer.submit(state.boxes, state.lightCoords, poseStack, collector);
    }

    public static final class DistillerRenderState extends BlockEntityRenderState {
        final List<FluidBoxRenderer.Box> boxes = new ArrayList<>();
    }
}
