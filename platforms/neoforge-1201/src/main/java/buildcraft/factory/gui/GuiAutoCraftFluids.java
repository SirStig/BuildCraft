/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * Copyright (c) 2026 Joshua Kac -- NeoForge port (BuildCraft 10)
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL
 * was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.factory.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.InventoryMenu;

import net.minecraftforge.client.extensions.common.IClientFluidTypeExtensions;
import net.minecraftforge.fluids.FluidStack;

import buildcraft.factory.container.ContainerAutoCraftFluids;
import buildcraft.lib.fluid.Tank;

/**
 * Mirrors the 26.x copy of this class -- see that one's own javadoc for the full account of why this is still a
 * plain-panel screen with no real GUI background texture (none ever existed for this block, and authoring one is
 * out of scope), but *not* plain text for the tanks any more: this draws a real vertical fill bar per tank (via
 * {@link #drawFluidBar}), reusing the exact fluid sprite/tint lookup
 * {@code buildcraft.factory.tile.RenderTileTank} already pinned down and {@code javap}-verified for this target --
 * see that class's own javadoc for the full account of {@code IClientFluidTypeExtensions.of(Fluid)}. This file
 * differs from the 26.x copy only in the usual 1.20.1 places: the classic {@code GuiGraphics}/
 * {@code AbstractContainerScreen#renderBg} pair (matching {@link GuiAutoCraftItems}'s own 1.20.1 copy), and a
 * genuinely different real fluid sprite/tint API and drawing primitive -- see below.
 *
 * <p><b>Drawing primitive, confirmed via reading the real decompiled source of {@code GuiGraphics} out of the
 * Forge 1.20.1 {@code -sources.jar}</b> (not guessed): the only overload that accepts an already-resolved
 * {@link TextureAtlasSprite} together with a direct colour is
 * {@code blit(int x, int y, int blitOffset, int width, int height, TextureAtlasSprite sprite, float red, float
 * green, float blue, float alpha)} -- confirmed by reading its body, it forwards straight to {@code innerBlit}
 * using the sprite's *full* UV rect ({@code getU0()}/{@code getU1()}/{@code getV0()}/{@code getV1()}) stretched
 * into the given pixel box; there is no overload that also crops the UV to a sub-rectangle for a raw
 * {@link TextureAtlasSprite} the way the classic {@code ResourceLocation}-based {@code blit} overloads can for a
 * fixed-size PNG (those assume a flat image, not a fractional atlas UV rect, so they cannot address an arbitrary
 * block-atlas sprite by pixel offset). The fill-from-the-bottom effect is therefore achieved with
 * {@link GuiGraphics#enableScissor(int, int, int, int)}/{@code disableScissor()} (both public, confirmed via
 * {@code javap}): draw the sprite at its full bar size every frame, clipped to only the visible fraction -- the
 * same real, public primitive used on the 26.x copy of this class (see that one's javadoc for why it is a genuine
 * technique, not a workaround, on that target too). Unlike 26.x, the tint here is passed as direct {@code float}
 * RGB components (no packed-int ARGB step, and no alpha-byte concern), taken from
 * {@code IClientFluidTypeExtensions.getTintColor(FluidStack)} exactly the way {@code RenderTileTank} already reads
 * it, with alpha passed as a literal {@code 1f} (fully opaque) rather than extracted from the tint value.
 *
 * <p><b>Fill direction and input-range safety</b>: identical convention and identical hand-traced numbers to the
 * 26.x copy -- see that class's own javadoc for the full account (bottom-up fill via scissoring the larger-Y half
 * of the bar's box; {@code BAR_HEIGHT = 54}; {@code f = 0.5} -> {@code fillHeight = 27}; {@code f = 1.0} ->
 * {@code fillHeight = 54}, unclipped; {@code f = 0.0} never reaches the sprite/scissor code at all, matching
 * {@code RenderTileTank}'s own "don't render anything for an empty tank" rule).
 *
 * <p>Verification limitation, matching every other GUI-adjacent piece of this port: no mouse/keyboard input
 * automation exists in this environment, so this screen's on-screen rendering -- including whether the two bars
 * actually look right -- cannot be exercised live. What was verified is a clean compile, a clean dedicated-server
 * boot, and the fill-fraction/pixel-height arithmetic above, hand-traced rather than eyeballed on screen.
 */
public class GuiAutoCraftFluids extends AbstractContainerScreen<ContainerAutoCraftFluids> {
    private static final int PANEL_COLOR = 0xFFC6_C6C6;
    private static final int TEXT_COLOR = 0x40_4040;

    /** Fluid-bar geometry -- see the 26.x copy of this class for the full layout rationale (clear of every slot
     * and of the player inventory, which starts at {@code y} 115). */
    private static final int BAR_WIDTH = 14;
    private static final int BAR_HEIGHT = 54;
    private static final int BAR_Y = 16;
    private static final int BAR1_X = 144;
    private static final int BAR2_X = 160;

    public GuiAutoCraftFluids(ContainerAutoCraftFluids menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        imageWidth = 176;
        imageHeight = 197;
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        graphics.fill(leftPos, topPos, leftPos + imageWidth, topPos + imageHeight, PANEL_COLOR);
        drawFluidBar(graphics, menu.tile.tank1, leftPos + BAR1_X, topPos + BAR_Y);
        drawFluidBar(graphics, menu.tile.tank2, leftPos + BAR2_X, topPos + BAR_Y);
        graphics.drawString(font, "Progress: " + Math.round(menu.getProgress() * 100) + "%", leftPos + 8, topPos + 88, TEXT_COLOR, false);
    }

    /** Draws one tank's real fluid-level bar at the given top-left pixel position, plus a compact percentage
     * readout underneath -- see this class's own javadoc for the full account of the drawing primitive, the tint
     * handling and the fill-direction math. */
    private void drawFluidBar(GuiGraphics graphics, Tank tank, int x, int y) {
        FluidStack fluidStack = tank.getFluid();
        int amount = tank.getFluidAmount();
        int capacity = tank.getCapacity();
        float fraction = capacity > 0 ? Mth.clamp(amount / (float) capacity, 0f, 1f) : 0f;
        graphics.drawString(font, Math.round(fraction * 100) + "%", x, y + BAR_HEIGHT + 2, TEXT_COLOR, false);

        if (fluidStack.isEmpty() || fraction <= 0f) {
            return;
        }
        IClientFluidTypeExtensions extensions = IClientFluidTypeExtensions.of(fluidStack.getFluid());
        ResourceLocation textureLocation = extensions.getStillTexture(fluidStack);
        TextureAtlasSprite sprite = Minecraft.getInstance().getTextureAtlas(InventoryMenu.BLOCK_ATLAS).apply(textureLocation);
        int argb = extensions.getTintColor(fluidStack);
        float r = ((argb >> 16) & 0xFF) / 255f;
        float g = ((argb >> 8) & 0xFF) / 255f;
        float b = (argb & 0xFF) / 255f;

        int fillHeight = Math.round(BAR_HEIGHT * fraction);
        int fillY = y + (BAR_HEIGHT - fillHeight);
        graphics.enableScissor(x, fillY, x + BAR_WIDTH, y + BAR_HEIGHT);
        graphics.blit(x, y, 0, BAR_WIDTH, BAR_HEIGHT, sprite, r, g, b, 1f);
        graphics.disableScissor();
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        renderTooltip(graphics, mouseX, mouseY);
    }
}
