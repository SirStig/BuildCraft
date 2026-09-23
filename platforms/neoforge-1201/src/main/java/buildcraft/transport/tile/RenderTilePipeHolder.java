/*
 * Copyright (c) 2026 Joshua Kac -- NeoForge port (BuildCraft 10)
 *
 * This file is part of the BuildCraft 10 port and is distributed under the terms of the MIT License.
 * Please check the contents of the license, which should be located as "LICENSE.PORT" in the BuildCraft
 * source code distribution.
 */
package buildcraft.transport.tile;

import java.util.Map;

import org.jetbrains.annotations.Nullable;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import net.minecraftforge.client.extensions.common.IClientFluidTypeExtensions;
import net.minecraftforge.fluids.FluidStack;

import buildcraft.api.core.EnumPipePart;
import buildcraft.api.transport.pipe.IPipe;
import buildcraft.api.transport.pluggable.PipePluggable;

import buildcraft.transport.pipe.Pipe;
import buildcraft.transport.pipe.flow.PipeFlowFluids;
import buildcraft.transport.pipe.flow.PipeFlowItems;
import buildcraft.transport.pipe.flow.TravellingItem;
import buildcraft.transport.plug.PluggableFacade;

/**
 * Renders every real item currently travelling through a {@link TilePipeHolder}'s own {@link PipeFlowItems} --
 * see the 26.x copy of this class's own javadoc for the full account of why this is genuinely new work rather
 * than a reproduction of 1.12.2's own {@code PipeFlowRendererItems}/{@code IPipeFlowRenderer}, and of the real
 * public accessor surface both copies of this class read.
 *
 * <p>Classic immediate-mode {@code render(...)} contract, matching this target's own {@code RenderTileEngine} --
 * `javap`-confirmed unchanged from the 1.12.2/{@code TileEntitySpecialRenderer} era on this target (see that
 * class's own javadoc for the real divergence between the two platforms). {@link ItemRenderer#renderStatic}
 * (confirmed via `javap` against {@code forge-1.20.1-47.1.106-merged.jar}: {@code renderStatic(ItemStack,
 * ItemDisplayContext, int, int, PoseStack, MultiBufferSource, Level, int)}) is the direct classic-API analogue of
 * 26.x's {@code ItemModelResolver#updateForTopItem} + {@code ItemStackRenderState#submit} split -- one call draws
 * the whole stack immediately, with no separate render-state object to extract ahead of time, matching every
 * other classic-contract renderer on this target.
 *
 * <p>Reached via {@code context.getItemRenderer()} in the constructor (also `javap`-confirmed, on
 * {@code BlockEntityRendererProvider.Context} itself) rather than a per-frame {@code Minecraft.getInstance()}
 * call -- the same already-provided instance either way, but cached once like {@code RenderTileEngine}'s own
 * {@code texture} field, instead of re-fetching it every {@link #render} call.
 *
 * <p>Fluid pipes ({@link PipeFlowFluids}) draw each section as a box -- the 26.x copy's javadoc has the geometry
 * (1.12.2's {@code PipeFlowRendererFluids}) and what is not reproduced. The sprite and tint come from
 * {@link IClientFluidTypeExtensions}, as in this target's {@code RenderTileTank}.
 *
 * <p><b>Facades</b> ({@code buildcraft.transport.plug.PluggableFacade}, the facades batch): a real disguised
 * block model via {@code BlockRenderDispatcher#renderSingleBlock(BlockState, PoseStack, MultiBufferSource, int,
 * int)} (confirmed via {@code javap} against {@code forge-1.20.1-47.1.106-merged.jar}) -- the same classic API
 * used to draw a block-item icon, real per-face textures and its own default tint included, no hand-built quads
 * needed. Fitted into the facade's own {@link PluggableFacade#getBoundingBox()} slab the same way the 26.x copy
 * does: translate to the box's minimum corner, then scale non-uniformly by the box's own size, so a full cube's
 * {@code 0..1}-local-space quads land exactly on the box with the visible face's texture undistorted.
 */
public class RenderTilePipeHolder implements BlockEntityRenderer<TilePipeHolder> {

