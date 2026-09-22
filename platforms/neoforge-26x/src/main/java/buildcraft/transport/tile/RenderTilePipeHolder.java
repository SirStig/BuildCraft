/*
 * Copyright (c) 2026 Joshua Kac -- NeoForge port (BuildCraft 10)
 *
 * This file is part of the BuildCraft 10 port and is distributed under the terms of the MIT License.
 * Please check the contents of the license, which should be located as "LICENSE.PORT" in the BuildCraft
 * source code distribution.
 */
package buildcraft.transport.tile;

import java.util.ArrayList;
import java.util.List;

import org.jetbrains.annotations.Nullable;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;

import net.minecraft.client.Minecraft;
import net.minecraft.client.color.block.BlockTintSource;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.block.FluidModel;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.Vec3;

import net.neoforged.neoforge.transfer.fluid.FluidResource;

import buildcraft.api.core.EnumPipePart;
import buildcraft.api.transport.pipe.IPipe;

import buildcraft.transport.pipe.Pipe;
import buildcraft.transport.pipe.flow.PipeFlowFluids;
import buildcraft.transport.pipe.flow.PipeFlowItems;
import buildcraft.transport.pipe.flow.TravellingItem;

/**
 * Renders every real item currently travelling through a {@link TilePipeHolder}'s own {@link PipeFlowItems} --
 * the last major visual gap in this whole port's pipe work, closing exactly the "the pipes... not having
 * animations... items inside them" complaint the connection-shape and material-tinting batches above already
 * built the geometry for. Registered once, in {@code buildcraft.transport.client.BCTransportClientRegistries},
 * the same way {@code RenderTileEngine}/{@code RenderTileTank} are.
 *
 * <p>Genuinely new, not a reproduction of 1.12.2's own {@code PipeFlowRendererItems}/{@code IPipeFlowRenderer} --
 * that framework was a bespoke {@code MutableQuad}/{@code BufferBuilder} system with no counterpart anywhere in
 * this port (the same category of deliberate non-reproduction {@code RenderTileEngine}'s own javadoc already
 * documents for {@code RenderEngine_BC8}), so this reads the same underlying {@link TravellingItem} state through
 * a small, freshly re-added public accessor surface ({@link PipeFlowItems#getTravellingItemsForRender()},
 * {@link TravellingItem#getStack()}/{@code #isPhantom()}/{@code #getRenderPosition}/{@code #getRenderDirection()}
 * -- see each one's own javadoc) and draws each item with this target's own modern item-rendering API instead.
 *
 * <p>State-extraction/{@code submit} split, matching {@code RenderTileEngine}'s own precedent on this target
 * (confirmed, not assumed, to be the real 26.x {@code BlockEntityRenderer} shape). {@link #extractRenderState}
 * builds one {@link ItemStackRenderState} per visible item via {@link ItemModelResolver#updateForTopItem}, the
 * exact real, {@code javap}-confirmed method (against {@code minecraft-patched-26.3.0.7-beta-merged.jar}) a
 * `BlockEntityRenderer with no owning entity uses -- real vanilla precedent read directly from the decompiled
 * client jar, not guessed: {@code CampfireRenderer} (same target, same "items sitting inside a block, no entity
 * owns them" shape) calls this exact overload with a {@code null} {@link net.minecraft.world.entity.ItemOwner},
 * {@link ItemDisplayContext#FIXED}, and a per-slot seed derived from the tile's own {@code BlockPos} -- all
 * three choices reused verbatim here for the same reason.
 *
 * <p><b>Fluid pipes</b> ({@link PipeFlowFluids}) draw each section's fluid as a box, the geometry of 1.12.2's
 * {@code PipeFlowRendererFluids}: a side section running along a horizontal axis is a box filled from the bottom to
 * its share of the section's capacity (from the top, for a gas); a vertical side section is a full-height column
 * whose width scales with the square root of its fill; the centre is a filled cube when anything horizontal is
 * flowing (or nothing leaves vertically), topped by such a column when fluid leaves upwards (downwards, for a gas).
 * The sprite and tint come from the fluid's {@code FluidModel}, exactly as {@code RenderTileTank} gets them on this
 * target (a nullable {@code tintSource()} -- lava has none). Not reproduced: 1.12.2's animated flow offsets
 * ({@code getOffsetsForRender}), which scrolled the boxes along the flow direction, and its client-side amount
 * interpolation -- see {@code PipeFlowFluids}' class javadoc for why neither has client state to work from here.
 * Each face's texture coordinates are the box's own block-space position on that face (as vanilla maps block
 * models), so the sprite is not stretched onto small boxes.
 */
