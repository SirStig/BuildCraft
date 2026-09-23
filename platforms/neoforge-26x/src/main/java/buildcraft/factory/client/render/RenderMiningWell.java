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

import buildcraft.api.properties.BuildCraftProperties;

import buildcraft.lib.client.render.laser.CompiledLaser;
import buildcraft.lib.client.render.laser.LaserData_BC8;
import buildcraft.lib.client.render.laser.LaserRenderer_BC8;
import buildcraft.lib.client.render.tile.RenderPartCube;

import buildcraft.core.client.BuildCraftLaserManager;
import buildcraft.factory.tile.TileMiningWell;

/** Draws the mining well's two status LEDs and its retracting intake-tube laser -- 1.12.2's
 * {@code common/buildcraft/factory/client/render/RenderMiningWell.java} (139 lines), ported onto this target's
 * {@code BlockEntityRenderer<T, S>} state-extraction contract, the same shape {@link RenderPartCube} and
 * {@code RenderTileTank}/{@code RenderMarkerVolume} already established.
 *
 * <p><b>The LEDs.</b> 1.12.2 built two small cubes with {@code RenderPartCube} (a mutable, per-instance object
 * re-positioned every frame) and drew them with a plain white texture, tinted per-vertex: a "power" LED whose
 * colour ramps red -> orange as {@link TileMiningWell#getPercentFilledForRender()} climbs (the exact
 * {@code COLOUR_POWER} gradient formula below is copied verbatim), and a "status" LED that is light green while
 * still digging and near-black once {@link TileMiningWell#isComplete()}. Both sit on the block's front face (the
 * one {@link BuildCraftProperties#BLOCK_FACING} points to), offset from its centre by the exact 1.12.2 pixel
 * fractions ({@code POWER = 2.5/16}, {@code STATUS = 4.5/16}, {@code Y = 5.5/16}). See {@link RenderPartCube}'s own
 * javadoc for why this port reads them with a plain static method instead of a mutable object, and for why the
 * tint base is {@code minecraft:block/white_concrete} rather than a copied asset (1.12.2's own white texture,
 * {@code ModelLoader.White.INSTANCE}, was generated at runtime with no packaged PNG behind it).
 *
 * <p><b>Lighting.</b> 1.12.2 sampled {@code world.getCombinedLight(pos.offset(facing), 0)} once (the light of the
 * block the well faces into) and forced each LED to at least {@code 0xF} (full block light) while
 * "on" ({@code maxLighti}), so an active LED visibly glows even in the dark. Reproduced with
 * {@link Level#getBrightness(LightLayer, BlockPos)} for both light layers plus a {@code Math.max} floor, packed
 * with {@link LightCoordsUtil#pack(int, int)} -- confirmed via {@code javap} against the real 26.3 merged jar that
 * this target has no {@code net.minecraft.client.renderer.LightTexture} at all any more (zero matches in the whole
 * jar); {@code LightCoordsUtil} is its real replacement, already used the same way by {@link CompiledLaser}'s own
 * light sampling.
 *
 * <p><b>The tube laser</b> reuses {@link BuildCraftLaserManager#TUBE_MINING_WELL} (this machine's own retracting-
 * tube sprite, not a generic marker/power laser -- see that field's own javadoc) through the same
 * {@link LaserRenderer_BC8} pipeline {@code RenderMarkerVolume}/{@code RenderMarkerConnections} already use,
 * replacing 1.12.2's separate {@code RenderTube} helper class (its only other caller, {@code RenderPump}, gets its
 * own copy of the same few lines rather than sharing a third file for two call sites). The beam runs from the
 * tile's own position down to {@link TileMiningWell#getWantedLength()} blocks below -- deliberately <em>not</em>
 * 1.12.2's client-interpolated {@code getLength(partialTicks)}; see {@code TileMiner}'s own javadoc for why this
 * port reads the raw, synced length directly and snaps to it rather than easing towards it.
 *
 * <p><b>Global rendering.</b> 1.12.2's {@code isGlobalRenderer(TileMiningWell) = true} is reproduced as
 * {@link #shouldRenderOffScreen()} (this target's {@code IBlockEntityRendererExtension} hook, matching
 * {@code RenderMarkerVolume}'s own precedent) plus {@link #getRenderBoundingBox}, widened down to the shaft's
 * current depth rather than left at the block's own 1x1x1 box -- a beam reaching hundreds of blocks below the
 * tile must not be culled just because the tile's own chunk section fell out of the frustum. */
