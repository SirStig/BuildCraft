/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * Copyright (c) 2026 Joshua Kac -- NeoForge port (BuildCraft 10)
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL
 * was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.energy.gui;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.InventoryMenu;

import net.minecraftforge.client.extensions.common.IClientFluidTypeExtensions;
import net.minecraftforge.fluids.FluidStack;

import buildcraft.energy.container.ContainerEngineIron;

/**
 * The Combustion Engine's screen (1.12.2's {@code GuiEngineIron_BC8}) -- see the 26.x copy for the layout, the
 * tiled fluid drawing with its hand-traced scissor arithmetic, the tank clicks and the tooltips. The fluid sprite and
 * tint come from {@link IClientFluidTypeExtensions} and the block atlas, as in this target's
 * {@code GuiAutoCraftFluids}; the drawing calls are {@code GuiGraphics#blit(int, int, int, int, int,
 * TextureAtlasSprite, float, float, float, float)} and {@code enableScissor}/{@code disableScissor}.
 */
public class GuiEngineIron extends AbstractContainerScreen<ContainerEngineIron> {
    private static final ResourceLocation TEXTURE = new ResourceLocation("buildcraft", "textures/gui/combustion_engine_gui.png");
    private static final int SIZE_X = 176;
    private static final int SIZE_Y = 177;
    private static final int TANK_Y = 18;
    private static final int TANK_WIDTH = 16;
    private static final int TANK_HEIGHT = 60;
    private static final int[] TANK_X = { 26, 80, 134 };
    private static final int OVERLAY_U = 176;
    private static final int OVERLAY_V = 0;

    public GuiEngineIron(ContainerEngineIron menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        imageWidth = SIZE_X;
        imageHeight = SIZE_Y;
    }

    @Override
    protected void init() {
        super.init();
        titleLabelX = (imageWidth - font.width(title)) / 2;
        inventoryLabelY = imageHeight - 96;
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        graphics.blit(TEXTURE, leftPos, topPos, 0, 0, imageWidth, imageHeight, 256, 256);
        for (int i = 0; i < TANK_X.length; i++) {
            int x = leftPos + TANK_X[i];
            int y = topPos + TANK_Y;
            drawFluid(graphics, menu.getClientFluid(i), menu.getTankCapacity(), x, y);
            graphics.blit(TEXTURE, x, y, OVERLAY_U, OVERLAY_V, TANK_WIDTH, TANK_HEIGHT, 256, 256);
        }
    }

    private static void drawFluid(GuiGraphics graphics, FluidStack fluid, int capacity, int x, int y) {
        if (fluid.isEmpty() || capacity <= 0) {
            return;
        }
        float fraction = Mth.clamp(fluid.getAmount() / (float) capacity, 0f, 1f);
        int fillHeight = Math.round(TANK_HEIGHT * fraction);
        if (fillHeight <= 0) {
            return;
        }
        IClientFluidTypeExtensions extensions = IClientFluidTypeExtensions.of(fluid.getFluid());
        ResourceLocation textureLocation = extensions.getStillTexture(fluid);
        TextureAtlasSprite sprite = Minecraft.getInstance().getTextureAtlas(InventoryMenu.BLOCK_ATLAS).apply(textureLocation);
        int argb = extensions.getTintColor(fluid);
        float r = ((argb >> 16) & 0xFF) / 255f;
        float g = ((argb >> 8) & 0xFF) / 255f;
        float b = (argb & 0xFF) / 255f;

        graphics.enableScissor(x, y + TANK_HEIGHT - fillHeight, x + TANK_WIDTH, y + TANK_HEIGHT);
        for (int tileY = y + TANK_HEIGHT - 16; tileY > y - 16; tileY -= 16) {
            graphics.blit(x, tileY, 0, TANK_WIDTH, 16, sprite, r, g, b, 1f);
        }
        graphics.disableScissor();
    }

    private int getHoveredTank(double mouseX, double mouseY) {
        for (int i = 0; i < TANK_X.length; i++) {
            if (isHovering(TANK_X[i], TANK_Y, TANK_WIDTH, TANK_HEIGHT, mouseX, mouseY)) {
                return i;
            }
        }
        return -1;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int tank = getHoveredTank(mouseX, mouseY);
        if (tank >= 0 && !menu.getCarried().isEmpty() && minecraft != null && minecraft.gameMode != null) {
            minecraft.gameMode.handleInventoryButtonClick(menu.containerId, tank);
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        renderTooltip(graphics, mouseX, mouseY);
        int tank = getHoveredTank(mouseX, mouseY);
        if (tank >= 0 && menu.getCarried().isEmpty()) {
            FluidStack fluid = menu.getClientFluid(tank);
            List<Component> lines = new ArrayList<>();
            if (!fluid.isEmpty()) {
                lines.add(fluid.getDisplayName());
            }
            lines.add(Component.literal(fluid.getAmount() + " / " + menu.getTankCapacity() + " mB").withStyle(ChatFormatting.GRAY));
            graphics.renderComponentTooltip(font, lines, mouseX, mouseY);
        }
    }
}
