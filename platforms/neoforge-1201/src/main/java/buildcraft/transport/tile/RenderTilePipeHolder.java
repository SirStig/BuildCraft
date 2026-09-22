/*
 * Copyright (c) 2026 Joshua Kac -- NeoForge port (BuildCraft 10)
 *
 * This file is part of the BuildCraft 10 port and is distributed under the terms of the MIT License.
 * Please check the contents of the license, which should be located as "LICENSE.PORT" in the BuildCraft
 * source code distribution.
 */
package buildcraft.transport.tile;

import org.jetbrains.annotations.Nullable;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import buildcraft.api.transport.pipe.IPipe;

import buildcraft.transport.pipe.flow.PipeFlowItems;
import buildcraft.transport.pipe.flow.TravellingItem;

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
        IPipe pipe = tile.getPipe();
        Level level = tile.getLevel();
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