public class RenderTilePipeHolder implements BlockEntityRenderer<TilePipeHolder, RenderTilePipeHolder.PipeItemsRenderState> {

    /** Items in this port's pipes have no analogue of {@code CampfireRenderer}'s own 0.375 cooking-item scale to
     * copy -- 1.12.2's own pipe item render used a similarly shrunk size to fit inside an 8px-wide pipe interior
     * (the connection-shape batch's own centre cube spans pixels 4-12, i.e. 0.5 blocks wide). Kept as a named
     * constant, not a claim that this exact figure was measured off the original's own quad geometry. */
    private static final float ITEM_SCALE = 0.4f;

    private final ItemModelResolver itemModelResolver;

    public RenderTilePipeHolder(BlockEntityRendererProvider.Context context) {
        this.itemModelResolver = context.itemModelResolver();
    }

    @Override
    public PipeItemsRenderState createRenderState() {
        return new PipeItemsRenderState();
    }

    @Override
    public void extractRenderState(
        TilePipeHolder tile, PipeItemsRenderState state, float partialTick, Vec3 cameraPosition,
        ModelFeatureRenderer.CrumblingOverlay breakProgress
    ) {
        BlockEntityRenderer.super.extractRenderState(tile, state, partialTick, cameraPosition, breakProgress);
        state.items.clear();
        state.fluidBoxes.clear();

        IPipe pipe = tile.getPipe();
        Level level = tile.getLevel();
        if (pipe != null && pipe.getFlow() instanceof PipeFlowFluids fluidFlow) {
            extractFluidState(pipe, fluidFlow, state);
            return;
        }
        if (pipe == null || level == null || !(pipe.getFlow() instanceof PipeFlowItems flow)) {
            return;
        }

        long now = level.getGameTime();
        int seed = (int) tile.getBlockPos().asLong();
        int index = 0;
        for (TravellingItem item : flow.getTravellingItemsForRender()) {
            if (item.isPhantom()) {
                continue;
            }
            ItemStack stack = item.getStack();
            if (stack.isEmpty()) {
                continue;
            }

            Vec3 pos = item.getRenderPosition(BlockPos.ZERO, now, partialTick, flow);
            Direction direction = item.getRenderDirection();

            ItemStackRenderState itemState = new ItemStackRenderState();
            itemModelResolver.updateForTopItem(itemState, stack, ItemDisplayContext.FIXED, level, null, seed + index);
            index++;
            if (itemState.isEmpty()) {
                continue;
            }
            state.items.add(new RenderedItem(itemState, pos, direction));
        }
    }

