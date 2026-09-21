/*
 * Copyright (c) 2026 Joshua Kac -- NeoForge port (BuildCraft 10)
 *
 * The BuildCraft API is distributed under the terms of the MIT License. Please check the contents of the
 * license, which should be located as "LICENSE.API" in the BuildCraft source code distribution.
 */
package buildcraft.api.items;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.item.ItemStack;

/**
 * Lets an item draw itself specially while travelling inside a pipe.
 *
 * <p>1.12.2's {@code renderItemInPipe(stack, x, y, z)} positioned the item with raw GL translations against the
 * current matrix. That is a {@link PoseStack} now, and geometry goes into a buffer from a
 * {@link MultiBufferSource} rather than being emitted immediately.
 *
 * <p>26.x goes further still -- it submits to a render graph through a {@code SubmitNodeCollector} and has no
 * {@code MultiBufferSource} at all -- which is why the two copies of this interface differ.
 *
 * <p>The {@code @SideOnly(Side.CLIENT)} is dropped rather than replaced: {@code @OnlyIn} would stop this
 * interface loading on a server, and a pipe holding a reference to it exists on both sides. Only the client
 * ever calls the method.
 */
public interface IItemCustomPipeRender {

    float getPipeRenderScale(ItemStack stack);

    /**
     * @param poseStack Already translated to the item's position along the pipe.
     * @param buffers Where to obtain a vertex consumer from.
     * @param packedLight The light value at the item's position.
     * @return False to use the default renderer, true if this drew the item itself.
     */
    boolean renderItemInPipe(ItemStack stack, PoseStack poseStack, MultiBufferSource buffers, int packedLight);
}
