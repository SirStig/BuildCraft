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
import net.minecraft.client.color.block.BlockTintSource;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.block.FluidModel;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.level.material.FluidState;

import net.neoforged.neoforge.fluids.FluidStack;

import buildcraft.energy.container.ContainerEngineIron;

/**
 * Renamed from 1.12.2's {@code GuiEngineIron_BC8}: the original {@code combustion_engine_gui.png} background
 * (176 x 177, copied byte-for-byte), and three 16 x 60 fluid tanks at x = 26, 80 and 134, y = 18 -- fuel, coolant,
 * residue -- each drawn as the fluid's still sprite with the gauge overlay from (176, 0) on top, exactly 1.12.2's
 * {@code GuiRectangle}s and {@code ICON_TANK_OVERLAY}. Title and "Inventory" labels sit where 1.12.2 drew them: the
 * title centred at y = 6, the inventory label at x = 8, y = {@code SIZE_Y - 96}.
 *
 * <p><b>Fluid drawing</b> reuses {@code buildcraft.factory.gui.GuiAutoCraftFluids}' lookup (the fluid's
 * {@link FluidModel} from {@code getFluidStateModelSet()}, its still sprite, and its tint source, which is
 * {@code null} for vanilla lava) and its scissor technique for the partial fill. One difference: 1.12.2's
 * {@code GuiUtil.drawFluid}/{@code FluidRenderer.drawFluidForGui} <em>tiled</em> the 16 x 16 sprite rather than
 * stretching it, so this draws whole 16 x 16 sprites stacked upward from the tank's bottom edge and lets the scissor
 * cut the top one. Hand-traced for a 60 px tank at screen y = {@code Y}: the tiles land at {@code Y+44}, {@code Y+28},
 * {@code Y+12} and {@code Y-4} (four tiles, since {@code ceil(60 / 16) = 4}); a tank at 5000/10000 mB gives
 * {@code fillHeight = round(60 * 0.5) = 30}, scissor rows {@code [Y+30, Y+60)}, so the bottom tile shows whole, the
 * second shows its lower 14 rows ({@code Y+30..Y+43}) and the other two are clipped away; 10000/10000 gives
 * {@code fillHeight = 60}, scissor {@code [Y, Y+60)}, and the top tile's upper 4 rows (above {@code Y}) are clipped;
 * 1/10000 gives {@code round(0.006) = 0} and draws nothing, and an empty tank returns before any drawing.
 *
 * <p><b>Tank clicks</b> send the tank index as a menu-button click (see {@link ContainerEngineIron}); only while an
 * item is on the cursor, as 1.12.2's {@code Tank#onGuiClicked} ignored empty-handed clicks. <b>Tooltips</b> show
 * 1.12.2's tank tooltip: the fluid name (when not empty) and the amount out of the capacity. The engine ledger
 * ({@code LedgerEngine}) and help overlay are not ported, as for the Stirling engine.
 */
public class GuiEngineIron extends AbstractContainerScreen<ContainerEngineIron> {
    private static final Identifier TEXTURE = Identifier.fromNamespaceAndPath("buildcraft", "textures/gui/combustion_engine_gui.png");
    private static final int SIZE_X = 176;
    private static final int SIZE_Y = 177;
    private static final int TANK_Y = 18;
    private static final int TANK_WIDTH = 16;
    private static final int TANK_HEIGHT = 60;
    private static final int[] TANK_X = { 26, 80, 134 };
    private static final int OVERLAY_U = 176;
    private static final int OVERLAY_V = 0;

    public GuiEngineIron(ContainerEngineIron menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title, SIZE_X, SIZE_Y);
    }

    @Override
    protected void init() {
        super.init();
        titleLabelX = (imageWidth - font.width(title)) / 2;
        inventoryLabelY = imageHeight - 96;
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractBackground(graphics, mouseX, mouseY, partialTick);
        graphics.blit(RenderPipelines.GUI_TEXTURED, TEXTURE, leftPos, topPos, 0.0F, 0.0F, imageWidth, imageHeight, 256, 256);
        for (int i = 0; i < TANK_X.length; i++) {
            int x = leftPos + TANK_X[i];
            int y = topPos + TANK_Y;
            drawFluid(graphics, menu.getClientFluid(i), menu.getTankCapacity(), x, y);
            graphics.blit(RenderPipelines.GUI_TEXTURED, TEXTURE, x, y, OVERLAY_U, OVERLAY_V, TANK_WIDTH, TANK_HEIGHT, 256, 256);
        }
    }

    /** See this class's javadoc for the tiling and the scissor arithmetic. */
    private static void drawFluid(GuiGraphicsExtractor graphics, FluidStack fluid, int capacity, int x, int y) {
        if (fluid.isEmpty() || capacity <= 0) {
            return;
        }
        float fraction = Mth.clamp(fluid.getAmount() / (float) capacity, 0f, 1f);
        int fillHeight = Math.round(TANK_HEIGHT * fraction);
        if (fillHeight <= 0) {
            return;
        }
        FluidState fluidState = fluid.getFluid().defaultFluidState();
        FluidModel model = Minecraft.getInstance().getModelManager().getFluidStateModelSet().get(fluidState);
        TextureAtlasSprite sprite = model.stillMaterial().sprite();
        // Nullable: vanilla's own lava model has no tint source at all (FluidStateModelSet.LAVA_MODEL).
        BlockTintSource tintSource = model.tintSource();
        int tint = tintSource == null ? 0xFFFFFFFF : ARGB.opaque(tintSource.color(fluidState.createLegacyBlock()));

        graphics.enableScissor(x, y + TANK_HEIGHT - fillHeight, x + TANK_WIDTH, y + TANK_HEIGHT);
        for (int tileY = y + TANK_HEIGHT - 16; tileY > y - 16; tileY -= 16) {
            graphics.blitSprite(RenderPipelines.GUI_TEXTURED, sprite, x, tileY, TANK_WIDTH, 16, tint);
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
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        int tank = getHoveredTank(event.x(), event.y());
        if (tank >= 0 && !menu.getCarried().isEmpty() && minecraft != null && minecraft.gameMode != null) {
            minecraft.gameMode.handleInventoryButtonClick(menu.containerId, tank);
            return true;
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    protected void extractTooltip(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        super.extractTooltip(graphics, mouseX, mouseY);
        int tank = getHoveredTank(mouseX, mouseY);
        if (tank >= 0 && menu.getCarried().isEmpty()) {
            FluidStack fluid = menu.getClientFluid(tank);
            List<Component> lines = new ArrayList<>();
            if (!fluid.isEmpty()) {
                lines.add(fluid.getHoverName());
            }
            lines.add(Component.literal(fluid.getAmount() + " / " + menu.getTankCapacity() + " mB").withStyle(ChatFormatting.GRAY));
            graphics.setComponentTooltipForNextFrame(font, lines, mouseX, mouseY);
        }
    }
}
