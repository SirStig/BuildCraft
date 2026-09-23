/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * Copyright (c) 2026 Joshua Kac -- NeoForge port (BuildCraft 10)
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL
 * was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.transport.gui;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;

import buildcraft.transport.container.ContainerDiamondPipe;

/**
 * The item/fluid diamond pipe's own screen: just the background texture (1.12.2's own {@code filter.png}, copied
 * byte-for-byte) plus the title and "Inventory" labels -- every filter slot comes from
 * {@link ContainerDiamondPipe}'s own layout. 1.12.2's colour-blind {@code filter_cb.png} swap
 * ({@code BCLibConfig.colourBlindMode}) is not ported: no such config/accessibility toggle exists anywhere in
 * this port yet, so only the standard texture is used. See {@code GuiAutoCraftItems}'s own javadoc for the
 * {@code extractBackground}/{@code GuiGraphicsExtractor} divergence this whole rendering rewrite shares.
 */
public class GuiDiamondPipe extends AbstractContainerScreen<ContainerDiamondPipe> {
    private static final Identifier TEXTURE = Identifier.fromNamespaceAndPath("buildcraft", "textures/gui/filter.png");
    private static final int SIZE_X = 175, SIZE_Y = 225;

    public GuiDiamondPipe(ContainerDiamondPipe menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title, SIZE_X, SIZE_Y);
        titleLabelX = 8;
        titleLabelY = 6;
        inventoryLabelX = 8;
        inventoryLabelY = SIZE_Y - 97;
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractBackground(graphics, mouseX, mouseY, partialTick);
        graphics.blit(RenderPipelines.GUI_TEXTURED, TEXTURE, leftPos, topPos, 0.0F, 0.0F, imageWidth, imageHeight, 256, 256);
    }
}
