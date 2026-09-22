/*
 * Copyright (c) 2016 SpaceToad and the BuildCraft team
 * Copyright (c) 2026 Joshua Kac -- NeoForge port (BuildCraft 10)
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL
 * was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.factory.gui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;

import buildcraft.factory.container.ContainerAutoCraftItems;

/**
 * The auto-workbench's screen: background, 3x3 blueprint grid, 3x3 materials grid, result slot and progress bar
 * all come from {@link ContainerAutoCraftItems}'s slot layout -- this class only draws the background texture and
 * the progress bar on top of it.
 *
 * <p>This target still has the classic {@code GuiGraphics}/{@code AbstractContainerScreen#renderBg} pair 1.12.2's
 * own {@code GuiScreen}/{@code GuiContainer} evolved into -- contrast the 26.x copy of this class, whose real
 * decompiled sources show a much deeper, actively-evolving "render state extraction" rewrite specific to that
 * bleeding-edge target (see that class's own javadoc for the full account). This file is the much closer,
 * near-literal port of 1.12.2's own {@code GuiAutoCraftItems#drawGuiContainerBackgroundLayer}.
 *
 * <p>The vanilla-recipe-book integration and the filter-overlay icons are both deliberately dropped -- see
 * {@code buildcraft.factory.tile.TileAutoWorkbenchBase} and {@code ContainerAutoCraftItems}'s own javadoc for why.
 *
 * <p>Verification limitation, matching this port's established caveat for every other GUI-adjacent piece of work:
 * this environment has no mouse/keyboard input automation, so this screen's on-screen rendering and click-to-
 * place-item interaction cannot be exercised live. See PORTING.md's progress entry for what was actually verified
 * instead (a clean compile, a clean dedicated-server boot, and the underlying tile/container logic via RCON).
 */
public class GuiAutoCraftItems extends AbstractContainerScreen<ContainerAutoCraftItems> {
    private static final ResourceLocation TEXTURE = new ResourceLocation("buildcraft", "textures/gui/autobench_item.png");
    private static final int SIZE_X = 176;
    private static final int SIZE_Y = 197;
    private static final int PROGRESS_X = 90;
    private static final int PROGRESS_Y = 47;
    private static final int PROGRESS_WIDTH = 23;
    private static final int PROGRESS_HEIGHT = 10;

    public GuiAutoCraftItems(ContainerAutoCraftItems menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        imageWidth = SIZE_X;
        imageHeight = SIZE_Y;
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        graphics.blit(TEXTURE, leftPos, topPos, 0, 0, imageWidth, imageHeight, 256, 256);

        int width = (int) Math.ceil(PROGRESS_WIDTH * Math.min(1.0F, Math.max(0.0F, menu.getProgress())));
        if (width > 0) {
            graphics.blit(TEXTURE, leftPos + PROGRESS_X, topPos + PROGRESS_Y, SIZE_X, 0, width, PROGRESS_HEIGHT, 256, 256);
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        renderTooltip(graphics, mouseX, mouseY);
    }
}
