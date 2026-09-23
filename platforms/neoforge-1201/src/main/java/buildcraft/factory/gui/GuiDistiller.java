/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * Copyright (c) 2026 Joshua Kac -- NeoForge port (BuildCraft 10)
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL
 * was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.factory.gui;

import java.util.List;
import java.util.Optional;

import com.mojang.blaze3d.systems.RenderSystem;

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

import buildcraft.api.recipes.BuildcraftRecipeRegistry;
import buildcraft.api.recipes.IRefineryRecipeManager.IDistillationRecipe;

import buildcraft.lib.fluid.BCFluidType;
import buildcraft.lib.fluid.Tank;

import buildcraft.factory.container.ContainerDistiller;
import buildcraft.factory.tile.TileDistiller;

/**
 * 1.12.2's {@code GuiDistiller}, on the original {@code textures/gui/distiller.png} (copied byte-for-byte). Every
 * rectangle below is 1.12.2's own {@code GuiIcon}/{@code GuiRectangle} constant. Three real fluid bars, drawn
 * exactly where 1.12.2's {@code WidgetFluidTank} elements sat: input {@code (44, 23, 16x38)}, gas output
 * {@code (98, 10, 34x17)}, liquid output {@code (98, 54, 34x17)}, each followed by its glass overlay icon.
 *
 * <p><b>Fluid bars</b> use {@link GuiAutoCraftFluids}' technique for this target (the fluid's still sprite and tint
 * from {@code IClientFluidTypeExtensions}, clipped with {@code enableScissor}), with one change: 1.12.2's
 * {@code GuiUtil.drawFluid} <i>tiled</i> the 16x16 sprite across the bar rather than stretching it, which matters for
 * the 34x17 output bars, so {@link #drawFluid} tiles 16x16 {@code blitSprite}s and lets the scissor clip the last
 * row/column. Fill is bottom-up in every bar (1.12.2's element did the same for the horizontal ones).
 * Hand-traced: input bar, 4000 mB capacity, 2000 mB stored: {@code fillHeight = round(38 * 0.5) = 19}, scissor
 * {@code y in [top+23+19, top+23+38) = [top+42, top+61)} -- the lower 19 of 38 rows; tiles at y offsets 0, 16, 32
 * (the third clipped to 6 rows by the bar's own bottom). Gas bar with 16 mB of 4000: {@code round(17 * 0.004) = 0}
 * -> nothing drawn (an empty-looking bar for under 1/34 full, the same rounding 1.12.2's int maths gave);
 * 3968 mB: {@code round(16.86) = 17} -> the full bar.
 *
 * <p><b>State icons:</b> when the tile is not {@code active}: the "valid input" icon if the input fluid has a
 * recipe, and the gas/liquid "blocked" icons if that output is full or holds a different fluid than the recipe
 * would produce -- 1.12.2's logic, including its harmless {@code gasBlocking = false} slip in the liquid branch
 * (it cannot change anything: the gas icon is already drawn by then). When active: the active background plus the
 * flowing-colour animation, 1.12.2's arithmetic unchanged ({@link #ACTIVE_SPEED} px/s along a 150 px window, ten
 * repeats spaced at 3/4 of the path length). 1.12.2 drew each animation segment with fractional pixel/UV bounds;
 * {@code blit} here takes integer pixel sizes, so each segment's bounds are rounded to whole pixels (with the
 * matching UV offset) -- a sub-pixel difference only.
 *
 * <p><b>Animation colours</b> were 1.12.2's {@code FluidRenderer.getAverageFluidColour} (the mean of the sprite's
 * pixels, computed from the stitched sprite data). This port keeps no CPU-side copy of stitched sprite pixels, so
 * {@link #fluidColour} approximates: for a BuildCraft fluid the midpoint of its {@code tex_light}/{@code tex_dark}
 * gradient pair (the two colours its sprite was generated from), otherwise the fluid's tint (water blue), else
 * white. This is the 1.20.1 copy of the 26.x class; only the drawing calls differ ({@code GuiGraphics#blit} with
 * {@code RenderSystem.setShaderColor} for the tinted animation, {@code IClientFluidTypeExtensions} for sprites).
 */
