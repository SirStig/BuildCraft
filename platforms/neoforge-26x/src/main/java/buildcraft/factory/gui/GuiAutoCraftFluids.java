/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * Copyright (c) 2026 Joshua Kac -- NeoForge port (BuildCraft 10)
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL
 * was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.factory.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.block.FluidModel;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.network.chat.Component;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.level.material.FluidState;

import net.neoforged.neoforge.transfer.fluid.FluidResource;

import buildcraft.factory.container.ContainerAutoCraftFluids;
import buildcraft.lib.fluid.Tank;

/**
 * A deliberately bare-minimum screen -- there is no genuine GUI background texture for this block (see
 * {@code buildcraft.factory.tile.TileAutoWorkbenchFluids}'s own javadoc: this block's registration was commented
 * out in 1.12.2, so no {@code autobench_fluid.png} was ever drawn), and this class still paints a plain panel fill
 * in its place rather than inventing one -- that part of the original scope cut stands (see PORTING.md). What is
 * no longer true is the *fluid rendering itself*: this now draws a real vertical fill bar per tank (via
 * {@link #drawFluidBar}), reusing the exact fluid sprite/tint lookup {@code buildcraft.factory.tile.RenderTileTank}
 * already pinned down and {@code javap}-verified for this target -- see that class's own javadoc for the full
 * account of {@code Minecraft.getInstance().getModelManager().getFluidStateModelSet()}. The two tanks' contents
 * are read directly off {@code menu.tile.tank1}/{@code tank2} (the synced tile -- see
 * {@link ContainerAutoCraftFluids}'s own javadoc for why no extra network plumbing is needed for that).
 *
 * <p><b>Drawing primitive, confirmed via reading the real decompiled source of
 * {@code net.minecraft.client.gui.GuiGraphicsExtractor} out of the 26.3 {@code -sources.jar}</b> (not guessed):
 * the only public overload that accepts an already-resolved {@link TextureAtlasSprite} together with a tint is
 * {@code blitSprite(RenderPipeline, TextureAtlasSprite, int x, int y, int width, int height, int color)} -- it
 * always draws the sprite's *full* UV rect ({@code u0}/{@code u1}/{@code v0}/{@code v1}) stretched into the given
 * pixel box; there is no publicly-reachable overload that also crops the UV to a sub-rectangle for a raw
 * {@link TextureAtlasSprite} (the one private overload that does -- used internally for the {@code Tile}/
 * {@code NineSlice} sprite-scaling cases -- is {@code private}, and the public {@code Identifier}-based
 * {@code blitSprite} overload that *does* expose UV cropping resolves its sprite from the GUI sprite atlas via
 * {@code guiSprites.getSprite(...)}, which cannot look up an arbitrary block-atlas fluid sprite at all). The
 * fill-from-the-bottom effect is therefore achieved the same way {@code GuiGraphicsExtractor} itself falls back to
 * internally for non-{@code Stretch} sprite scaling (same file, same lookup): draw the sprite at its full bar size
 * every frame, wrapped in {@link GuiGraphicsExtractor#enableScissor(int, int, int, int)}/{@code disableScissor()}
 * (both public) clipped to only the visible fraction -- a real, verified primitive, not a workaround, since it is
 * literally vanilla's own technique for this exact "can't crop a raw sprite" situation. The tint argument is a
 * direct {@code int} ARGB colour; {@link ARGB#opaque(int)} is used to force the alpha byte to {@code 0xFF} because
 * {@code BlockTintSource#color(BlockState)} (what {@code RenderTileTank} also reads) returns a plain
 * {@code 0x00RRGGBB} value with the alpha byte unset, which -- confirmed by reading {@code innerBlit}'s use of the
 * colour as a real multiplicative tint -- would otherwise multiply the sprite to fully transparent.
 *
 * <p><b>Fill direction: bottom-up</b>, matching {@code RenderTileTank}'s own vertical fill-from-{@code Y_MIN}
 * convention -- the natural reading for a tank (liquid rises from the bottom). In 2D screen space, Y increases
 * downward, so "reveal the bottom" means scissoring to the *larger*-Y half of the bar's box, not the smaller: for
 * a bar occupying {@code [barY, barY + BAR_HEIGHT)}, a fill fraction {@code f} clips to
 * {@code [barY + BAR_HEIGHT - fillHeight, barY + BAR_HEIGHT)} where {@code fillHeight = round(BAR_HEIGHT * f)}.
 * Hand-traced: {@code BAR_HEIGHT = 54}; {@code f = 0.0} never reaches this code at all (see below); {@code f = 0.5}
 * gives {@code fillHeight = 27}, scissor {@code [barY + 27, barY + 54)} -- the bottom half, 27 px tall;
 * {@code f = 1.0} gives {@code fillHeight = 54}, scissor {@code [barY, barY + 54)} -- the whole bar, unclipped.
 * No negative height is ever possible: {@code fraction} is {@link Mth#clamp(float, float, float)}-clamped to
 * {@code [0, 1]} before use (guarded against a zero-capacity divide, even though {@code Tank}'s capacity is a
 * positive compile-time constant here), and an empty tank or a fraction of exactly {@code 0} returns before any
 * sprite/scissor call runs at all -- matching {@code RenderTileTank}'s own "don't render anything for an empty
 * tank" rule exactly, one level up (no zero-height sliver is ever drawn, not even a clipped-to-nothing one).
 *
 * <p>Verification limitation, matching every other GUI-adjacent piece of this port (see
 * {@code buildcraft.factory.gui.GuiAutoCraftItems}'s own javadoc): no mouse/keyboard input automation exists in
 * this environment, so this screen's on-screen rendering -- including whether the two bars actually look right --
 * cannot be exercised live. What was verified is a clean compile, a clean dedicated-server boot (this class is
 * still never loaded there -- see the package's own client-only registration), and the fill-fraction/pixel-height
 * arithmetic above, hand-traced rather than eyeballed on screen.
 */
