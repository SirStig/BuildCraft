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
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;

import buildcraft.transport.container.ContainerDiamondWoodPipe;
import buildcraft.transport.pipe.behaviour.PipeBehaviourWoodDiamond.FilterMode;

/** The wood/diamond combo pipe's own screen -- see the 26.x copy of this class for the full account, including
 * the scope cut (plain vanilla {@link Button} widgets rather than 1.12.2's custom pixel icon buttons/
 * {@code GuiImageButton} -- no button-widget framework exists in {@code buildcraft.lib.gui} on this target
 * either). {@code Button.Builder} takes {@code bounds(x, y, w, h)} here rather than 26.x's {@code pos}/{@code
 * size} pair (confirmed via {@code javap} against this target's own Forge sources -- a real, small API
 * divergence, not a typo). */
public class GuiDiamondWoodPipe extends AbstractContainerScreen<ContainerDiamondWoodPipe> {
    private static final ResourceLocation TEXTURE = new ResourceLocation("buildcraft", "textures/gui/pipe_diamond_wood.png");
    private static final int SIZE_X = 175, SIZE_Y = 161;

    public GuiDiamondWoodPipe(ContainerDiamondWoodPipe menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        imageWidth = SIZE_X;
        imageHeight = SIZE_Y;
        titleLabelX = (SIZE_X - 8) / 2;
        titleLabelY = 6;
        inventoryLabelX = 8;
        inventoryLabelY = SIZE_Y - 93;
    }

    @Override
    protected void init() {
        super.init();

        addRenderableWidget(Button.builder(Component.translatable("tip.PipeItemsEmerald.whitelist"),
            b -> sendFilterMode(FilterMode.WHITE_LIST)).bounds(leftPos + 7, topPos + 41, 52, 18).build());
        addRenderableWidget(Button.builder(Component.translatable("tip.PipeItemsEmerald.blacklist"),
            b -> sendFilterMode(FilterMode.BLACK_LIST)).bounds(leftPos + 61, topPos + 41, 52, 18).build());
        addRenderableWidget(Button.builder(Component.translatable("tip.PipeItemsEmerald.roundrobin"),
            b -> sendFilterMode(FilterMode.ROUND_ROBIN)).bounds(leftPos + 115, topPos + 41, 52, 18).build());
    }

    private void sendFilterMode(FilterMode mode) {
        if (minecraft != null && minecraft.gameMode != null) {
            minecraft.gameMode.handleInventoryButtonClick(menu.containerId, mode.ordinal());
        }
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        graphics.blit(TEXTURE, leftPos, topPos, 0, 0, imageWidth, imageHeight, 256, 256);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        renderTooltip(graphics, mouseX, mouseY);
    }
}