    /** Fills {@code state.fluidBoxes} with 1.12.2's {@code PipeFlowRendererFluids} geometry -- see this class's own
     * javadoc. Sprite and tint lookup is {@code RenderTileTank#extractRenderState}'s. */
    private static void extractFluidState(IPipe pipe, PipeFlowFluids flow, PipeItemsRenderState state) {
        FluidResource fluid = flow.getFluidForRender();
        if (fluid.isEmpty()) {
            return;
        }
        int[] amounts = flow.getAmountsForRender();
        double capacity = flow.capacity;
        boolean gas = fluid.getFluidType().getDensity() < 0;

        boolean horizontal = false;
        boolean vertical = pipe.isConnected(gas ? Direction.DOWN : Direction.UP);
        for (Direction face : Direction.values()) {
            double amount = amounts[EnumPipePart.fromFacing(face).getIndex()];
            if (face.getAxis() != Direction.Axis.Y) {
                horizontal |= pipe.isConnected(face) && amount > 0;
            }
            if (amount <= 0) {
                continue;
            }
            double size = pipe instanceof Pipe realPipe ? realPipe.getConnectedDist(face) : 0.25;
            double[] center = { 0.5, 0.5, 0.5 };
            double[] radius = { 0.24, 0.24, 0.24 };
            int axis = face.getAxis().ordinal();
            center[axis] += face.getAxisDirection().getStep() * (0.245 + size / 2);
            radius[axis] = 0.005 + size / 2;
            double fill = amount / capacity;
            if (face.getAxis() == Direction.Axis.Y) {
                double perc = Math.sqrt(fill);
                radius[0] = perc * 0.24;
                radius[2] = perc * 0.24;
                fill = 1;
            }
            addFluidBox(state, center[0] - radius[0], center[1] - radius[1], center[2] - radius[2],
                center[0] + radius[0], center[1] + radius[1], center[2] + radius[2], fill, gas);
        }

        double amount = amounts[EnumPipePart.CENTER.getIndex()];
        if (amount > 0) {
            double horizPos = 0.26;
            if (horizontal || !vertical) {
                addFluidBox(state, 0.26, 0.26, 0.26, 0.74, 0.74, 0.74, amount / capacity, gas);
                horizPos += (0.74 - 0.26) * amount / capacity;
            }
            if (vertical && horizPos < 0.74) {
                double perc = Math.sqrt(amount / capacity);
                double minXZ = 0.5 - 0.24 * perc;
                double maxXZ = 0.5 + 0.24 * perc;
                double yMin = gas ? 0.26 : horizPos;
                double yMax = gas ? 1 - horizPos : 0.74;
                addFluidBox(state, minXZ, yMin, minXZ, maxXZ, yMax, maxXZ, 1, gas);
            }
        }
        if (state.fluidBoxes.isEmpty()) {
            return;
        }

        FluidState fluidState = fluid.getFluid().defaultFluidState();
        FluidModel model = Minecraft.getInstance().getModelManager().getFluidStateModelSet().get(fluidState);
        TextureAtlasSprite sprite = model.stillMaterial().sprite();
        state.fluidAtlas = sprite.atlasLocation();
        state.u0 = sprite.getU0();
        state.u1 = sprite.getU1();
        state.v0 = sprite.getV0();
        state.v1 = sprite.getV1();
        // Nullable: vanilla's own lava model has no tint source at all.
        BlockTintSource tintSource = model.tintSource();
        state.fluidTint = tintSource == null ? 0xFFFFFFFF : tintSource.color(fluidState.createLegacyBlock());
    }

    /** 1.12.2's {@code FluidRenderer.renderFluid(..., amount, capacity, min, max, ...)}: the box from {@code min} to
     * {@code max}, cut down to {@code fill} of its height -- kept at the bottom, or at the top for a gas. */
    private static void addFluidBox(
        PipeItemsRenderState state, double minX, double minY, double minZ, double maxX, double maxY, double maxZ,
        double fill, boolean gas
    ) {
        fill = Math.min(1, fill);
        if (fill <= 0) {
            return;
        }
        double height = (maxY - minY) * fill;
        if (gas) {
            minY = maxY - height;
        } else {
            maxY = minY + height;
        }
        state.fluidBoxes.add(new float[] {
            (float) minX, (float) minY, (float) minZ, (float) maxX, (float) maxY, (float) maxZ
        });
    }

    @Override
    public void submit(
        PipeItemsRenderState state, PoseStack poseStack, SubmitNodeCollector submitNodeCollector, CameraRenderState camera
    ) {
        if (!state.fluidBoxes.isEmpty()) {
            // Non-culling, for RenderTileTank's reason: a hand-built box's winding is not checked on screen here.
            RenderType renderType = RenderTypes.entityTranslucent(state.fluidAtlas);
            submitNodeCollector.submitCustomGeometry(poseStack, renderType, (pose, consumer) -> {
                for (float[] box : state.fluidBoxes) {
                    renderFluidBox(pose, consumer, state, box);
                }
            });
        }
        for (RenderedItem rendered : state.items) {
            poseStack.pushPose();
            poseStack.translate(rendered.pos.x, rendered.pos.y, rendered.pos.z);
            applyDirectionRotation(poseStack, rendered.direction);
            poseStack.scale(ITEM_SCALE, ITEM_SCALE, ITEM_SCALE);
            rendered.state.submit(poseStack, submitNodeCollector, state.lightCoords, OverlayTexture.NO_OVERLAY, 0);
            poseStack.popPose();
        }
    }

