/*
 * Copyright (c) 2016 SpaceToad and the BuildCraft team
 * Copyright (c) 2026 Joshua Kac -- NeoForge port (BuildCraft 10)
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.silicon.gui;

import java.util.ArrayList;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;

import buildcraft.silicon.EnumAssemblyRecipeState;
import buildcraft.silicon.container.ContainerAssemblyTable;

/** Mirrors the 26.x copy of this class -- see that one's own javadoc for why the 1.12.2 {@code LedgerTablePower}
 * side-ledger is not ported. */
public class GuiAssemblyTable extends AbstractContainerScreen<ContainerAssemblyTable> {
    private static final ResourceLocation TEXTURE = new ResourceLocation("buildcraft", "textures/gui/assembly_table.png");
    private static final int SIZE_X = 176, SIZE_Y = 220;

    private static final int[] ICON_SAVED = { 176, 0, 16, 16 };
    private static final int[] ICON_SAVED_ENOUGH = { 176, 16, 16, 16 };
    private static final int[] ICON_SAVED_ENOUGH_ACTIVE = { 176, 32, 16, 16 };
    private static final int PROGRESS_U = 176, PROGRESS_V = 48, PROGRESS_W = 4, PROGRESS_H = 70;
    private static final int PROGRESS_X = 86, PROGRESS_Y = 36;

    public GuiAssemblyTable(ContainerAssemblyTable menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        imageWidth = SIZE_X;
        imageHeight = SIZE_Y;
        titleLabelX = 6;
        titleLabelY = 6;
    }

    private int posX(int index) {
        return leftPos + 116 + (index % 3) * 18;
    }

    private int posY(int index) {
        return topPos + 36 + (index / 3) * 18;
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

        ArrayList<EnumAssemblyRecipeState> states = new ArrayList<>(menu.tile.recipesStates.values());
        for (int i = 0; i < states.size() && i < 12; i++) {
            int[] icon = switch (states.get(i)) {
                case SAVED -> ICON_SAVED;
                case SAVED_ENOUGH -> ICON_SAVED_ENOUGH;
                case SAVED_ENOUGH_ACTIVE -> ICON_SAVED_ENOUGH_ACTIVE;
                default -> null;
            };
            if (icon != null) {
                graphics.blit(TEXTURE, posX(i), posY(i), icon[0], icon[1], icon[2], icon[3]);
            }
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            int count = menu.tile.recipesStates.size();
            for (int i = 0; i < count && i < 12; i++) {
                if (mouseX >= posX(i) && mouseX < posX(i) + 16 && mouseY >= posY(i) && mouseY < posY(i) + 16) {
                    if (minecraft != null && minecraft.gameMode != null) {
                        minecraft.gameMode.handleInventoryButtonClick(menu.containerId, i);
                    }
                    return true;
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }
}