public class RenderMiningWell implements BlockEntityRenderer<TileMiningWell, RenderMiningWell.MiningWellRenderState> {

    private static final Identifier WHITE_SPRITE = Identifier.withDefaultNamespace("block/white_concrete");

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
    public MiningWellRenderState createRenderState() {
        return new MiningWellRenderState();
    }

    @Override
    public void extractRenderState(TileMiningWell tile, MiningWellRenderState state, float partialTick,
        Vec3 cameraPosition, ModelFeatureRenderer.CrumblingOverlay breakProgress) {
        BlockEntityRenderer.super.extractRenderState(tile, state, partialTick, cameraPosition, breakProgress);

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

        state.sprite = LaserRenderer_BC8.getSprite(WHITE_SPRITE);

        state.powerX = ledX + dX * LED_POWER_OFFSET;
        state.powerZ = ledZ + dZ * LED_POWER_OFFSET;
        state.powerColour = COLOUR_POWER[colourIndex];
        state.powerLight = packLight(level, facePos, percentFilled > 0.01f);

        state.statusX = ledX + dX * LED_STATUS_OFFSET;
        state.statusZ = ledZ + dZ * LED_STATUS_OFFSET;
        state.statusColour = complete ? COLOUR_STATUS_OFF : COLOUR_STATUS_ON;
        state.statusLight = packLight(level, facePos, !complete);

        int wantedLength = tile.getWantedLength();
        if (complete || wantedLength <= 0) {
            state.laser = null;
        } else {
            Vec3 start = new Vec3(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
            Vec3 end = new Vec3(pos.getX() + 0.5, pos.getY() - wantedLength, pos.getZ() + 0.5);
            LaserData_BC8 data = new LaserData_BC8(BuildCraftLaserManager.TUBE_MINING_WELL, start, end, 1 / 16.0);
            state.laser = LaserRenderer_BC8.compile(data);
        }
    }

    @Override
    public void submit(MiningWellRenderState state, PoseStack poseStack, SubmitNodeCollector submitNodeCollector,
        CameraRenderState camera) {
        submitNodeCollector.submitCustomGeometry(poseStack, RenderTypes.entityTranslucent(TextureAtlas.LOCATION_BLOCKS),
            (pose, consumer) -> {
                RenderPartCube.render(pose, consumer, state.sprite, state.powerX, LED_Y, state.powerZ,
                    LED_HALF_SIZE, state.powerColour, state.powerLight);
                RenderPartCube.render(pose, consumer, state.sprite, state.statusX, LED_Y, state.statusZ,
                    LED_HALF_SIZE, state.statusColour, state.statusLight);
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
    public AABB getRenderBoundingBox(TileMiningWell tile) {
        BlockPos pos = tile.getBlockPos();
        int depth = Math.max(tile.getWantedLength(), 0);
        return new AABB(pos.getX(), pos.getY() - depth, pos.getZ(), pos.getX() + 1, pos.getY() + 1, pos.getZ() + 1);
    }

    /** 1.12.2's {@code maxLighti}: the real light of {@code pos}, floored to full block light while {@code glow}
     * is set, so an active LED reads as lit even in the dark. */
    private static int packLight(Level level, BlockPos pos, boolean glow) {
        if (glow) {
            return LightCoordsUtil.pack(15, level.getBrightness(LightLayer.SKY, pos));
        }
        return LightCoordsUtil.pack(level.getBrightness(LightLayer.BLOCK, pos), level.getBrightness(LightLayer.SKY, pos));
    }

    /** Everything {@link #submit} needs, captured once per frame by {@link #extractRenderState}. */
    public static final class MiningWellRenderState extends BlockEntityRenderState {
        TextureAtlasSprite sprite;
        float powerX, powerZ;
        int powerColour;
        int powerLight;
        float statusX, statusZ;
        int statusColour;
        int statusLight;
        CompiledLaser laser;
    }
}