public class GuiDistiller extends AbstractContainerScreen<ContainerDistiller> {
    private static final ResourceLocation TEXTURE = new ResourceLocation("buildcraft", "textures/gui/distiller.png");
    private static final int SIZE_X = 176, SIZE_Y = 161;

    // {u, v, w, h} texture icons and {x, y, w, h} GUI rectangles -- 1.12.2's constants.
    private static final int[] ICON_TANK_VERTICAL_OVERLAY = { 0, 161, 16, 38 };
    private static final int[] ICON_TANK_HORIZONTAL_OVERLAY = { 17, 161, 34, 17 };
    private static final int[] RECT_TANK_IN = { 44, 23, 16, 38 };
    private static final int[] RECT_TANK_GAS = { 98, 10, 34, 17 };
    private static final int[] RECT_TANK_LIQUID = { 98, 54, 34, 17 };
    private static final int[] ICON_OFF_VALID_INPUT = { 176, 14, 17, 29 };
    private static final int[] RECT_OFF_VALID_INPUT = { 61, 26, 17, 29 };
    private static final int[] ICON_OFF_OUTPUT_GAS = { 192, 0, 20, 13 };
    private static final int[] RECT_OFF_OUTPUT_GAS = { 77, 12, 20, 13 };
    private static final int[] ICON_OFF_OUTPUT_LIQUID = { 192, 44, 20, 13 };
    private static final int[] RECT_OFF_OUTPUT_LIQUID = { 77, 56, 20, 13 };

    private static final int[] ICON_ACTIVE_BACKGROUND = { 176, 57, 36, 57 };
    private static final int[] RECT_ACTIVE_BACKGROUND = { 61, 12, 36, 57 };
    private static final int[] ICON_ACTIVE_ANIM_1 = { 212, 26, 26, 5 };
    private static final int[] RECT_ACTIVE_ANIM_1 = { 61, 38, 26, 5 };
    private static final int[] ICON_ACTIVE_ANIM_2A = { 225, 13, 13, 16 };
    private static final int[] RECT_ACTIVE_ANIM_2A = { 74, 25, 13, 16 };
    private static final int[] ICON_ACTIVE_ANIM_2B = { 225, 28, 13, 16 };
    private static final int[] RECT_ACTIVE_ANIM_2B = { 74, 40, 13, 16 };
    private static final int[] ICON_ACTIVE_ANIM_3A = { 230, 5, 3, 8 };
    private static final int[] RECT_ACTIVE_ANIM_3A = { 79, 17, 3, 8 };
    private static final int[] ICON_ACTIVE_ANIM_3B = { 230, 44, 3, 8 };
    private static final int[] RECT_ACTIVE_ANIM_3B = { 79, 56, 3, 8 };
    private static final int[] ICON_ACTIVE_ANIM_4A = { 230, 5, 18, 3 };
    private static final int[] RECT_ACTIVE_ANIM_4A = { 79, 17, 18, 3 };
    private static final int[] ICON_ACTIVE_ANIM_4B = { 230, 49, 18, 3 };
    private static final int[] RECT_ACTIVE_ANIM_4B = { 79, 61, 18, 3 };

    /** Pixels per second */
    private static final double ACTIVE_SPEED = 300;
    private static final double ACTIVE_PIXEL_WIDTH = 150;

    private long lastActiveTime = -1;
    private double activeStart = -ACTIVE_PIXEL_WIDTH;
    private double activeEnd = 0;