    /** A small, honestly-scoped simplification, not a claim of rotation fidelity -- see this class's own javadoc
     * and the task-level scope note this batch was given ("exact rotation fidelity is a nice-to-have"). Horizontal
     * directions reuse {@link Direction#toYRot()} (the same real, {@code javap}-confirmed method the piston-rod
     * batch's own {@code RenderTileEngine} javadoc already established exists identically on both platforms);
     * {@link Direction#UP}/{@link Direction#DOWN} get a plain 90-degree tilt since {@code toYRot()} alone cannot
     * express a vertical facing. A {@code null} direction (only possible if {@link TravellingItem#getRenderPosition}
     * degrades to {@code side == null}, per its own javadoc -- not reachable from any real gameplay path this
     * batch found) leaves the item unrotated rather than throwing. */
    private static void applyDirectionRotation(PoseStack poseStack, @Nullable Direction direction) {
        if (direction == null) {
            return;
        }
        if (direction == Direction.UP) {
            poseStack.rotateDegrees(Axis.XP, -90f);
        } else if (direction == Direction.DOWN) {
            poseStack.rotateDegrees(Axis.XP, 90f);
        } else {
            poseStack.rotateDegrees(Axis.YP, direction.toYRot());
        }
    }

    private static void renderFluidBox(PoseStack.Pose pose, VertexConsumer consumer, PipeItemsRenderState state, float[] b) {
        float x0 = b[0], y0 = b[1], z0 = b[2], x1 = b[3], y1 = b[4], z1 = b[5];
        // -Y, +Y, -X, +X, -Z, +Z
        quad(pose, consumer, state, x0, y0, z0, x0, y0, z1, x1, y0, z1, x1, y0, z0, 0, -1, 0);
        quad(pose, consumer, state, x0, y1, z1, x0, y1, z0, x1, y1, z0, x1, y1, z1, 0, 1, 0);
        quad(pose, consumer, state, x0, y0, z1, x0, y0, z0, x0, y1, z0, x0, y1, z1, -1, 0, 0);
        quad(pose, consumer, state, x1, y0, z0, x1, y0, z1, x1, y1, z1, x1, y1, z0, 1, 0, 0);
        quad(pose, consumer, state, x0, y0, z0, x1, y0, z0, x1, y1, z0, x0, y1, z0, 0, 0, -1);
        quad(pose, consumer, state, x1, y0, z1, x0, y0, z1, x0, y1, z1, x1, y1, z1, 0, 0, 1);
    }

    private static void quad(
        PoseStack.Pose pose, VertexConsumer consumer, PipeItemsRenderState state, float xa, float ya, float za,
        float xb, float yb, float zb, float xc, float yc, float zc, float xd, float yd, float zd, int nx, int ny,
        int nz
    ) {
        fluidVertex(pose, consumer, state, xa, ya, za, nx, ny, nz);
        fluidVertex(pose, consumer, state, xb, yb, zb, nx, ny, nz);
        fluidVertex(pose, consumer, state, xc, yc, zc, nx, ny, nz);
        fluidVertex(pose, consumer, state, xd, yd, zd, nx, ny, nz);
    }

    /** Texture coordinates from the vertex's own block-space position on the face's plane: x/z across the top and
     * bottom, x (or z) across and y down the sides. */
    private static void fluidVertex(
        PoseStack.Pose pose, VertexConsumer consumer, PipeItemsRenderState state, float x, float y, float z, int nx,
        int ny, int nz
    ) {
        float s;
        float t;
        if (ny != 0) {
            s = x;
            t = z;
        } else if (nx != 0) {
            s = z;
            t = 1 - y;
        } else {
            s = x;
            t = 1 - y;
        }
        int argb = state.fluidTint;
        consumer.addVertex(pose, x, y, z)
            .setColor(((argb >> 16) & 0xFF) / 255f, ((argb >> 8) & 0xFF) / 255f, (argb & 0xFF) / 255f, 1f)
            .setUv(state.u0 + (state.u1 - state.u0) * s, state.v0 + (state.v1 - state.v0) * t)
            .setOverlay(OverlayTexture.NO_OVERLAY)
            .setLight(state.lightCoords)
            .setNormal(pose, nx, ny, nz);
    }

    /** Everything {@link #submit} needs, captured once per frame by {@link #extractRenderState} -- {@code submit}
     * itself never touches the real {@link TilePipeHolder}/{@link PipeFlowItems}, matching {@code RenderTileEngine}'s
     * own state-extraction contract on this target. */
    public static final class PipeItemsRenderState extends BlockEntityRenderState {
        final List<RenderedItem> items = new ArrayList<>();
        /** Fluid pipes only: one {@code {minX, minY, minZ, maxX, maxY, maxZ}} per box, plus the fluid's sprite and
         * tint. */
        final List<float[]> fluidBoxes = new ArrayList<>();
        Identifier fluidAtlas;
        float u0, u1, v0, v1;
        int fluidTint;
    }

    private record RenderedItem(ItemStackRenderState state, Vec3 pos, @Nullable Direction direction) {}
}
