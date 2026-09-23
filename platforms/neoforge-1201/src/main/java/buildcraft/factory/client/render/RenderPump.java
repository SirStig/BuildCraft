/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * Copyright (c) 2026 Joshua Kac -- NeoForge port (BuildCraft 10)
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL
 * was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.factory.client.render;

import java.util.List;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Axis;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.phys.Vec3;

import buildcraft.lib.client.render.laser.CompiledLaser;
import buildcraft.lib.client.render.laser.LaserData_BC8;
import buildcraft.lib.client.render.laser.LaserRenderer_BC8;
import buildcraft.lib.client.render.tile.RenderPartCube;

import buildcraft.core.client.BuildCraftLaserManager;
import buildcraft.factory.tile.TilePump;

/** Mirrors the 26.x class of the same name -- see that one's javadoc for the full account of what this reproduces
 * from 1.12.2 (the four-sided LED layout, the pump-specific colour ramp, and why the power LED never glows while
 * the status LED does). This file differs only in the usual 1.20.1 places already established by
 * {@code RenderMiningWell}: the classic {@code render(...)} contract and {@code LightTexture}'s real
 * {@code pack}/{@code FULL_BRIGHT} API in place of 26.x's {@code LightCoordsUtil}. */
public class RenderPump implements BlockEntityRenderer<TilePump> {

    private static final ResourceLocation WHITE_SPRITE = new ResourceLocation("block/white_concrete");

    private static final float LED_POWER_OFFSET = 1.5f / 16f;
    private static final float LED_STATUS_OFFSET = 3.5f / 16f;
    private static final float LED_Y = 13.5f / 16f;
    private static final float LED_HALF_SIZE = 1f / 32f;

    private static final int COLOUR_STATUS_ON = 0xFF_77_DD_77; // a light green
    private static final int COLOUR_STATUS_OFF = 0xFF_1F_10_1B; // black-ish

    private static final int[] COLOUR_POWER = new int[16];
    private static final Direction[] SIDES = new Direction[4];
    private static final float[] POWER_X = new float[4];
    private static final float[] POWER_Z = new float[4];
    private static final float[] STATUS_X = new float[4];
    private static final float[] STATUS_Z = new float[4];

    static {
        for (int i = 0; i < COLOUR_POWER.length; i++) {
            int c = (i * 0x40) / COLOUR_POWER.length;
            int r = (i * 0xE0) / COLOUR_POWER.length + 0x1F;
            COLOUR_POWER[i] = (0xFF << 24) + (c << 16) + (c << 8) + r;
        }
        for (int i = 0; i < 4; i++) {
            Direction facing = Direction.from2DDataValue(i);
            SIDES[i] = facing;

            final int dX, dZ;
            final float ledX, ledZ;
            if (facing.getAxis() == Axis.X) {
                dX = 0;
                dZ = facing.getAxisDirection().getStep();
                ledZ = 0.5f;
                ledX = facing == Direction.EAST ? 15.6f / 16f : 0.4f / 16f;
            } else {
                dX = -facing.getAxisDirection().getStep();
                dZ = 0;
                ledX = 0.5f;
                ledZ = facing == Direction.SOUTH ? 15.6f / 16f : 0.4f / 16f;
            }
            POWER_X[i] = ledX + dX * LED_POWER_OFFSET;
            POWER_Z[i] = ledZ + dZ * LED_POWER_OFFSET;
            STATUS_X[i] = ledX + dX * LED_STATUS_OFFSET;
            STATUS_Z[i] = ledZ + dZ * LED_STATUS_OFFSET;
        }
    }

    public RenderPump(BlockEntityRendererProvider.Context context) {}

    @Override
    public void render(TilePump tile, float partialTick, PoseStack poseStack, MultiBufferSource buffers,
        int packedLight, int packedOverlay) {
        Level level = tile.getLevel();
        BlockPos pos = tile.getBlockPos();
        boolean complete = tile.isComplete();
        float percentFilled = tile.getPercentFilledForRender();

        TextureAtlasSprite sprite = LaserRenderer_BC8.getSprite(WHITE_SPRITE);
        int powerColour = COLOUR_POWER[(int) (percentFilled * (COLOUR_POWER.length - 1))];
        int statusColour = complete ? COLOUR_STATUS_OFF : COLOUR_STATUS_ON;
        VertexConsumer consumer = buffers.getBuffer(RenderType.entityTranslucent(InventoryMenu.BLOCK_ATLAS));
        PoseStack.Pose pose = poseStack.last();

        for (int i = 0; i < 4; i++) {
            BlockPos neighbour = pos.relative(SIDES[i]);
            // The power LED never glows -- see the 26.x copy's javadoc for why this genuinely differs from
            // RenderMiningWell.
            RenderPartCube.render(pose, consumer, sprite, POWER_X[i], LED_Y, POWER_Z[i], LED_HALF_SIZE, powerColour,
                packLight(level, neighbour, false));
            RenderPartCube.render(pose, consumer, sprite, STATUS_X[i], LED_Y, STATUS_Z[i], LED_HALF_SIZE,
                statusColour, packLight(level, neighbour, !complete));
        }

        int wantedLength = tile.getWantedLength();
        if (!complete && wantedLength > 0) {
            Vec3 start = new Vec3(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
            Vec3 end = new Vec3(pos.getX() + 0.5, pos.getY() - wantedLength, pos.getZ() + 0.5);
            LaserData_BC8 data = new LaserData_BC8(BuildCraftLaserManager.TUBE_PUMP, start, end, 1 / 16.0);
            CompiledLaser laser = LaserRenderer_BC8.compile(data);
            LaserRenderer_BC8.render(poseStack, buffers, List.of(laser), Vec3.atLowerCornerOf(pos));
        }
    }

    @Override
    public boolean shouldRenderOffScreen(TilePump tile) {
        return true;
    }

    /** See {@code RenderMiningWell}'s copy of this method's javadoc. */
    private static int packLight(Level level, BlockPos pos, boolean glow) {
        if (glow) {
            return LightTexture.pack(15, level.getBrightness(LightLayer.SKY, pos));
        }
        return LightTexture.pack(level.getBrightness(LightLayer.BLOCK, pos), level.getBrightness(LightLayer.SKY, pos));
    }
}
