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
import com.mojang.math.Axis;

import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
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

            ItemStackRenderState itemState = new ItemStackRenderState();
            itemModelResolver.updateForTopItem(itemState, stack, ItemDisplayContext.FIXED, level, null, seed + index);
            index++;
            if (itemState.isEmpty()) {
                continue;
            }
            state.items.add(new RenderedItem(itemState, pos, direction));
        }
    }

    @Override
    public void submit(
        PipeItemsRenderState state, PoseStack poseStack, SubmitNodeCollector submitNodeCollector, CameraRenderState camera
    ) {
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

    /** Everything {@link #submit} needs, captured once per frame by {@link #extractRenderState} -- {@code submit}
     * itself never touches the real {@link TilePipeHolder}/{@link PipeFlowItems}, matching {@code RenderTileEngine}'s
     * own state-extraction contract on this target. */
    public static final class PipeItemsRenderState extends BlockEntityRenderState {
        final List<RenderedItem> items = new ArrayList<>();
    }

    private record RenderedItem(ItemStackRenderState state, Vec3 pos, @Nullable Direction direction) {}
}
