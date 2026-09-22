/*
 * Copyright (c) 2016 SpaceToad and the BuildCraft team
 * Copyright (c) 2026 Joshua Kac -- NeoForge port (BuildCraft 10)
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL
 * was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.factory.gui;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;

import buildcraft.factory.container.ContainerAutoCraftItems;

/**
 * The auto-workbench's screen: background, 3x3 blueprint grid, 3x3 materials grid, result slot and progress bar
 * all come from {@link ContainerAutoCraftItems}'s slot layout -- this class only draws the background texture and
 * the progress bar on top of it.
 *
 * <p><b>Genuine platform/version divergence, not just a rename.</b> 1.12.2's {@code GuiScreen}/{@code GuiContainer}
 * drew through an immediate-mode {@code drawTexturedModalRect}/{@code render}/{@code drawGuiContainerBackgroundLayer}
 * triple. Confirmed via reading this target's real decompiled sources (26.3 has moved to a "render state
 * extraction" architecture): {@code GuiGraphics} does not exist as a class here at all any more, replaced by
 * {@link GuiGraphicsExtractor}, and {@link AbstractContainerScreen} no longer has an abstract
 * {@code renderBg}/{@code render} pair to override -- the background hook is {@code Screen#extractBackground}
 * instead (confirmed against vanilla's own {@code HopperScreen}/{@code AbstractFurnaceScreen}, both read from this
 * target's bundled sources jar and used as the template for this class), called once per frame before slots and
 * tooltips are extracted. This is a deep, actively-evolving rendering rewrite specific to this bleeding-edge
 * target -- 1.20.1's copy of this class is a much closer, near-literal port of the 1.12.2 original (still
 * {@code GuiContainer}/{@code drawGuiContainerBackgroundLayer}).
 *
 * <p><b>The vanilla-recipe-book integration and the filter-overlay icons are both deliberately dropped</b> --
 * see {@code buildcraft.factory.tile.TileAutoWorkbenchBase} and {@code ContainerAutoCraftItems}'s own javadoc for
 * why.
 *
 * <p><b>Verification limitation, matching this port's established caveat for every other GUI-adjacent piece of
 * work</b> (block models, the flood gate's wrench gesture, the creative-tab fix): this environment has no mouse/
 * keyboard input automation, so this screen's on-screen rendering and click-to-place-item interaction cannot be
 * exercised live. What was verified is a clean compile against this target's real API, a clean
 * {@code :neoforge-26x:runClient} boot with no class-loading crash naming this class (see PORTING.md's progress
 * entry), and the underlying tile/container logic via RCON, bypassing the GUI entirely.
 */
public class GuiAutoCraftItems extends AbstractContainerScreen<ContainerAutoCraftItems> {
    private static final Identifier TEXTURE = Identifier.fromNamespaceAndPath("buildcraft", "textures/gui/autobench_item.png");
    private static final int SIZE_X = 176;
    private static final int SIZE_Y = 197;
    private static final int PROGRESS_X = 90;
    private static final int PROGRESS_Y = 47;
    private static final int PROGRESS_WIDTH = 23;
    private static final int PROGRESS_HEIGHT = 10;

    public GuiAutoCraftItems(ContainerAutoCraftItems menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title, SIZE_X, SIZE_Y);
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractBackground(graphics, mouseX, mouseY, partialTick);
        graphics.blit(RenderPipelines.GUI_TEXTURED, TEXTURE, leftPos, topPos, 0.0F, 0.0F, imageWidth, imageHeight, 256, 256);

        int width = Mth.ceil(PROGRESS_WIDTH * Mth.clamp(menu.getProgress(), 0.0F, 1.0F));
        if (width > 0) {
            graphics.blit(RenderPipelines.GUI_TEXTURED, TEXTURE, leftPos + PROGRESS_X, topPos + PROGRESS_Y,
                SIZE_X, 0.0F, width, PROGRESS_HEIGHT, 256, 256);
        }
    }
}
