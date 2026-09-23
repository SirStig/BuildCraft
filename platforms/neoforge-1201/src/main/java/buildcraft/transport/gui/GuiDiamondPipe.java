/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * Copyright (c) 2026 Joshua Kac -- NeoForge port (BuildCraft 10)
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL
 * was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.transport.gui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;

import buildcraft.transport.container.ContainerDiamondPipe;

/** The item/fluid diamond pipe's own screen -- see the 26.x copy of this class for the full account (1.12.2's
 * own {@code filter.png}, copied byte-for-byte; the colour-blind swap is not ported). This target still has the
 * classic {@code GuiGraphics}/{@code AbstractContainerScreen#renderBg} pair -- see {@code GuiAutoCraftItems}'s
 * own javadoc for the identical divergence from 26.x's rewrite. */
public class GuiDiamondPipe extends AbstractContainerScreen<ContainerDiamondPipe> {
    private static final ResourceLocation TEXTURE = new ResourceLocation("buildcraft", "textures/gui/filter.png");
    private static final int SIZE_X = 175, SIZE_Y = 225;

    public GuiDiamondPipe(ContainerDiamondPipe menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        imageWidth = SIZE_X;
        imageHeight = SIZE_Y;
        titleLabelX = 8;
        titleLabelY = 6;
        inventoryLabelX = 8;
        inventoryLabelY = SIZE_Y - 97;
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        graphics.blit(TEXTURE, leftPos, topPos, 0, 0, imageWidth, imageHeight, 256, 256);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        renderTooltip(graphics, mouseX, mouseY);
    }
}
