/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * Copyright (c) 2026 Joshua Kac -- NeoForge port (BuildCraft 10)
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL
 * was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.factory.gui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

import buildcraft.factory.container.ContainerAutoCraftFluids;

/**
 * Mirrors the 26.x copy of this class -- see that one's own javadoc for why this is a deliberately bare-minimum
 * screen (no fluid-tank-rendering precedent anywhere in this port, and no genuine GUI texture ever existed for
 * this block in 1.12.2 either). This file differs only in keeping the classic {@code GuiGraphics}/
 * {@code AbstractContainerScreen#renderBg} pair, exactly like {@link GuiAutoCraftItems}'s own 1.20.1 copy.
 *
 * <p>Verification limitation, matching every other GUI-adjacent piece of this port: no mouse/keyboard input
 * automation exists in this environment, so this screen's on-screen rendering cannot be exercised live. What was
 * verified is a clean compile, a clean dedicated-server boot, and the underlying tile/container/capability logic
 * via RCON.
 */
public class GuiAutoCraftFluids extends AbstractContainerScreen<ContainerAutoCraftFluids> {
    private static final int PANEL_COLOR = 0xFFC6_C6C6;
    private static final int TEXT_COLOR = 0x40_4040;

    public GuiAutoCraftFluids(ContainerAutoCraftFluids menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        imageWidth = 176;
        imageHeight = 197;
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        graphics.fill(leftPos, topPos, leftPos + imageWidth, topPos + imageHeight, PANEL_COLOR);
        graphics.drawString(font, "Tank 1: " + menu.tile.tank1.getContentsString(), leftPos + 8, topPos + 68, TEXT_COLOR, false);
        graphics.drawString(font, "Tank 2: " + menu.tile.tank2.getContentsString(), leftPos + 8, topPos + 78, TEXT_COLOR, false);
        graphics.drawString(font, "Progress: " + Math.round(menu.getProgress() * 100) + "%", leftPos + 8, topPos + 88, TEXT_COLOR, false);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        renderTooltip(graphics, mouseX, mouseY);
    }
}
