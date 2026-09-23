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

import buildcraft.api.properties.BuildCraftProperties;

import buildcraft.lib.client.render.laser.CompiledLaser;
import buildcraft.lib.client.render.laser.LaserData_BC8;
import buildcraft.lib.client.render.laser.LaserRenderer_BC8;
import buildcraft.lib.client.render.tile.RenderPartCube;

import buildcraft.core.client.BuildCraftLaserManager;
import buildcraft.factory.tile.TileMiningWell;

/** Mirrors the 26.x class of the same name -- see that one's javadoc for the full account of what this reproduces
 * from 1.12.2's {@code RenderMiningWell}/{@code RenderTube} (the LED geometry/colour ramps, the light-sampling
 * rule, and why the tube laser reads {@link TileMiningWell#getWantedLength()} directly rather than a client-
 * interpolated length). This file differs only in the usual 1.20.1 places: the classic immediate-mode
 * {@code render(...)} contract rather than the extract/{@code submit} split, and the real light-packing API.
 *
 * <p><b>Real light-packing API -- genuinely different from 26.x, confirmed via {@code javap} against the real
 * Forge 1.20.1 universal jar.</b> Unlike 26.x (where {@code net.minecraft.client.renderer.LightTexture} does not
 * exist at all any more -- see the 26.x copy's javadoc), this target's classic
 * {@code net.minecraft.client.renderer.LightTexture} still has {@code pack(int, int)} and {@code FULL_BRIGHT},
 * the same API {@code CompiledLaser}'s own 1.20.1 copy already uses for exactly the same purpose.
 *
 * <p><b>Global rendering</b> is split differently here too: 1.12.2's {@code isGlobalRenderer} maps to
 * {@link #shouldRenderOffScreen} alone (this target's {@code BlockEntityRenderer<T>} interface passes the tile in,
 * confirmed via {@code javap}, unlike 26.x's parameterless version). The matching widened render-bounding-box half
 * lives on {@code TileMiningWell}'s own base class instead -- confirmed via {@code javap} against
 * {@code net.minecraftforge.common.extensions.IForgeBlockEntity}: this target's {@code getRenderBoundingBox()} is
 * a zero-argument method on the block entity itself, not a renderer-level hook the way 26.x's
 * {@code IBlockEntityRendererExtension} makes it -- see {@code TileMiner}'s own override and
 * {@code TileHeatExchange}'s identical precedent for widening one instead of leaving the block's default 1x1x1
 * box in place. */
public class RenderMiningWell implements BlockEntityRenderer<TileMiningWell> {

    private static final ResourceLocation WHITE_SPRITE = new ResourceLocation("block/white_concrete");

    private static final float LED_POWER_OFFSET = 2.5f / 16f;
    private static final float LED_STATUS_OFFSET = 4.5f / 16f;
    private static final float LED_Y = 5.5f / 16f;
    private static final float LED_HALF_SIZE = 1f / 32f;

    private static final int COLOUR_STATUS_ON = 0xFF_77_DD_77; // a light green
    private static final int COLOUR_STATUS_OFF = 0xFF_1F_10_1B; // black-ish

    private static final int[] COLOUR_POWER = new int[16];

    static {
        for (int i = 0; i < COLOUR_POWER.length; i++) {
            int c = ((i * 0x40) / COLOUR_POWER.length) & 0xFF;
            int r = (((i * 0xB0) / COLOUR_POWER.length) & 0xFF) + 0x4F;
            COLOUR_POWER[i] = (0xFF << 24) | (c << 16) | (c << 8) | r;
        }
    }

    public RenderMiningWell(BlockEntityRendererProvider.Context context) {}

    @Override
    public void render(TileMiningWell tile, float partialTick, PoseStack poseStack, MultiBufferSource buffers,
        int packedLight, int packedOverlay) {
        Level level = tile.getLevel();
        BlockPos pos = tile.getBlockPos();
        Direction facing = tile.getBlockState().getValue(BuildCraftProperties.BLOCK_FACING);

        final int dX, dZ;
        final float ledX, ledZ;
        if (facing.getAxis() == Axis.X) {
            dX = 0;
            dZ = facing.getAxisDirection().getStep();
            ledZ = 0.5f;
            ledX = facing == Direction.EAST ? 15.8f / 16f : 0.2f / 16f;
        } else {
            dX = -facing.getAxisDirection().getStep();
            dZ = 0;
            ledX = 0.5f;
            ledZ = facing == Direction.SOUTH ? 15.8f / 16f : 0.2f / 16f;
        }

        boolean complete = tile.isComplete();
        float percentFilled = tile.getPercentFilledForRender();
        int colourIndex = (int) (percentFilled * (COLOUR_POWER.length - 1));
        BlockPos facePos = pos.relative(facing);

        TextureAtlasSprite sprite = LaserRenderer_BC8.getSprite(WHITE_SPRITE);
        VertexConsumer consumer = buffers.getBuffer(RenderType.entityTranslucent(InventoryMenu.BLOCK_ATLAS));
        PoseStack.Pose pose = poseStack.last();

        RenderPartCube.render(pose, consumer, sprite, ledX + dX * LED_POWER_OFFSET, LED_Y,
            ledZ + dZ * LED_POWER_OFFSET, LED_HALF_SIZE, COLOUR_POWER[colourIndex],
            packLight(level, facePos, percentFilled > 0.01f));
        RenderPartCube.render(pose, consumer, sprite, ledX + dX * LED_STATUS_OFFSET, LED_Y,
            ledZ + dZ * LED_STATUS_OFFSET, LED_HALF_SIZE, complete ? COLOUR_STATUS_OFF : COLOUR_STATUS_ON,
            packLight(level, facePos, !complete));

        int wantedLength = tile.getWantedLength();
        if (!complete && wantedLength > 0) {
            Vec3 start = new Vec3(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
            Vec3 end = new Vec3(pos.getX() + 0.5, pos.getY() - wantedLength, pos.getZ() + 0.5);
            LaserData_BC8 data = new LaserData_BC8(BuildCraftLaserManager.TUBE_MINING_WELL, start, end, 1 / 16.0);
            CompiledLaser laser = LaserRenderer_BC8.compile(data);
            LaserRenderer_BC8.render(poseStack, buffers, List.of(laser), Vec3.atLowerCornerOf(pos));
        }
    }

    @Override
    public boolean shouldRenderOffScreen(TileMiningWell tile) {
        return true;
    }

    /** See the 26.x copy of this method's javadoc. */
    private static int packLight(Level level, BlockPos pos, boolean glow) {
        if (glow) {
            return LightTexture.pack(15, level.getBrightness(LightLayer.SKY, pos));
        }
        return LightTexture.pack(level.getBrightness(LightLayer.BLOCK, pos), level.getBrightness(LightLayer.SKY, pos));
    }
}
