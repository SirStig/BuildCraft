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
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;

import buildcraft.transport.container.ContainerDiamondWoodPipe;
import buildcraft.transport.pipe.behaviour.PipeBehaviourWoodDiamond.FilterMode;

/**
 * The wood/diamond combo pipe's own screen: the background texture (1.12.2's own {@code pipe_emerald.png}, copied
 * byte-for-byte as {@code pipe_diamond_wood.png} -- this port's own filename, since {@code pipe_emerald.png} names
 * a pipe this port does not have) plus three filter-mode buttons.
 *
 * <p><b>Scope cut: plain vanilla {@link Button} widgets, not 1.12.2's custom pixel icon buttons.</b> 1.12.2 drew
 * three 18x18 icon buttons from {@code pipe_emerald_button.png} via its own {@code GuiImageButton}/
 * {@code IButtonBehaviour} radio-group machinery. Neither {@code GuiImageButton} nor any button-widget framework
 * exists anywhere in {@code buildcraft.lib.gui} on this port yet (confirmed by grepping the whole source tree --
 * this is the first BuildCraft screen with any clickable widget beyond slots), and building one from scratch is
 * out of proportion to this batch's own scope (get the container/menu wired and compiling, per this round's own
 * priorities). Plain vanilla {@link Button#builder} widgets with short text labels stand in instead -- a real,
 * working three-way choice, just not a pixel-accurate one. A future pass can swap these for real icon buttons
 * without touching {@link ContainerDiamondWoodPipe} at all (the client-to-server wiring is already the real,
 * final {@link net.minecraft.world.inventory.AbstractContainerMenu#clickMenuButton} mechanism -- see that class's
 * own javadoc).
 */
public class GuiDiamondWoodPipe extends AbstractContainerScreen<ContainerDiamondWoodPipe> {
    private static final Identifier TEXTURE = Identifier.fromNamespaceAndPath("buildcraft", "textures/gui/pipe_diamond_wood.png");
    private static final int SIZE_X = 175, SIZE_Y = 161;

    public GuiDiamondWoodPipe(ContainerDiamondWoodPipe menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title, SIZE_X, SIZE_Y);
        titleLabelX = (SIZE_X - 8) / 2;
        titleLabelY = 6;
        inventoryLabelX = 8;
        inventoryLabelY = SIZE_Y - 93;
    }

    @Override
    protected void init() {
        super.init();

        addRenderableWidget(Button.builder(Component.translatable("tip.PipeItemsEmerald.whitelist"),
            b -> sendFilterMode(FilterMode.WHITE_LIST)).pos(leftPos + 7, topPos + 41).size(52, 18).build());
        addRenderableWidget(Button.builder(Component.translatable("tip.PipeItemsEmerald.blacklist"),
            b -> sendFilterMode(FilterMode.BLACK_LIST)).pos(leftPos + 61, topPos + 41).size(52, 18).build());
        addRenderableWidget(Button.builder(Component.translatable("tip.PipeItemsEmerald.roundrobin"),
            b -> sendFilterMode(FilterMode.ROUND_ROBIN)).pos(leftPos + 115, topPos + 41).size(52, 18).build());
    }

    private void sendFilterMode(FilterMode mode) {
        if (minecraft != null && minecraft.gameMode != null) {
            minecraft.gameMode.handleInventoryButtonClick(menu.containerId, mode.ordinal());
        }
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractBackground(graphics, mouseX, mouseY, partialTick);
        graphics.blit(RenderPipelines.GUI_TEXTURED, TEXTURE, leftPos, topPos, 0.0F, 0.0F, imageWidth, imageHeight, 256, 256);
    }
}
