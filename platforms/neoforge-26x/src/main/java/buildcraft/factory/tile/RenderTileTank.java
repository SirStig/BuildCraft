/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * Copyright (c) 2026 Joshua Kac -- NeoForge port (BuildCraft 10)
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL
 * was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.factory.tile;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.color.block.BlockTintSource;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.block.FluidModel;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.Vec3;

import net.neoforged.neoforge.transfer.fluid.FluidResource;

/**
 * The second {@code BlockEntityRenderer} in this port, giving {@link TileTank} the fluid-level visual its own class
 * javadoc has always documented as deferred: {@code getFluidForRender} (client-side fluid-level interpolation) was
 * dropped from that class, and {@link buildcraft.lib.fluid.Tank}'s own dropped {@code clientFluid}/
 * {@code clientAmount}/{@code colorRenderCache} fields, purely for "there is no renderer in this port to consume
 * it" -- exactly the same "deferred, ready to re-add" situation {@code buildcraft.lib.engine.RenderTileEngine}
 * closed for the engines' piston rod. This class closes it here, reusing that class's own state-extraction/
 * {@code submit} split (see its javadoc for the full account of why this target's {@code BlockEntityRenderer<T, S>}
 * needs one at all).
 *
 * <p><b>No client-side interpolation field, unlike {@code RenderTileEngine}'s {@code clientProgress}/
 * {@code lastClientProgress}.</b> That machinery exists because a continuously-moving piston needs to look smooth
 * between infrequent full syncs. A tank's fill level is the opposite case: {@link Tank}'s {@code onChange}
 * callback is wired straight to {@link buildcraft.lib.tile.TileBC#markDirtyAndSync()} (see {@link TileTank}'s own
 * javadoc), which fires a full-NBT sync on *every* content change, not once a tick -- so whatever
 * {@link TileTank#tank} reads on the client is never more than one network round-trip stale, and a fluid level
 * only ever moves in small per-tick increments (a pipe or engine moves at most a few hundred mB of a multi-thousand
 * mB tank capacity per tick). The result is one small, immediately-corrected step per sync rather than the
 * continuous back-and-forth motion the piston rod needed to fake between syncs -- not worth a second smoothing
 * mechanism, so this reads {@link TileTank#tank} directly, every frame, off whatever the last sync left it at.
 *
 * <p><b>Real geometry from 1.12.2's own {@code RenderTank}</b> (120 lines, {@code common/buildcraft/factory/client
 * /render/RenderTank.java}): the fluid box sits inset from the tank's full-block bounds by {@link #X_MIN}/
 * {@link #X_MAX}/{@link #Z_MIN}/{@link #Z_MAX} ({@code 0.13}/{@code 0.86}), with {@link #Y_MIN}/{@link #Y_MAX}
 * ({@code 0.01}/{@code 0.99}) as the full-tank vertical range that the actual fill fraction
 * ({@code amount / capacity}, from {@link buildcraft.lib.fluid.Tank#getAmountAsInt(int)}/
 * {@link buildcraft.lib.fluid.Tank#getCapacity()}) scales up from {@link #Y_MIN}. <b>Deliberately cut from the
 * original:</b> the connected-tank seamless stretching ({@code MIN_CONNECTED}/{@code MAX_CONNECTED}, which pushed
 * the shared face of two full, same-fluid, vertically-adjacent tanks flush to the block edge so a tall stack reads
 * as one unbroken column) is real 1.12.2 behaviour, but re-deriving it correctly needs a neighbour-tank fluid/
 * fullness check on every frame for comparatively little payoff on a first pass whose actual on-screen result
 * cannot be checked visually in this project anyway (see the porting task's own verification limitation) -- this
 * renderer always draws the plain inset box. A later pass can add the stretch back by checking
 * {@link TileTank#canTanksConnect} against the neighbour above/below the way the original did.
 *
 * <p><b>Deliberately not a reproduction of 1.12.2's quad technique</b> (a bespoke {@code MutableQuad}/
 * {@code FluidRenderer}/immediate-mode {@code BufferBuilder} framework with no counterpart anywhere in this port,
 * the same call already made for {@code RenderTileEngine}). Instead this hand-builds one box (six faces, via
 * {@link SubmitNodeCollector#submitCustomGeometry}) with the fluid's own still sprite and tint, confirmed via
 * {@code javap} against the real 26.3 client jar to be sourced very differently here than on 1.20.1 -- see this
 * class's own note at {@link #extractRenderState}. Faces are drawn with a non-culling {@link RenderType}
 * ({@link RenderTypes#entityTranslucent(Identifier)}, not {@code entityTranslucentCull}) specifically because
 * getting a hand-built quad's winding order exactly right without being able to see the result on screen is a real
 * risk -- non-culling costs a few invisible backfaces on a single small box per tank and guarantees every face
 * actually draws regardless of winding.
 *
 * <p>Every face reuses the fluid's still texture (matching the original, which also only ever asked for
 * {@code FluidSpriteType.STILL}) at the same UV rect, not a separately-scaled top/side/bottom mapping -- a small,
 * honestly-scoped simplification, not a claim that the texture tiles seamlessly at every box size.
 */
