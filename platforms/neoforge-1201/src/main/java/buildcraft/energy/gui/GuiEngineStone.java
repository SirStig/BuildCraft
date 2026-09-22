/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * Copyright (c) 2026 Joshua Kac -- NeoForge port (BuildCraft 10)
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL
 * was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.energy.gui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;

import buildcraft.energy.container.ContainerEngineStone;

/**
 * The Stirling Engine's screen: background, one fuel slot and a flame-level indicator whose height scales with
 * {@link ContainerEngineStone#getFuelLevel()} -- see the 26.x copy of this class for the full layout description
 * and the dropped-help-tooltip-framework note.
 *
 * <p>This target still has the classic {@code GuiGraphics}/{@code AbstractContainerScreen#renderBg} pair 1.12.2's
 * own {@code GuiEngineStone_BC8#drawGuiContainerBackgroundLayer} evolved into -- contrast the 26.x copy of this
 * class, whose real decompiled sources show a much deeper "render state extraction" rewrite specific to that
 * bleeding-edge target.
 *
 * <p>Verification limitation, matching this port's established caveat for every other GUI-adjacent piece of work:
 * this environment has no mouse/keyboard input automation, so this screen's on-screen rendering cannot be
 * exercised live. See PORTING.md's progress entry for what was actually verified instead (a clean compile, a
 * clean dedicated-server boot, and the underlying tile/container logic via RCON).
 */
public class GuiEngineStone extends AbstractContainerScreen<ContainerEngineStone> {
    private static final ResourceLocation TEXTURE = new ResourceLocation("buildcraft", "textures/gui/steam_engine_gui.png");
    private static final int SIZE_X = 176;
    private static final int SIZE_Y = 166;
    private static final int FLAME_X = 81;
    private static final int FLAME_Y = 25;
    private static final int FLAME_WIDTH = 14;
    private static final int FLAME_HEIGHT = 14;

    public GuiEngineStone(ContainerEngineStone menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        imageWidth = SIZE_X;
        imageHeight = SIZE_Y;
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        graphics.blit(TEXTURE, leftPos, topPos, 0, 0, imageWidth, imageHeight, 256, 256);

        int flameHeight = (int) Math.ceil(Math.min(1.0F, Math.max(0.0F, menu.getFuelLevel())) * FLAME_HEIGHT);
        if (flameHeight > 0) {
            graphics.blit(TEXTURE, leftPos + FLAME_X, topPos + FLAME_Y + (FLAME_HEIGHT - flameHeight),
                SIZE_X, FLAME_HEIGHT - flameHeight, FLAME_WIDTH, flameHeight + 2, 256, 256);
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        renderTooltip(graphics, mouseX, mouseY);
    }
}
