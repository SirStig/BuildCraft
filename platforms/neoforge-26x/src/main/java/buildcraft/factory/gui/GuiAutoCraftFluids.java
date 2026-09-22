/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * Copyright (c) 2026 Joshua Kac -- NeoForge port (BuildCraft 10)
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL
 * was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.factory.gui;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

import buildcraft.factory.container.ContainerAutoCraftFluids;

/**
 * A deliberately bare-minimum screen -- there is no fluid-tank-rendering widget anywhere in this port to build on
 * (no other tile with a {@code Tank} field, {@code TilePump}/{@code TileFloodGate}/{@code TileTank}, has a
 * GUI/container at all yet), and no genuine GUI texture for this block ever existed in 1.12.2 either (see
 * {@code buildcraft.factory.tile.TileAutoWorkbenchFluids}'s own javadoc: this block's registration was commented
 * out there, so no {@code autobench_fluid.png} was ever drawn). Rather than invent a fluid-level bar/sprite from
 * nothing, this screen paints a plain panel and the two tanks' contents as plain text (via
 * {@code menu.tile.tank1}/{@code tank2}, read directly off the synced tile -- see {@link ContainerAutoCraftFluids}'s
 * own javadoc for why no extra network plumbing is needed for that) alongside the same slot layout
 * {@link ContainerAutoCraftFluids} lays out. This is a known, explicitly documented scope cut -- see PORTING.md.
 *
 * <p>Verification limitation, matching every other GUI-adjacent piece of this port (see
 * {@code buildcraft.factory.gui.GuiAutoCraftItems}'s own javadoc): no mouse/keyboard input automation exists in
 * this environment, so this screen's on-screen rendering cannot be exercised live. What was verified is a clean
 * compile, a clean dedicated-server boot, and the underlying tile/container/capability logic via RCON.
 */
public class GuiAutoCraftFluids extends AbstractContainerScreen<ContainerAutoCraftFluids> {
    private static final int SIZE_X = 176;
    private static final int SIZE_Y = 197;
    private static final int PANEL_COLOR = 0xFFC6_C6C6;
    private static final int TEXT_COLOR = 0x40_4040;

    public GuiAutoCraftFluids(ContainerAutoCraftFluids menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title, SIZE_X, SIZE_Y);
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractBackground(graphics, mouseX, mouseY, partialTick);
        graphics.fill(leftPos, topPos, leftPos + imageWidth, topPos + imageHeight, PANEL_COLOR);
        graphics.text(font, "Tank 1: " + menu.tile.tank1.getContentsString(), leftPos + 8, topPos + 68, TEXT_COLOR);
        graphics.text(font, "Tank 2: " + menu.tile.tank2.getContentsString(), leftPos + 8, topPos + 78, TEXT_COLOR);
        graphics.text(font, "Progress: " + Math.round(menu.getProgress() * 100) + "%", leftPos + 8, topPos + 88, TEXT_COLOR);
    }
}