public class RenderTileTank implements BlockEntityRenderer<TileTank, RenderTileTank.TankRenderState> {

    private static final float X_MIN = 0.13f;
    private static final float X_MAX = 0.86f;
    private static final float Y_MIN = 0.01f;
    private static final float Y_MAX = 0.99f;
    private static final float Z_MIN = 0.13f;
    private static final float Z_MAX = 0.86f;

    public RenderTileTank(BlockEntityRendererProvider.Context context) {}

    @Override
    public TankRenderState createRenderState() {
        return new TankRenderState();
    }

    /**
     * Confirmed via {@code javap} against the real 26.3 client jar: {@code net.neoforged.neoforge.client.extensions
     * .common.IClientFluidTypeExtensions} on this target no longer declares {@code getStillTexture()}/
     * {@code getTintColor()} at all (only fog/overlay hooks remain) -- a genuine platform divergence from 1.20.1,
     * not an oversight, because fluid rendering itself moved into vanilla as a real, data-driven system on this
     * target: {@code net.minecraft.client.renderer.block.FluidStateModelSet} (reachable from
     * {@link Minecraft#getModelManager()}{@code .getFluidStateModelSet()}) maps a {@link FluidState} to a
     * {@code FluidModel} record exposing {@code stillMaterial()}/{@code flowingMaterial()}/{@code overlayMaterial()}
     * (each a baked {@code Material} wrapping a real {@link TextureAtlasSprite}) and a {@code tintSource()}
     * ({@code BlockTintSource}, resolved to a colour via {@link FluidState#createLegacyBlock()} the same way
     * vanilla's own fluid renderer does). That is the real, current 26.x path this method uses in place of the
     * classic Forge/NeoForge {@code IClientFluidTypeExtensions} texture/tint pair 1.20.1 still has -- see the
     * 1.20.1 copy of this class for that older, still-current-there API.
     */
    @Override
    public void extractRenderState(
        TileTank tile, TankRenderState state, float partialTick, Vec3 cameraPosition,
        ModelFeatureRenderer.CrumblingOverlay breakProgress
    ) {
        BlockEntityRenderer.super.extractRenderState(tile, state, partialTick, cameraPosition, breakProgress);
        FluidResource fluid = tile.tank.getFluidType();
        int amount = tile.tank.getAmountAsInt(0);
        int capacity = tile.tank.getCapacity();
        float fraction = capacity > 0 ? Mth.clamp(amount / (float) capacity, 0f, 1f) : 0f;
        if (fluid.isEmpty() || fraction <= 0f) {
            state.hasFluid = false;
            return;
        }
        FluidState fluidState = fluid.getFluid().defaultFluidState();
        FluidModel model = Minecraft.getInstance().getModelManager().getFluidStateModelSet().get(fluidState);
        TextureAtlasSprite sprite = model.stillMaterial().sprite();

        state.hasFluid = true;
        state.atlasLocation = sprite.atlasLocation();
        state.u0 = sprite.getU0();
        state.u1 = sprite.getU1();
        state.v0 = sprite.getV0();
        state.v1 = sprite.getV1();
        // Nullable: vanilla's own lava model has no tint source at all (FluidStateModelSet.LAVA_MODEL).
        BlockTintSource tintSource = model.tintSource();
        state.tint = tintSource == null ? 0xFFFFFFFF : tintSource.color(fluidState.createLegacyBlock());
        state.topY = Y_MIN + (Y_MAX - Y_MIN) * fraction;
    }