    public GuiDistiller(ContainerDistiller menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        imageWidth = SIZE_X;
        imageHeight = SIZE_Y;
        titleLabelX = 6;
        titleLabelY = 6;
        inventoryLabelX = 8;
        inventoryLabelY = SIZE_Y - 96;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        super.render(graphics, mouseX, mouseY, partialTick);
        renderTooltip(graphics, mouseX, mouseY);
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        TileDistiller tile = menu.tile;
        drawIcon(graphics, new int[] { 0, 0, SIZE_X, SIZE_Y }, new int[] { 0, 0, SIZE_X, SIZE_Y });

        drawTank(graphics, tile.tankIn, RECT_TANK_IN, ICON_TANK_VERTICAL_OVERLAY);
        drawTank(graphics, tile.tankGasOut, RECT_TANK_GAS, ICON_TANK_HORIZONTAL_OVERLAY);
        drawTank(graphics, tile.tankLiquidOut, RECT_TANK_LIQUID, ICON_TANK_HORIZONTAL_OVERLAY);

        FluidStack currentInput = TileDistiller.getFluid(tile.tankIn);
        FluidStack currentGas = TileDistiller.getFluid(tile.tankGasOut);
        FluidStack currentLiquid = TileDistiller.getFluid(tile.tankLiquidOut);

        if (tile.isActive()) {
            drawIcon(graphics, ICON_ACTIVE_BACKGROUND, RECT_ACTIVE_BACKGROUND);

            long now = System.currentTimeMillis();
            if (lastActiveTime != -1) {
                double change = ACTIVE_SPEED * (now - lastActiveTime) / 1000.0;
                activeStart += change;
                activeEnd += change;
            }
            lastActiveTime = now;

            double distance = 0;
            double startStore = activeStart;
            double endStore = activeEnd;

            int inputColour = fluidColour(currentInput);
            int liquidColour = fluidColour(currentLiquid);
            int gasColour = fluidColour(currentGas);

            for (int i = 0; i < 10; i++) {
                distance = drawAnimation(graphics, inputColour, gasColour, liquidColour);
                activeStart -= distance * 3 / 4;
                activeEnd -= distance * 3 / 4;
            }

            activeStart = startStore;
            activeEnd = endStore;

            if (activeStart >= distance * 3) {
                activeStart -= distance * 3 / 4;
                activeEnd -= distance * 3 / 4;
            }
        } else {
            activeStart = -ACTIVE_PIXEL_WIDTH;
            activeEnd = 0;
            lastActiveTime = -1;

            IDistillationRecipe recipe = currentInput.isEmpty() || BuildcraftRecipeRegistry.refineryRecipes == null
                ? null
                : BuildcraftRecipeRegistry.refineryRecipes.getDistillationRegistry().getRecipeForInput(currentInput);
            if (recipe != null) {
                drawIcon(graphics, ICON_OFF_VALID_INPUT, RECT_OFF_VALID_INPUT);
            }

            boolean gasBlocking = !currentGas.isEmpty() && currentGas.getAmount() >= tile.tankGasOut.getCapacity();
            if (!gasBlocking && !currentGas.isEmpty() && recipe != null
                && !recipe.outGas().isFluidEqual(currentGas)) {
                gasBlocking = true;
            }
            if (gasBlocking) {
                drawIcon(graphics, ICON_OFF_OUTPUT_GAS, RECT_OFF_OUTPUT_GAS);
            }

            boolean liquidBlocking = !currentLiquid.isEmpty()
                && currentLiquid.getAmount() >= tile.tankLiquidOut.getCapacity();
            if (!liquidBlocking && !currentLiquid.isEmpty() && recipe != null
                && !recipe.outLiquid().isFluidEqual(currentLiquid)) {
                liquidBlocking = true;
            }
            if (liquidBlocking) {
                drawIcon(graphics, ICON_OFF_OUTPUT_LIQUID, RECT_OFF_OUTPUT_LIQUID);
            }
        }
    }

    @Override
    protected void renderTooltip(GuiGraphics graphics, int mouseX, int mouseY) {
        super.renderTooltip(graphics, mouseX, mouseY);
        TileDistiller tile = menu.tile;
        tankTooltip(graphics, tile.tankIn, RECT_TANK_IN, "buildcraft.help.distiller.tank_in.title", mouseX, mouseY);
        tankTooltip(graphics, tile.tankGasOut, RECT_TANK_GAS, "buildcraft.help.distiller.tank_gas_out.title", mouseX, mouseY);
        tankTooltip(graphics, tile.tankLiquidOut, RECT_TANK_LIQUID, "buildcraft.help.distiller.tank_liquid_out.title", mouseX,
            mouseY);
    }

    private void tankTooltip(GuiGraphics graphics, Tank tank, int[] rect, String titleKey, int mouseX, int mouseY) {
        int x = leftPos + rect[0];
        int y = topPos + rect[1];
        if (mouseX < x || mouseY < y || mouseX >= x + rect[2] || mouseY >= y + rect[3]) {
            return;
        }
        FluidStack fluid = tank.getFluid();
        Component name = fluid.isEmpty() ? Component.translatable("buildcraft.gui.fluid.empty") : fluid.getDisplayName();
        List<Component> lines = List.of(Component.translatable(titleKey), name,
            Component.literal(tank.getFluidAmount() + " / " + tank.getCapacity() + " mB"));
        graphics.renderTooltip(font, lines, Optional.empty(), mouseX, mouseY);
    }

