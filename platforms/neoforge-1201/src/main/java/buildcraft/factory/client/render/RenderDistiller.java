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
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;

import buildcraft.api.properties.BuildCraftProperties;

import buildcraft.factory.block.BlockDistiller;
import buildcraft.factory.tile.TileDistiller;

/** 1.12.2's {@code RenderDistiller} -- see the 26.x copy for the geometry and what is not ported. */
public class RenderDistiller implements BlockEntityRenderer<TileDistiller> {

    public RenderDistiller(BlockEntityRendererProvider.Context context) {}

    static int turnsFromWest(Direction facing) {
        return switch (facing) {
            case NORTH -> 1;
            case EAST -> 2;
            case SOUTH -> 3;
            default -> 0;
        };
    }

    @Override
    public void render(TileDistiller tile, float partialTick, PoseStack poseStack, MultiBufferSource buffer, int light,
        int overlay) {
        BlockState blockState = tile.getBlockState();
        if (!(blockState.getBlock() instanceof BlockDistiller)) {
            return;
        }
        int turns = turnsFromWest(blockState.getValue(BuildCraftProperties.BLOCK_FACING));
        double s = 1 / 64.0;
        double[][] in = FluidBoxRenderer.tankSize(0, 0, 4, 8, 16, 12, s, s, s, turns);
        double[][] gas = FluidBoxRenderer.tankSize(8, 8, 0, 16, 16, 16, s, s, s, turns);
        double[][] liquid = FluidBoxRenderer.tankSize(8, 0, 0, 16, 8, 16, s, s, s, turns);
        FluidBoxRenderer.tankBox(tile.tankIn, in[0], in[1], poseStack, buffer, light);
        FluidBoxRenderer.tankBox(tile.tankGasOut, gas[0], gas[1], poseStack, buffer, light);
        FluidBoxRenderer.tankBox(tile.tankLiquidOut, liquid[0], liquid[1], poseStack, buffer, light);
    }
}
