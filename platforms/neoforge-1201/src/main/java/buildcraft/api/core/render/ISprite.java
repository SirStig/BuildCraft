/*
 * Copyright (c) 2026 Joshua Kac -- NeoForge port (BuildCraft 10)
 *
 * The BuildCraft API is distributed under the terms of the MIT License. Please check the contents of the
 * license, which should be located as "LICENSE.API" in the BuildCraft source code distribution.
 */
package buildcraft.api.core.render;

import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.resources.ResourceLocation;

/**
 * A rectangle of some texture, used for the icons BuildCraft draws in its GUIs.
 *
 * <p>The 1.12.2 version of this interface was built for immediate-mode GL: it had a {@code bindTexture()} that
 * called {@code GlStateManager.bindTexture} and left the result as global state for whatever drew next. That model
 * is gone -- the texture is now part of the draw call, chosen through a {@code RenderType} or passed to
 * {@code GuiGraphics}, and nothing may bind it out of band. So {@code bindTexture()} has no replacement and is not
 * ported; a sprite now simply <em>names</em> its texture and lets the caller draw with it.
 *
 * <p>The UV convention from 1.12.2 is kept: {@link #getInterpU}/{@link #getInterpV} take a value between 0 and 1,
 * unlike Minecraft's {@link TextureAtlasSprite}, which works in 0-16.
 */
public interface ISprite {

    /**
     * @return The texture this sprite is part of: an atlas id for a block or item sprite, or a standalone texture
     *         such as a GUI background.
     */
    ResourceLocation getTexture();

    /**
     * @param u A value between 0 and 1.
     * @return The u coordinate within {@link #getTexture()}.
     */
    double getInterpU(double u);

    /**
     * @param v A value between 0 and 1.
     * @return The v coordinate within {@link #getTexture()}.
     */
    double getInterpV(double v);
}