    private void drawIcon(GuiGraphics graphics, int[] icon, int[] rect) {
        graphics.blit(TEXTURE, leftPos + rect[0], topPos + rect[1], icon[0], icon[1], rect[2], rect[3]);
    }

    private void drawTank(GuiGraphics graphics, Tank tank, int[] rect, int[] overlay) {
        drawFluid(graphics, tank, leftPos + rect[0], topPos + rect[1], rect[2], rect[3]);
        drawIcon(graphics, overlay, rect);
    }

    /** One tank's fluid, bottom-up, tiled 16x16 -- see the class javadoc for the hand-traced numbers. */
    static void drawFluid(GuiGraphics graphics, Tank tank, int x, int y, int width, int height) {
        FluidStack fluid = tank.getFluid();
        int amount = fluid.getAmount();
        int capacity = tank.getCapacity();
        float fraction = capacity > 0 ? Mth.clamp(amount / (float) capacity, 0f, 1f) : 0f;
        if (fluid.isEmpty() || fraction <= 0f) {
            return;
        }
        int fillHeight = Math.round(height * fraction);
        if (fillHeight <= 0) {
            return;
        }
        IClientFluidTypeExtensions extensions = IClientFluidTypeExtensions.of(fluid.getFluid());
        TextureAtlasSprite sprite = Minecraft.getInstance().getTextureAtlas(InventoryMenu.BLOCK_ATLAS)
            .apply(extensions.getStillTexture(fluid));
        int argb = extensions.getTintColor(fluid);
        float r = ((argb >> 16) & 0xFF) / 255f;
        float g = ((argb >> 8) & 0xFF) / 255f;
        float b = (argb & 0xFF) / 255f;

        graphics.enableScissor(x, y + height - fillHeight, x + width, y + height);
        for (int ty = 0; ty < height; ty += 16) {
            for (int tx = 0; tx < width; tx += 16) {
                graphics.blit(x + tx, y + ty, 0, 16, 16, sprite, r, g, b, 1f);
            }
        }
        graphics.disableScissor();
    }

    private static int fluidColour(FluidStack fluid) {
        if (fluid.isEmpty()) {
            return -1;
        }
        if (fluid.getFluid().getFluidType() instanceof BCFluidType bc) {
            int a = bc.getLightColour();
            int b = bc.getDarkColour();
            int r = (((a >> 16) & 0xFF) + ((b >> 16) & 0xFF)) / 2;
            int g = (((a >> 8) & 0xFF) + ((b >> 8) & 0xFF)) / 2;
            int bl = ((a & 0xFF) + (b & 0xFF)) / 2;
            return 0xFF00_0000 | (r << 16) | (g << 8) | bl;
        }
        return 0xFF00_0000 | IClientFluidTypeExtensions.of(fluid.getFluid()).getTintColor(fluid);
    }