    @Override
    public void submit(
        TankRenderState state, PoseStack poseStack, SubmitNodeCollector submitNodeCollector, CameraRenderState camera
    ) {
        if (!state.hasFluid) {
            return;
        }
        RenderType renderType = RenderTypes.entityTranslucent(state.atlasLocation);
        submitNodeCollector.submitCustomGeometry(
            poseStack, renderType, (pose, consumer) -> renderFluidBox(pose, consumer, state)
        );
    }

    private static void renderFluidBox(PoseStack.Pose pose, VertexConsumer consumer, TankRenderState state) {
        int argb = state.tint;
        float r = ((argb >> 16) & 0xFF) / 255f;
        float g = ((argb >> 8) & 0xFF) / 255f;
        float b = (argb & 0xFF) / 255f;
        int light = state.lightCoords;
        int overlay = OverlayTexture.NO_OVERLAY;
        float u0 = state.u0;
        float u1 = state.u1;
        float v0 = state.v0;
        float v1 = state.v1;
        float minX = X_MIN;
        float maxX = X_MAX;
        float minY = Y_MIN;
        float maxY = state.topY;
        float minZ = Z_MIN;
        float maxZ = Z_MAX;

        // -Y (bottom)
        face(
            pose, consumer, minX, minY, minZ, minX, minY, maxZ, maxX, minY, maxZ, maxX, minY, minZ, 0, -1, 0, u0, v0,
            u1, v1, r, g, b, light, overlay
        );
        // +Y (top / fluid surface)
        face(
            pose, consumer, minX, maxY, maxZ, minX, maxY, minZ, maxX, maxY, minZ, maxX, maxY, maxZ, 0, 1, 0, u0, v0,
            u1, v1, r, g, b, light, overlay
        );
        // -X (west)
        face(
            pose, consumer, minX, minY, maxZ, minX, minY, minZ, minX, maxY, minZ, minX, maxY, maxZ, -1, 0, 0, u0, v0,
            u1, v1, r, g, b, light, overlay
        );
        // +X (east)
        face(
            pose, consumer, maxX, minY, minZ, maxX, minY, maxZ, maxX, maxY, maxZ, maxX, maxY, minZ, 1, 0, 0, u0, v0,
            u1, v1, r, g, b, light, overlay
        );
        // -Z (north)
        face(
            pose, consumer, minX, minY, minZ, maxX, minY, minZ, maxX, maxY, minZ, minX, maxY, minZ, 0, 0, -1, u0, v0,
            u1, v1, r, g, b, light, overlay
        );
        // +Z (south)
        face(
            pose, consumer, maxX, minY, maxZ, minX, minY, maxZ, minX, maxY, maxZ, maxX, maxY, maxZ, 0, 0, 1, u0, v0,
            u1, v1, r, g, b, light, overlay
        );
    }

    private static void face(
        PoseStack.Pose pose, VertexConsumer consumer, float x0, float y0, float z0, float x1, float y1, float z1,
        float x2, float y2, float z2, float x3, float y3, float z3, float nx, float ny, float nz, float u0, float v0,
        float u1, float v1, float r, float g, float b, int light, int overlay
    ) {
        vertex(pose, consumer, x0, y0, z0, u0, v1, nx, ny, nz, r, g, b, light, overlay);
        vertex(pose, consumer, x1, y1, z1, u1, v1, nx, ny, nz, r, g, b, light, overlay);
        vertex(pose, consumer, x2, y2, z2, u1, v0, nx, ny, nz, r, g, b, light, overlay);
        vertex(pose, consumer, x3, y3, z3, u0, v0, nx, ny, nz, r, g, b, light, overlay);
    }

    private static void vertex(
        PoseStack.Pose pose, VertexConsumer consumer, float x, float y, float z, float u, float v, float nx,
        float ny, float nz, float r, float g, float b, int light, int overlay
    ) {
        consumer.addVertex(pose, x, y, z)
            .setColor(r, g, b, 1f)
            .setUv(u, v)
            .setOverlay(overlay)
            .setLight(light)
            .setNormal(pose, nx, ny, nz);
    }

    /** Everything {@link #submit} needs, captured once per frame by {@link #extractRenderState} -- {@code submit}
     * itself never touches the real {@link TileTank}, matching {@code RenderTileEngine}'s own
     * {@code RodRenderState}. */
    public static final class TankRenderState extends BlockEntityRenderState {
        boolean hasFluid;
        Identifier atlasLocation;
        float u0, u1, v0, v1;
        int tint;
        float topY;
    }
}
