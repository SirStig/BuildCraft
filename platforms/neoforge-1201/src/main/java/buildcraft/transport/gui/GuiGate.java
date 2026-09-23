/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * Copyright (c) 2026 Joshua Kac -- NeoForge port (BuildCraft 10)
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL
 * was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.transport.gui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

import buildcraft.lib.statement.ActionWrapper;
import buildcraft.lib.statement.TriggerWrapper;

import buildcraft.transport.container.ContainerGate;
import buildcraft.transport.gate.GateLogic;

/** See the 26.x copy of this class for the full account (a wholly new, minimal vanilla-{@link Button} gate
 * screen, not a port of 1.12.2's own hand-drawn {@code GuiGate}). Only real divergence: this target's classic
 * {@link GuiGraphics}/{@code renderBg}/{@code renderLabels}/{@code render} method shapes, predating 26.x's
 * {@code GuiGraphicsExtractor}/{@code extractBackground}/{@code extractLabels} split. */
public class GuiGate extends AbstractContainerScreen<ContainerGate> {

    private static final int WIDTH = 300;
    private static final int TOP_MARGIN = 24;
    private static final int ROW_HEIGHT = 22;
    private static final int BOTTOM_MARGIN = 12;
    private static final int BUTTON_WIDTH = 140;

    private Button[] triggerButtons;
    private Button[] actionButtons;
    private Button[] connectionButtons;

    public GuiGate(ContainerGate menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        this.imageWidth = WIDTH;
        this.imageHeight = TOP_MARGIN + menu.pluggable.logic.statements.length * ROW_HEIGHT + BOTTOM_MARGIN;
    }

    @Override
    protected void init() {
        super.init();
        GateLogic logic = menu.pluggable.logic;
        triggerButtons = new Button[logic.statements.length];
        actionButtons = new Button[logic.statements.length];
        connectionButtons = new Button[logic.connections.length];

        for (int i = 0; i < logic.statements.length; i++) {
            int slot = i;
            int y = topPos + TOP_MARGIN + slot * ROW_HEIGHT;
            triggerButtons[slot] = addRenderableWidget(
                Button.builder(triggerLabel(slot), b -> click(ContainerGate.triggerButtonId(slot)))
                    .bounds(leftPos + 8, y, BUTTON_WIDTH, 20)
                    .build()
            );
            actionButtons[slot] = addRenderableWidget(
                Button.builder(actionLabel(slot), b -> click(ContainerGate.actionButtonId(slot)))
                    .bounds(leftPos + 8 + BUTTON_WIDTH + 4, y, BUTTON_WIDTH, 20)
                    .build()
            );
        }
        for (int i = 0; i < logic.connections.length; i++) {
            int slot = i;
            int y = topPos + TOP_MARGIN + (slot + 1) * ROW_HEIGHT - 7;
            connectionButtons[slot] = addRenderableWidget(
                Button.builder(connectionLabel(slot), b -> click(ContainerGate.connectionButtonId(slot)))
                    .bounds(leftPos + WIDTH / 2 - 15, y, 30, 12)
                    .build()
            );
        }
    }

    private void click(int buttonId) {
        minecraft.gameMode.handleInventoryButtonClick(menu.containerId, buttonId);
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        for (int i = 0; i < triggerButtons.length; i++) {
            triggerButtons[i].setMessage(triggerLabel(i));
            actionButtons[i].setMessage(actionLabel(i));
        }
        for (int i = 0; i < connectionButtons.length; i++) {
            connectionButtons[i].setMessage(connectionLabel(i));
        }
    }

    private Component triggerLabel(int slot) {
        TriggerWrapper trigger = menu.pluggable.logic.statements[slot].trigger.get();
        return trigger == null ? Component.translatable("buildcraft.gui.gate.slot_none") : trigger.getDescription();
    }

    private Component actionLabel(int slot) {
        ActionWrapper action = menu.pluggable.logic.statements[slot].action.get();
        return action == null ? Component.translatable("buildcraft.gui.gate.slot_none") : action.getDescription();
    }

    private Component connectionLabel(int slot) {
        return Component.literal(menu.pluggable.logic.connections[slot] ? "AND" : "|");
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        graphics.fill(leftPos, topPos, leftPos + imageWidth, topPos + imageHeight, 0xC0202020);
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        // No player-inventory slots exist on this menu, so only the title is drawn.
        graphics.drawString(font, title, titleLabelX, titleLabelY, 0xFFFFFF, false);
    }
}
