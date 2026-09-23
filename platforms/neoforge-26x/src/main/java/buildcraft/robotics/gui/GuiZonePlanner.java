/* Copyright (c) 2016 SpaceToad and the BuildCraft team
 * Copyright (c) 2026 Joshua Kac -- NeoForge port (BuildCraft 10)
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/. */
package buildcraft.robotics.gui;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

import buildcraft.robotics.container.ContainerZonePlanner;

/**
 * The Zone Planner's screen, cut down to what {@link ContainerZonePlanner} actually still lays out.
 *
 * <p><b>1.12.2's {@code GuiZonePlanner} is not ported here at all</b> -- that class was a ~450-line hand-rolled
 * 3D scene (raw {@code GL11}/{@code GLU} immediate-mode calls: a perspective projection pushed and scissored into
 * a sub-rectangle of the 2D screen, a ray-traced mouse pick against a per-chunk client-side map cache, and
 * mouse-drag rectangle painting) with no equivalent anywhere else in this port and no realistic mapping onto
 * either target's modern, retained-mode rendering pipeline in the time budget for this pass -- see
 * {@code TileZonePlanner}'s own javadoc for the matching server-side cut (the whole client-editable-map network
 * path) this leaves nothing dangling from. This is a deliberate, documented scope cut, not an oversight; the data
 * model it would have visualised ({@code ZonePlan}) is fully ported and already synced.
 *
 * <p>What remains is a plain panel background (following {@code GuiAutoCraftFluids}'s own precedent for a block
 * with no authored GUI texture asset yet -- 1.12.2's real {@code zone_planner.png} exists but was authored for
 * the cut 3D viewport layout, not this trimmed one, so reusing it here would be misleading) plus the paintbrush
 * slot grid and player inventory {@link ContainerZonePlanner} lays out.
 */
public class GuiZonePlanner extends AbstractContainerScreen<ContainerZonePlanner> {
    private static final int SIZE_X = 176;
    private static final int SIZE_Y = 242;
    private static final int PANEL_COLOR = 0xFFC6_C6C6;

    public GuiZonePlanner(ContainerZonePlanner menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title, SIZE_X, SIZE_Y);
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractBackground(graphics, mouseX, mouseY, partialTick);
        graphics.fill(leftPos, topPos, leftPos + imageWidth, topPos + imageHeight, PANEL_COLOR);
    }
}
