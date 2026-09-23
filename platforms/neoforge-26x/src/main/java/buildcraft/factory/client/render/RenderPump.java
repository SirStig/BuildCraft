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

import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Axis;
import net.minecraft.resources.Identifier;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import buildcraft.lib.client.render.laser.CompiledLaser;
import buildcraft.lib.client.render.laser.LaserData_BC8;
import buildcraft.lib.client.render.laser.LaserRenderer_BC8;
import buildcraft.lib.client.render.tile.RenderPartCube;

import buildcraft.core.client.BuildCraftLaserManager;
import buildcraft.factory.tile.TilePump;

/** Draws the pump's eight status LEDs (a power/status pair on each of its four horizontal sides -- a pump, unlike
 * the mining well, has no facing property, so every side gets one) and its retracting intake-tube laser --
 * 1.12.2's {@code common/buildcraft/factory/client/render/RenderPump.java} (155 lines). See
 * {@link RenderMiningWell}'s own javadoc for the shared design (the LED colour ramp/light-sampling approach, the
 * tube laser, and the global-rendering override); this class differs from it in two real ways, not just which
 * tile it reads:
 *
 * <ul>
 * <li><b>Four LED pairs, one per {@code Direction.from2DDataValue(0..3)}</b> (1.12.2's {@code EnumFacing
 * .getHorizontal(i)}), at fixed offsets from each side's centre -- {@code POWER = 1.5/16}, {@code STATUS = 3.5/16},
 * {@code Y = 13.5/16} (near the top of the block, unlike the mining well's mid-height {@code 5.5/16}) -- computed
 * once into {@link #POWER_X}/{@link #POWER_Z}/{@link #STATUS_X}/{@link #STATUS_Z} since the geometry never
 * changes, only the colours and per-side light do.</li>
 * <li><b>The power LED never glows.</b> A real, deliberate difference from the mining well, confirmed by reading
 * 1.12.2's own code rather than assumed: {@code RenderPump}'s {@code LED_POWER[i].center.lighti(block, sky)} takes
 * the neighbour's plain ambient light with no {@code maxLighti} floor at all, while only
 * {@code LED_STATUS[i].center.lighti(Math.max(statusLight, block), sky)} forces a floor (to full block light while
 * {@link TilePump#isComplete()} is {@code false}) -- unlike {@code RenderMiningWell}, where <em>both</em> LEDs get
 * a {@code maxLighti} floor. {@link #packLight} takes an explicit {@code glow} flag for exactly this reason,
 * called {@code false} for the power LED here and {@code true} there.</li>
 * </ul>
 *
 * <p>The pump's own colour-ramp constants ({@code COLOUR_POWER}'s {@code 0xE0}/{@code 0x1F}, versus the mining
 * well's {@code 0xB0}/{@code 0x4F}) are 1.12.2's own, copied verbatim rather than shared -- the two originals never
 * shared this gradient either. */
public class RenderPump implements BlockEntityRenderer<TilePump, RenderPump.PumpRenderState> {

    private static final Identifier WHITE_SPRITE = Identifier.withDefaultNamespace("block/white_concrete");

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
    public PumpRenderState createRenderState() {
        return new PumpRenderState();
    }

    @Override
    public void extractRenderState(TilePump tile, PumpRenderState state, float partialTick, Vec3 cameraPosition,
        ModelFeatureRenderer.CrumblingOverlay breakProgress) {
        BlockEntityRenderer.super.extractRenderState(tile, state, partialTick, cameraPosition, breakProgress);

        Level level = tile.getLevel();
        BlockPos pos = tile.getBlockPos();
        boolean complete = tile.isComplete();
        float percentFilled = tile.getPercentFilledForRender();

        state.sprite = LaserRenderer_BC8.getSprite(WHITE_SPRITE);
        state.powerColour = COLOUR_POWER[(int) (percentFilled * (COLOUR_POWER.length - 1))];
        state.statusColour = complete ? COLOUR_STATUS_OFF : COLOUR_STATUS_ON;

        for (int i = 0; i < 4; i++) {
            BlockPos neighbour = pos.relative(SIDES[i]);
            // The power LED never glows -- see this class's own javadoc for why this genuinely differs from
            // RenderMiningWell, where both LEDs do.
            state.powerLight[i] = packLight(level, neighbour, false);
            state.statusLight[i] = packLight(level, neighbour, !complete);
        }

        int wantedLength = tile.getWantedLength();
        if (complete || wantedLength <= 0) {
            state.laser = null;
        } else {
            Vec3 start = new Vec3(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
            Vec3 end = new Vec3(pos.getX() + 0.5, pos.getY() - wantedLength, pos.getZ() + 0.5);
            LaserData_BC8 data = new LaserData_BC8(BuildCraftLaserManager.TUBE_PUMP, start, end, 1 / 16.0);
            state.laser = LaserRenderer_BC8.compile(data);
        }
    }

    @Override
    public void submit(PumpRenderState state, PoseStack poseStack, SubmitNodeCollector submitNodeCollector,
        CameraRenderState camera) {
        submitNodeCollector.submitCustomGeometry(poseStack, RenderTypes.entityTranslucent(TextureAtlas.LOCATION_BLOCKS),
            (pose, consumer) -> {
                for (int i = 0; i < 4; i++) {
                    RenderPartCube.render(pose, consumer, state.sprite, POWER_X[i], LED_Y, POWER_Z[i],
                        LED_HALF_SIZE, state.powerColour, state.powerLight[i]);
                    RenderPartCube.render(pose, consumer, state.sprite, STATUS_X[i], LED_Y, STATUS_Z[i],
                        LED_HALF_SIZE, state.statusColour, state.statusLight[i]);
                }
            });
        if (state.laser != null) {
            LaserRenderer_BC8.submit(submitNodeCollector, poseStack, List.of(state.laser),
                Vec3.atLowerCornerOf(state.blockPos));
        }
    }

    @Override
    public boolean shouldRenderOffScreen() {
        return true;
    }

    @Override
    public AABB getRenderBoundingBox(TilePump tile) {
        BlockPos pos = tile.getBlockPos();
        int depth = Math.max(tile.getWantedLength(), 0);
        return new AABB(pos.getX(), pos.getY() - depth, pos.getZ(), pos.getX() + 1, pos.getY() + 1, pos.getZ() + 1);
    }

    /** See {@code RenderMiningWell}'s copy of this method's javadoc. */
    private static int packLight(Level level, BlockPos pos, boolean glow) {
        if (glow) {
            return LightCoordsUtil.pack(15, level.getBrightness(LightLayer.SKY, pos));
        }
        return LightCoordsUtil.pack(level.getBrightness(LightLayer.BLOCK, pos), level.getBrightness(LightLayer.SKY, pos));
    }

    /** Everything {@link #submit} needs, captured once per frame by {@link #extractRenderState}. */
    public static final class PumpRenderState extends BlockEntityRenderState {
        TextureAtlasSprite sprite;
        int powerColour;
        int statusColour;
        final int[] powerLight = new int[4];
        final int[] statusLight = new int[4];
        CompiledLaser laser;
    }
}
