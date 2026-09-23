/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * Copyright (c) 2026 Joshua Kac -- NeoForge port (BuildCraft 10)
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL
 * was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.builders.gui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

import buildcraft.builders.container.ContainerFiller;
import buildcraft.builders.filler.FillerPattern;
import buildcraft.builders.tile.TileFiller;

/**
 * Mirrors the 26.x class of the same name -- see that one's javadoc for why the original drag-and-drop pattern
 * palette is replaced by six plain {@link Button}s here. Only the rendering calls differ for this target:
 * {@code GuiGraphics#fill}/{@code renderBg} instead of the 26.x {@code GuiGraphicsExtractor}/
 * {@code extractBackground} split.
 */
public class GuiFiller extends AbstractContainerScreen<ContainerFiller> {
    private static final int SIZE_X = 176;
    private static final int SIZE_Y = 210;

    private Button patternButton;
    private Button param0Button;
    private Button param1Button;
    private Button invertButton;
    private Button excavateButton;
    private Button enabledButton;

    public GuiFiller(ContainerFiller menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        imageWidth = SIZE_X;
        imageHeight = SIZE_Y;
    }

    @Override
    protected void init() {
        super.init();
        int x = leftPos;
        int y = topPos;
        patternButton = addRenderableWidget(
            Button.builder(Component.empty(), b -> click(ContainerFiller.BUTTON_PATTERN))
                .bounds(x + 8, y + 20, 52, 18).build());
        param0Button = addRenderableWidget(
            Button.builder(Component.empty(), b -> click(ContainerFiller.BUTTON_PARAM_0))
                .bounds(x + 64, y + 20, 52, 18).build());
        param1Button = addRenderableWidget(
            Button.builder(Component.empty(), b -> click(ContainerFiller.BUTTON_PARAM_1))
                .bounds(x + 120, y + 20, 52, 18).build());
        invertButton = addRenderableWidget(
            Button.builder(Component.empty(), b -> click(ContainerFiller.BUTTON_INVERT))
                .bounds(x + 8, y + 40, 52, 18).build());
        excavateButton = addRenderableWidget(
            Button.builder(Component.empty(), b -> click(ContainerFiller.BUTTON_EXCAVATE))
                .bounds(x + 64, y + 40, 52, 18).build());
        enabledButton = addRenderableWidget(
            Button.builder(Component.empty(), b -> click(ContainerFiller.BUTTON_ENABLED))
                .bounds(x + 120, y + 40, 52, 18).build());
        updateButtons();
    }

    private void click(int buttonId) {
        if (minecraft != null && minecraft.gameMode != null) {
            minecraft.gameMode.handleInventoryButtonClick(menu.containerId, buttonId);
        }
    }

    private void updateButtons() {
        TileFiller tile = menu.tile;
        FillerPattern pattern = tile.getPattern();
        patternButton.setMessage(Component.translatable(pattern.translationKey()));
        param0Button.setMessage(paramLabel(pattern, 0));
        param1Button.setMessage(paramLabel(pattern, 1));
        param0Button.active = pattern.paramCount() > 0;
        param1Button.active = pattern.paramCount() > 1;
        invertButton.setMessage(Component.translatable(
            tile.isInverted() ? "buildcraft.gui.filler.invert_on" : "buildcraft.gui.filler.invert_off"));
        excavateButton.setMessage(Component.translatable(
            tile.canExcavate() ? "buildcraft.gui.filler.excavate_on" : "buildcraft.gui.filler.excavate_off"));
        enabledButton.setMessage(Component.translatable(
            tile.isEnabled() ? "buildcraft.gui.filler.enabled_on" : "buildcraft.gui.filler.enabled_off"));
    }

    private Component paramLabel(FillerPattern pattern, int index) {
        if (index >= pattern.paramCount()) {
            return Component.translatable("buildcraft.gui.filler.param_none");
        }
        return Component.translatable(pattern.paramLabelKey(index, menu.tile.getParam(index)));
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        super.render(graphics, mouseX, mouseY, partialTick);
        renderTooltip(graphics, mouseX, mouseY);
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        updateButtons();
        graphics.fill(leftPos, topPos, leftPos + imageWidth, topPos + imageHeight, 0xC0303030);
        graphics.fill(leftPos, topPos, leftPos + imageWidth, topPos + 16, 0xC0202020);
    }
}
