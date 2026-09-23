/*
 * Copyright (c) 2016 SpaceToad and the BuildCraft team
 * Copyright (c) 2026 Joshua Kac -- NeoForge port (BuildCraft 10)
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.silicon.gui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;

import buildcraft.silicon.container.ContainerAdvancedCraftingTable;

/** Mirrors the 26.x copy of this class -- see that one's own javadoc for why the vanilla recipe-book integration
 * is not ported this round. */
public class GuiAdvancedCraftingTable extends AbstractContainerScreen<ContainerAdvancedCraftingTable> {
    private static final ResourceLocation TEXTURE = new ResourceLocation("buildcraft", "textures/gui/advanced_crafting_table.png");
    private static final int SIZE_X = 176, SIZE_Y = 241;
    private static final int PROGRESS_U = 176, PROGRESS_V = 0, PROGRESS_W = 4, PROGRESS_H = 70;
    private static final int PROGRESS_X = 164, PROGRESS_Y = 7;

    public GuiAdvancedCraftingTable(ContainerAdvancedCraftingTable menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        imageWidth = SIZE_X;
        imageHeight = SIZE_Y;
        titleLabelX = 6;
        titleLabelY = 5;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        super.render(graphics, mouseX, mouseY, partialTick);
        renderTooltip(graphics, mouseX, mouseY);
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        graphics.blit(TEXTURE, leftPos, topPos, 0, 0, SIZE_X, SIZE_Y);

        long target = menu.tile.getTarget();
        if (target != 0) {
            double v = (double) menu.tile.power / target;
            int fillHeight = (int) Math.ceil(PROGRESS_H * Math.min(v, 1));
            int startY = (int) (PROGRESS_H * Math.max(1 - v, 0));
            if (fillHeight > 0) {
                graphics.blit(TEXTURE, leftPos + PROGRESS_X, topPos + PROGRESS_Y + startY,
                    PROGRESS_U, PROGRESS_V + startY, PROGRESS_W, fillHeight);
            }
        }
    }
}
