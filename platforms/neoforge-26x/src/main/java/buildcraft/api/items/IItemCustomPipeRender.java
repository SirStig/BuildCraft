/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * Copyright (c) 2026 Joshua Kac -- NeoForge port (BuildCraft 10)
 *
 * The BuildCraft API is distributed under the terms of the MIT License. Please check the contents of the
 * license, which should be located as "LICENSE.API" in the BuildCraft source code distribution.
 */
package buildcraft.api.items;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.world.item.ItemStack;

/**
 * Lets an item draw itself specially while travelling inside a pipe.
 *
 * <p>1.12.2's {@code renderItemInPipe(stack, x, y, z)} positioned the item by issuing raw GL translations
 * against the current matrix and emitted geometry immediately. Neither survives: the transform is carried in a
 * {@link PoseStack}, and 26.x does not draw during the render pass at all -- it <em>submits</em> work to a
 * render graph that is sorted and executed later. {@code MultiBufferSource} is gone with it; the collector is
 * {@link SubmitNodeCollector}, and freeform geometry goes through its {@code submitCustomGeometry}.
 *
 * <p>The 1.20.1 copy of this interface still takes a {@code MultiBufferSource}, which is why the two differ.
 *
 * <p>The {@code @SideOnly(Side.CLIENT)} is dropped rather than replaced: {@code @OnlyIn} would stop this
 * interface loading on a server, and a pipe holding a reference to it exists on both sides. Only the client ever
 * calls the method.
 */
public interface IItemCustomPipeRender {

    float getPipeRenderScale(ItemStack stack);

    /**
     * @param poseStack Already translated to the item's position along the pipe.
     * @param collector Where to submit geometry for this frame.
     * @param packedLight The light value at the item's position.
     * @return False to use the default renderer, true if this submitted the item itself.
     */
    boolean renderItemInPipe(ItemStack stack, PoseStack poseStack, SubmitNodeCollector collector, int packedLight);
}
