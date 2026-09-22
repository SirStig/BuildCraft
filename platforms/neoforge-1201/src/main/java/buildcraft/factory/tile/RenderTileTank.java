/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * Copyright (c) 2026 Joshua Kac -- NeoForge port (BuildCraft 10)
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL
 * was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.factory.tile;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.inventory.InventoryMenu;

import net.minecraftforge.client.extensions.common.IClientFluidTypeExtensions;
import net.minecraftforge.fluids.FluidStack;

/**
 * Mirrors the 26.x class of the same name -- see that one's javadoc for the full account of why {@link TileTank}
 * needs a fluid-level renderer at all (closing the exact "no renderer to consume it" deferral both {@code TileTank}
 * and {@link buildcraft.lib.fluid.Tank}'s own class javadocs already document), why no client-side interpolation
 * field is needed the way {@code RenderTileEngine}'s piston rod needed one, the real geometry from 1.12.2's own
 * {@code RenderTank}, and why the connected-tank seamless-stretching behaviour is deliberately cut from this first
 * pass.
 *
 * <p>Classic {@code render(...)} contract, not the 26.x state-extraction/{@code submit} split -- matching the same
 * genuine platform divergence {@code RenderTileEngine}'s own two copies already establish (this target's
 * {@code BlockEntityRenderer<T>} never grew that split).
 *
 * <p><b>Real fluid sprite/tint API -- genuinely different from 26.x, confirmed via {@code javap} against the real
 * Forge 1.20.1 universal jar.</b> Unlike 26.x (where {@code IClientFluidTypeExtensions} lost its texture/tint
 * methods entirely once vanilla grew its own data-driven fluid renderer -- see the 26.x copy's javadoc),
 * 1.20.1's {@code net.minecraftforge.client.extensions.common.IClientFluidTypeExtensions} still has the classic,
 * well-established shape: {@code IClientFluidTypeExtensions.of(Fluid)} returns an instance whose
 * {@code getStillTexture(FluidStack)}/{@code getTintColor(FluidStack)} give the sprite location and tint directly.
 * The location still has to be resolved to an actual {@link TextureAtlasSprite} through the block atlas
 * ({@link Minecraft#getTextureAtlas(ResourceLocation)} with {@link InventoryMenu#BLOCK_ATLAS}, the same atlas
 * lookup idiom every Forge-family fluid-rendering mod uses) -- there is no vanilla {@code FluidStateModelSet}
 * equivalent on this target to hand back an already-baked sprite the way 26.x's does.
 */
public class RenderTileTank implements BlockEntityRenderer<TileTank> {

    private static final float X_MIN = 0.13f;
    private static final float X_MAX = 0.86f;
    private static final float Y_MIN = 0.01f;
    private static final float Y_MAX = 0.99f;
    private static final float Z_MIN = 0.13f;
    private static final float Z_MAX = 0.86f;

    public RenderTileTank(BlockEntityRendererProvider.Context context) {}

    @Override
    public void render(
        TileTank tile, float partialTick, PoseStack poseStack, MultiBufferSource buffer, int packedLight,
        int packedOverlay
    ) {
        FluidStack fluidStack = tile.tank.getFluid();
        int amount = tile.tank.getFluidAmount();
        int capacity = tile.tank.getCapacity();
        float fraction = capacity > 0 ? Mth.clamp(amount / (float) capacity, 0f, 1f) : 0f;
        if (fluidStack.isEmpty() || fraction <= 0f) {
            return;
        }

        IClientFluidTypeExtensions extensions = IClientFluidTypeExtensions.of(fluidStack.getFluid());
        ResourceLocation textureLocation = extensions.getStillTexture(fluidStack);
        TextureAtlasSprite sprite = Minecraft.getInstance().getTextureAtlas(InventoryMenu.BLOCK_ATLAS).apply(textureLocation);
        int argb = extensions.getTintColor(fluidStack);
        float r = ((argb >> 16) & 0xFF) / 255f;
        float g = ((argb >> 8) & 0xFF) / 255f;
        float b = (argb & 0xFF) / 255f;
        float topY = Y_MIN + (Y_MAX - Y_MIN) * fraction;

        // Non-culling, matching the 26.x copy's own reasoning: a hand-built quad's winding order cannot be
        // checked on screen in this project, so a non-culling RenderType guarantees every face draws regardless.
        RenderType renderType = RenderType.entityTranslucent(InventoryMenu.BLOCK_ATLAS);
        VertexConsumer consumer = buffer.getBuffer(renderType);
        renderFluidBox(poseStack.last(), consumer, sprite, r, g, b, topY, packedLight, packedOverlay);
    }

