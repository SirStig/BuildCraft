/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * Copyright (c) 2026 Joshua Kac -- NeoForge port (BuildCraft 10)
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL
 * was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.energy.gui;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;

import buildcraft.energy.container.ContainerEngineStone;

/**
 * The Stirling Engine's screen: background, one fuel slot (from {@link ContainerEngineStone}'s slot layout) and a
 * flame-level indicator whose height scales with {@link ContainerEngineStone#getFuelLevel()} -- much smaller than
 * {@code GuiAutoCraftItems}, no phantom-slot grid at all here.
 *
 * <p>Genuine platform/version divergence, not just a rename -- see {@code GuiAutoCraftItems}'s own javadoc for the
 * full account of why 26.3's "render state extraction" architecture ({@link GuiGraphicsExtractor}, no
 * {@code renderBg}) replaces 1.12.2's {@code drawGuiContainerBackgroundLayer}; the 1.20.1 copy of this class is
 * the much closer, near-literal port.
 *
 * <p><b>The in-GUI help/tooltip framework ({@code LedgerEngine}, {@code DummyHelpElement},
 * {@code ElementHelpInfo}) is deliberately dropped</b>, along with 1.12.2's manual title/"gui.inventory" string
 * drawing -- {@link AbstractContainerScreen} already draws both via {@code title}/{@code inventoryLabel}, so this
 * class doesn't need to lay them out itself. See {@code TileEngineStone}'s own javadoc for why the help/tooltip
 * system is out of scope for this pass.
 *
 * <p><b>Verification limitation, matching this port's established caveat for every other GUI-adjacent piece of
 * work</b> (the auto-workbench batch, block models, the flood gate's wrench gesture): this environment has no
 * mouse/keyboard input automation, so this screen's on-screen rendering cannot be exercised live. What was
 * verified is a clean compile against this target's real API, a clean {@code :neoforge-26x:runClient} boot with
 * no class-loading crash naming this class, and the underlying tile/container logic via RCON, bypassing the GUI
 * entirely -- see PORTING.md's progress entry.
 */
public class GuiEngineStone extends AbstractContainerScreen<ContainerEngineStone> {
    private static final Identifier TEXTURE = Identifier.fromNamespaceAndPath("buildcraft", "textures/gui/steam_engine_gui.png");
    private static final int SIZE_X = 176;
    private static final int SIZE_Y = 166;
    private static final int FLAME_X = 81;
    private static final int FLAME_Y = 25;
    private static final int FLAME_WIDTH = 14;
    private static final int FLAME_HEIGHT = 14;

    public GuiEngineStone(ContainerEngineStone menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title, SIZE_X, SIZE_Y);
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractBackground(graphics, mouseX, mouseY, partialTick);
        graphics.blit(RenderPipelines.GUI_TEXTURED, TEXTURE, leftPos, topPos, 0.0F, 0.0F, imageWidth, imageHeight, 256, 256);

        int flameHeight = Mth.ceil(Mth.clamp(menu.getFuelLevel(), 0.0F, 1.0F) * FLAME_HEIGHT);
        if (flameHeight > 0) {
            graphics.blit(RenderPipelines.GUI_TEXTURED, TEXTURE,
                leftPos + FLAME_X, topPos + FLAME_Y + (FLAME_HEIGHT - flameHeight),
                SIZE_X, FLAME_HEIGHT - flameHeight, FLAME_WIDTH, flameHeight + 2, 256, 256);
        }
    }
}
