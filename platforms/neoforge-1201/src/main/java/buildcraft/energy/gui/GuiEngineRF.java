/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * Copyright (c) 2026 Joshua Kac -- NeoForge port (BuildCraft 10)
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.energy.gui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

import buildcraft.energy.container.ContainerEngineRF;

/**
 * The RF Engine's screen -- see the 26.x copy of this class for the full account of the plain-panel background
 * and RF-level bar this mirrors.
 */
public class GuiEngineRF extends AbstractContainerScreen<ContainerEngineRF> {
    private static final int SIZE_X = 176;
    private static final int SIZE_Y = 166;
    private static final int PANEL_COLOR = 0xFFC6_C6C6;
    private static final int BAR_COLOR = 0xFF40_A0FF;
    private static final int BAR_X = 8;
    private static final int BAR_Y = 20;
    private static final int BAR_WIDTH = 160;
    private static final int BAR_HEIGHT = 8;

    public GuiEngineRF(ContainerEngineRF menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        imageWidth = SIZE_X;
        imageHeight = SIZE_Y;
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        graphics.fill(leftPos, topPos, leftPos + imageWidth, topPos + imageHeight, PANEL_COLOR);

        int barWidth = Math.round(Math.min(1.0F, Math.max(0.0F, menu.getRfLevel())) * BAR_WIDTH);
        if (barWidth > 0) {
            graphics.fill(leftPos + BAR_X, topPos + BAR_Y, leftPos + BAR_X + barWidth, topPos + BAR_Y + BAR_HEIGHT, BAR_COLOR);
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        renderTooltip(graphics, mouseX, mouseY);
    }
}
