/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * Copyright (c) 2026 Joshua Kac -- NeoForge port (BuildCraft 10)
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL
 * was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.transport.gui;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

import buildcraft.lib.statement.ActionWrapper;
import buildcraft.lib.statement.TriggerWrapper;

import buildcraft.transport.container.ContainerGate;
import buildcraft.transport.gate.GateLogic;

/**
 * A wholly new, minimal gate configuration screen -- not a port of 1.12.2's own {@code GuiGate} (a hand-drawn
 * icon grid with per-side colour-coded ledgers, hover tooltips and a custom texture). That is squarely a
 * rendering-polish task, out of this batch's scope the same way every pluggable's model is (see
 * {@link buildcraft.transport.plug.PluggableGate}'s own javadoc); what this batch needs is a GUI that genuinely
 * opens and lets a player configure a real gate, which plain vanilla {@link Button} widgets do perfectly well.
 *
 * <p>One row per gate slot: a button cycling the slot's trigger, a button cycling its action (both via
 * {@link ContainerGate#clickMenuButton}, vanilla's own button-click packet -- see that class's own javadoc), and
 * -- between two consecutive slots -- a small toggle for {@link GateLogic#connections}. Labels are refreshed
 * every {@link #containerTick()} from the container's own live state, which is itself kept current by
 * {@link ContainerGate}'s {@link net.minecraft.world.inventory.DataSlot}s.
 */
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
        super(menu, playerInventory, title, WIDTH, TOP_MARGIN + menu.pluggable.logic.statements.length * ROW_HEIGHT + BOTTOM_MARGIN);
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
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractBackground(graphics, mouseX, mouseY, partialTick);
        graphics.fill(leftPos, topPos, leftPos + imageWidth, topPos + imageHeight, 0xC0202020);
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        // No player-inventory slots exist on this menu, so only the title is drawn -- the vanilla
        // "Inventory" label would otherwise float above nothing.
        graphics.text(font, title, titleLabelX, titleLabelY, 0xFFFFFF, false);
    }
}