    /** See the 26.x copy of this class's own javadoc for why this figure exists and what it is not claiming. */
    private static final float ITEM_SCALE = 0.4f;

    private final ItemRenderer itemRenderer;

    public RenderTilePipeHolder(BlockEntityRendererProvider.Context context) {
        this.itemRenderer = context.getItemRenderer();
    }

    @Override
    public void render(
        TilePipeHolder tile, float partialTick, PoseStack poseStack, MultiBufferSource bufferSource, int packedLight,
        int packedOverlay
    ) {
        renderFacades(tile, poseStack, bufferSource, packedLight, packedOverlay);

        IPipe pipe = tile.getPipe();
        Level level = tile.getLevel();
        if (pipe != null && pipe.getFlow() instanceof PipeFlowFluids fluidFlow) {
            renderFluids(pipe, fluidFlow, poseStack, bufferSource, packedLight);
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

            poseStack.pushPose();
            poseStack.translate(pos.x, pos.y, pos.z);
            applyDirectionRotation(poseStack, direction);
            poseStack.scale(ITEM_SCALE, ITEM_SCALE, ITEM_SCALE);
            itemRenderer.renderStatic(
                stack, ItemDisplayContext.FIXED, packedLight, packedOverlay, poseStack, bufferSource, level,
                seed + (index++)
            );
            poseStack.popPose();
        }
    }

    /** Draws every non-hollow {@link PluggableFacade} currently attached to this pipe holder as a real disguised
     * block model -- see this class's own javadoc. */
    private static void renderFacades(
        TilePipeHolder tile, PoseStack poseStack, MultiBufferSource bufferSource, int packedLight, int packedOverlay
    ) {
        for (Map.Entry<Direction, PipePluggable> entry : tile.getPluggables().entrySet()) {
            if (!(entry.getValue() instanceof PluggableFacade facade) || facade.isHollow()) {
                continue;
            }
            BlockState disguise = facade.states.phasedStates[facade.activeState].stateInfo.state;
            if (disguise.isAir() || disguise.getRenderShape() != RenderShape.MODEL) {
                continue;
            }
            AABB box = facade.getBoundingBox();
            poseStack.pushPose();
            poseStack.translate(box.minX, box.minY, box.minZ);
            poseStack.scale((float) (box.maxX - box.minX), (float) (box.maxY - box.minY), (float) (box.maxZ - box.minZ));
            Minecraft.getInstance().getBlockRenderer()
                .renderSingleBlock(disguise, poseStack, bufferSource, packedLight, packedOverlay);
            poseStack.popPose();
        }
    }

    /** 1.12.2's {@code PipeFlowRendererFluids} geometry -- the same boxes the 26.x copy's
     * {@code extractFluidState} builds, drawn immediately. */
    private static void renderFluids(
        IPipe pipe, PipeFlowFluids flow, PoseStack poseStack, MultiBufferSource bufferSource, int light
    ) {
        FluidStack fluid = flow.getFluidForRender();
        if (fluid.isEmpty()) {
            return;
        }
        int[] amounts = flow.getAmountsForRender();
        double capacity = flow.capacity;
        boolean gas = fluid.getFluid().getFluidType().getDensity() < 0;

        IClientFluidTypeExtensions extensions = IClientFluidTypeExtensions.of(fluid.getFluid());
        ResourceLocation textureLocation = extensions.getStillTexture(fluid);
        TextureAtlasSprite sprite = Minecraft.getInstance().getTextureAtlas(InventoryMenu.BLOCK_ATLAS).apply(textureLocation);
        int argb = extensions.getTintColor(fluid);
        FluidBoxes boxes = new FluidBoxes(
            poseStack.last(), bufferSource.getBuffer(RenderType.entityTranslucent(InventoryMenu.BLOCK_ATLAS)), sprite,
            ((argb >> 16) & 0xFF) / 255f, ((argb >> 8) & 0xFF) / 255f, (argb & 0xFF) / 255f, light, gas
        );

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
            boxes.box(center[0] - radius[0], center[1] - radius[1], center[2] - radius[2],
                center[0] + radius[0], center[1] + radius[1], center[2] + radius[2], fill);
        }