public class GuiAutoCraftFluids extends AbstractContainerScreen<ContainerAutoCraftFluids> {
    private static final int SIZE_X = 176;
    private static final int SIZE_Y = 197;
    private static final int PANEL_COLOR = 0xFFC6_C6C6;
    private static final int TEXT_COLOR = 0x40_4040;

    /** Fluid-bar geometry: two 14px-wide vertical bars in the otherwise-empty panel space to the right of the
     * output slot ({@code x} 124-142) -- clear of every slot {@link ContainerAutoCraftFluids} lays out and of the
     * player inventory (which starts at {@code y} 115), with a 2px margin on every side. */
    private static final int BAR_WIDTH = 14;
    private static final int BAR_HEIGHT = 54;
    private static final int BAR_Y = 16;
    private static final int BAR1_X = 144;
    private static final int BAR2_X = 160;

    public GuiAutoCraftFluids(ContainerAutoCraftFluids menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title, SIZE_X, SIZE_Y);
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractBackground(graphics, mouseX, mouseY, partialTick);
        graphics.fill(leftPos, topPos, leftPos + imageWidth, topPos + imageHeight, PANEL_COLOR);
        drawFluidBar(graphics, menu.tile.tank1, leftPos + BAR1_X, topPos + BAR_Y);
        drawFluidBar(graphics, menu.tile.tank2, leftPos + BAR2_X, topPos + BAR_Y);
        graphics.text(font, "Progress: " + Math.round(menu.getProgress() * 100) + "%", leftPos + 8, topPos + 88, TEXT_COLOR);
    }

    /** Draws one tank's real fluid-level bar at the given top-left pixel position, plus a compact percentage
     * readout underneath -- see this class's own javadoc for the full account of the drawing primitive, the tint
     * handling and the fill-direction math. */
    private void drawFluidBar(GuiGraphicsExtractor graphics, Tank tank, int x, int y) {
        int amount = tank.getAmountAsInt(0);
        int capacity = tank.getCapacity();
        float fraction = capacity > 0 ? Mth.clamp(amount / (float) capacity, 0f, 1f) : 0f;
        graphics.text(font, Math.round(fraction * 100) + "%", x, y + BAR_HEIGHT + 2, TEXT_COLOR);

        FluidResource fluid = tank.getFluidType();
        if (fluid.isEmpty() || fraction <= 0f) {
            return;
        }
        FluidState fluidState = fluid.getFluid().defaultFluidState();
        FluidModel model = Minecraft.getInstance().getModelManager().getFluidStateModelSet().get(fluidState);
        TextureAtlasSprite sprite = model.stillMaterial().sprite();
        int tint = ARGB.opaque(model.tintSource().color(fluidState.createLegacyBlock()));

        int fillHeight = Math.round(BAR_HEIGHT * fraction);
        int fillY = y + (BAR_HEIGHT - fillHeight);
        graphics.enableScissor(x, fillY, x + BAR_WIDTH, y + BAR_HEIGHT);
        graphics.blitSprite(RenderPipelines.GUI_TEXTURED, sprite, x, y, BAR_WIDTH, BAR_HEIGHT, tint);
        graphics.disableScissor();
    }
}
