/* Copyright (c) 2016 SpaceToad and the BuildCraft team
 * Copyright (c) 2026 Joshua Kac -- NeoForge port (BuildCraft 10)
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/. */
package buildcraft.robotics.gui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

import buildcraft.robotics.container.ContainerZonePlanner;

/**
 * The Zone Planner's screen -- see the 26.x copy of this class for the full account of why 1.12.2's real 3D-scene
 * {@code GuiZonePlanner} is not ported (a scope cut, not an oversight) and why a plain panel fill is used in
 * place of the original's {@code zone_planner.png} (authored for the cut viewport layout, not this trimmed one).
 */
public class GuiZonePlanner extends AbstractContainerScreen<ContainerZonePlanner> {
    private static final int SIZE_X = 176;
    private static final int SIZE_Y = 242;
    private static final int PANEL_COLOR = 0xFFC6_C6C6;

    public GuiZonePlanner(ContainerZonePlanner menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        imageWidth = SIZE_X;
        imageHeight = SIZE_Y;
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        graphics.fill(leftPos, topPos, leftPos + imageWidth, topPos + imageHeight, PANEL_COLOR);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        renderTooltip(graphics, mouseX, mouseY);
    }
}