    private static void renderFluidBox(
        PoseStack.Pose pose, VertexConsumer consumer, TextureAtlasSprite sprite, float r, float g, float b,
        float topY, int light, int overlay
    ) {
        float u0 = sprite.getU0();
        float u1 = sprite.getU1();
        float v0 = sprite.getV0();
        float v1 = sprite.getV1();
        float minX = X_MIN;
        float maxX = X_MAX;
        float minY = Y_MIN;
        float maxY = topY;
        float minZ = Z_MIN;
        float maxZ = Z_MAX;

        // -Y (bottom)
        face(
            pose, consumer, minX, minY, minZ, minX, minY, maxZ, maxX, minY, maxZ, maxX, minY, minZ, 0, -1, 0, u0, v0,
            u1, v1, r, g, b, light, overlay
        );
        // +Y (top / fluid surface)
        face(
            pose, consumer, minX, maxY, maxZ, minX, maxY, minZ, maxX, maxY, minZ, maxX, maxY, maxZ, 0, 1, 0, u0, v0,
            u1, v1, r, g, b, light, overlay
        );
        // -X (west)
        face(
            pose, consumer, minX, minY, maxZ, minX, minY, minZ, minX, maxY, minZ, minX, maxY, maxZ, -1, 0, 0, u0, v0,
            u1, v1, r, g, b, light, overlay
        );
        // +X (east)
        face(
            pose, consumer, maxX, minY, minZ, maxX, minY, maxZ, maxX, maxY, maxZ, maxX, maxY, minZ, 1, 0, 0, u0, v0,
            u1, v1, r, g, b, light, overlay
        );
        // -Z (north)
        face(
            pose, consumer, minX, minY, minZ, maxX, minY, minZ, maxX, maxY, minZ, minX, maxY, minZ, 0, 0, -1, u0, v0,
            u1, v1, r, g, b, light, overlay
        );
        // +Z (south)
        face(
            pose, consumer, maxX, minY, maxZ, minX, minY, maxZ, minX, maxY, maxZ, maxX, maxY, maxZ, 0, 0, 1, u0, v0,
            u1, v1, r, g, b, light, overlay
        );
    }

    private static void face(
        PoseStack.Pose pose, VertexConsumer consumer, float x0, float y0, float z0, float x1, float y1, float z1,
        float x2, float y2, float z2, float x3, float y3, float z3, float nx, float ny, float nz, float u0, float v0,
        float u1, float v1, float r, float g, float b, int light, int overlay
    ) {
        vertex(pose, consumer, x0, y0, z0, u0, v1, nx, ny, nz, r, g, b, light, overlay);
        vertex(pose, consumer, x1, y1, z1, u1, v1, nx, ny, nz, r, g, b, light, overlay);
        vertex(pose, consumer, x2, y2, z2, u1, v0, nx, ny, nz, r, g, b, light, overlay);
        vertex(pose, consumer, x3, y3, z3, u0, v0, nx, ny, nz, r, g, b, light, overlay);
    }

    private static void vertex(
        PoseStack.Pose pose, VertexConsumer consumer, float x, float y, float z, float u, float v, float nx,
        float ny, float nz, float r, float g, float b, int light, int overlay
    ) {
        consumer.vertex(pose.pose(), x, y, z)
            .color(r, g, b, 1f)
            .uv(u, v)
            .overlayCoords(overlay)
            .uv2(light)
            .normal(pose.normal(), nx, ny, nz)
            .endVertex();
    }
}