    private double drawAnimation(GuiGraphics graphics, int colourInput, int colourGas, int colourLiquid) {
        double distance = 0;

        distance += RECT_ACTIVE_ANIM_1[2] * RECT_ACTIVE_ANIM_1[3];
        drawAnimationBottomToTop(graphics, colourGas, offset(ICON_ACTIVE_ANIM_2A, 0, 114), RECT_ACTIVE_ANIM_2A, distance);
        drawAnimationTopToBottom(graphics, colourLiquid, offset(ICON_ACTIVE_ANIM_2B, 0, 114), RECT_ACTIVE_ANIM_2B, distance);
        distance += RECT_ACTIVE_ANIM_2A[3] * RECT_ACTIVE_ANIM_2A[2];
        drawAnimationBottomToTop(graphics, colourGas, offset(ICON_ACTIVE_ANIM_3A, 0, 114), RECT_ACTIVE_ANIM_3A, distance);
        drawAnimationTopToBottom(graphics, colourLiquid, offset(ICON_ACTIVE_ANIM_3B, 0, 114), RECT_ACTIVE_ANIM_3B, distance);
        distance += RECT_ACTIVE_ANIM_3A[3] * RECT_ACTIVE_ANIM_3A[2];
        drawAnimationLeftToRight(graphics, colourGas, offset(ICON_ACTIVE_ANIM_4A, 0, 114), RECT_ACTIVE_ANIM_4A, distance);
        drawAnimationLeftToRight(graphics, colourLiquid, offset(ICON_ACTIVE_ANIM_4B, 0, 114), RECT_ACTIVE_ANIM_4B, distance);
        distance += RECT_ACTIVE_ANIM_4A[2] * RECT_ACTIVE_ANIM_4A[3];

        double d2 = 0;
        drawAnimationLeftToRight(graphics, colourInput, offset(ICON_ACTIVE_ANIM_1, -36, 114), RECT_ACTIVE_ANIM_1, d2);
        d2 += RECT_ACTIVE_ANIM_1[2] * RECT_ACTIVE_ANIM_1[3];
        drawAnimationBottomToTop(graphics, colourInput, offset(ICON_ACTIVE_ANIM_2A, -36, 114), RECT_ACTIVE_ANIM_2A, d2);
        drawAnimationTopToBottom(graphics, colourInput, offset(ICON_ACTIVE_ANIM_2B, -36, 114), RECT_ACTIVE_ANIM_2B, d2);

        return distance;
    }

    private static int[] offset(int[] icon, int du, int dv) {
        return new int[] { icon[0] + du, icon[1] + dv, icon[2], icon[3] };
    }

    private void drawAnimationLeftToRight(GuiGraphics g, int colour, int[] icon, int[] rect, double startPoint) {
        double start = activeStart - startPoint;
        double end = activeEnd - startPoint;
        double size = rect[2] * rect[3];
        if (end < 0 || start >= size) {
            return;
        }
        drawAnimationPart(g, colour, icon, rect, Math.max(0, start / size), 0, Math.min(1, end / size), 1);
    }

    private void drawAnimationBottomToTop(GuiGraphics g, int colour, int[] icon, int[] rect, double startPoint) {
        double start = activeStart - startPoint;
        double end = activeEnd - startPoint;
        double size = rect[2] * rect[3];
        if (end < 0 || start >= size) {
            return;
        }
        drawAnimationPart(g, colour, icon, rect, 0, 1 - Mth.clamp(end / size, 0.0, 1.0), 1,
            1 - Mth.clamp(start / size, 0.0, 1.0));
    }

    private void drawAnimationTopToBottom(GuiGraphics g, int colour, int[] icon, int[] rect, double startPoint) {
        double start = activeStart - startPoint;
        double end = activeEnd - startPoint;
        double size = rect[2] * rect[3];
        if (end < 0 || start >= size) {
            return;
        }
        drawAnimationPart(g, colour, icon, rect, 0, Math.max(0, start / size), 1, Math.min(1, end / size));
    }

    /** 1.12.2's {@code drawAnimationPart}: the {@code [u0, u1] x [v0, v1]} fraction of {@code icon}, drawn over the
     * same fraction of {@code rect}, tinted. Rounded to whole pixels -- see the class javadoc. */
    private void drawAnimationPart(GuiGraphics g, int colour, int[] icon, int[] rect, double u0, double v0,
        double u1, double v1) {
        int px0 = (int) Math.round(u0 * rect[2]);
        int px1 = (int) Math.round(u1 * rect[2]);
        int py0 = (int) Math.round(v0 * rect[3]);
        int py1 = (int) Math.round(v1 * rect[3]);
        if (px1 <= px0 || py1 <= py0) {
            return;
        }
        RenderSystem.enableBlend();
        RenderSystem.setShaderColor(((colour >> 16) & 0xFF) / 255f, ((colour >> 8) & 0xFF) / 255f, (colour & 0xFF) / 255f,
            ((colour >>> 24) & 0xFF) / 255f);
        g.blit(TEXTURE, leftPos + rect[0] + px0, topPos + rect[1] + py0, icon[0] + px0, icon[1] + py0, px1 - px0, py1 - py0);
        RenderSystem.setShaderColor(1, 1, 1, 1);
    }
}