        double amount = amounts[EnumPipePart.CENTER.getIndex()];
        if (amount > 0) {
            double horizPos = 0.26;
            if (horizontal || !vertical) {
                boxes.box(0.26, 0.26, 0.26, 0.74, 0.74, 0.74, amount / capacity);
                horizPos += (0.74 - 0.26) * amount / capacity;
            }
            if (vertical && horizPos < 0.74) {
                double perc = Math.sqrt(amount / capacity);
                double minXZ = 0.5 - 0.24 * perc;
                double maxXZ = 0.5 + 0.24 * perc;
                double yMin = gas ? 0.26 : horizPos;
                double yMax = gas ? 1 - horizPos : 0.74;
                boxes.box(minXZ, yMin, minXZ, maxXZ, yMax, maxXZ, 1);
            }
        }
    }

    /** Draws 1.12.2 {@code FluidRenderer.renderFluid}-style boxes -- cut down to {@code fill} of their height, from
     * the bottom (or the top, for a gas) -- with a non-culling render type, for {@code RenderTileTank}'s reason.
     * Texture coordinates come from each vertex's block-space position on its face, so the sprite is not stretched
     * onto small boxes. */
    private record FluidBoxes(
        PoseStack.Pose pose, VertexConsumer consumer, TextureAtlasSprite sprite, float r, float g, float b, int light,
        boolean gas
    ) {
        void box(double minX, double minY, double minZ, double maxX, double maxY, double maxZ, double fill) {
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
            float x0 = (float) minX, y0 = (float) minY, z0 = (float) minZ;
            float x1 = (float) maxX, y1 = (float) maxY, z1 = (float) maxZ;
            // -Y, +Y, -X, +X, -Z, +Z
            quad(x0, y0, z0, x0, y0, z1, x1, y0, z1, x1, y0, z0, 0, -1, 0);
            quad(x0, y1, z1, x0, y1, z0, x1, y1, z0, x1, y1, z1, 0, 1, 0);
            quad(x0, y0, z1, x0, y0, z0, x0, y1, z0, x0, y1, z1, -1, 0, 0);
            quad(x1, y0, z0, x1, y0, z1, x1, y1, z1, x1, y1, z0, 1, 0, 0);
            quad(x0, y0, z0, x1, y0, z0, x1, y1, z0, x0, y1, z0, 0, 0, -1);
            quad(x1, y0, z1, x0, y0, z1, x0, y1, z1, x1, y1, z1, 0, 0, 1);
        }

        private void quad(
            float xa, float ya, float za, float xb, float yb, float zb, float xc, float yc, float zc, float xd,
            float yd, float zd, int nx, int ny, int nz
        ) {
            vertex(xa, ya, za, nx, ny, nz);
            vertex(xb, yb, zb, nx, ny, nz);
            vertex(xc, yc, zc, nx, ny, nz);
            vertex(xd, yd, zd, nx, ny, nz);
        }

        private void vertex(float x, float y, float z, int nx, int ny, int nz) {
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
            consumer.vertex(pose.pose(), x, y, z)
                .color(r, g, b, 1f)
                .uv(sprite.getU(s * 16), sprite.getV(t * 16))
                .overlayCoords(OverlayTexture.NO_OVERLAY)
                .uv2(light)
                .normal(pose.normal(), nx, ny, nz)
                .endVertex();
        }
    }

    /** See the 26.x copy of this class's own javadoc for the reasoning; only the rotation call itself differs
     * (this target's {@code PoseStack} has no {@code rotateDegrees(Axis, float)} overload -- confirmed via
     * `javap`, only {@code mulPose(Quaternionf)} -- so {@link Axis#rotationDegrees(float)} builds the quaternion
     * explicitly, the standard 1.20.1-era idiom for this exact call shape). */
    private static void applyDirectionRotation(PoseStack poseStack, @Nullable Direction direction) {
        if (direction == null) {
            return;
        }
        if (direction == Direction.UP) {
            poseStack.mulPose(Axis.XP.rotationDegrees(-90f));
        } else if (direction == Direction.DOWN) {
            poseStack.mulPose(Axis.XP.rotationDegrees(90f));
        } else {
            poseStack.mulPose(Axis.YP.rotationDegrees(direction.toYRot()));
        }
    }
}
