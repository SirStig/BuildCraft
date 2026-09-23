/*
 * Copyright (c) 2026 Joshua Kac -- NeoForge port (BuildCraft 10)
 *
 * This file is part of the BuildCraft 10 port and is distributed under the terms of the MIT License.
 * Please check the contents of the license, which should be located as "LICENSE.PORT" in the BuildCraft
 * source code distribution.
 */
package buildcraft.factory.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.util.Mth;
import net.minecraft.world.inventory.InventoryMenu;

import net.minecraftforge.client.extensions.common.IClientFluidTypeExtensions;
import net.minecraftforge.fluids.FluidStack;

import buildcraft.lib.fluid.Tank;

/**
 * The 1.20.1 copy of the 26.x {@code FluidBoxRenderer} (see that class's javadoc): the same boxes, fill rule
 * (bottom-up, or top-down for a negative-density gas) and non-culling translucent entity render type as
 * {@code buildcraft.factory.tile.RenderTileTank}'s 1.20.1 copy, with the sprite and tint from
 * {@code IClientFluidTypeExtensions} and drawn immediately (1.20.1 has no state-extraction split).
 */
public final class FluidBoxRenderer {

    private FluidBoxRenderer() {}

    public static void tankBox(Tank tank, double[] min, double[] max, PoseStack poseStack, MultiBufferSource buffer,
        int light) {
        FluidStack fluid = tank.getFluid();
        int capacity = tank.getCapacity();
        float fraction = capacity > 0 ? Mth.clamp(fluid.getAmount() / (float) capacity, 0f, 1f) : 0f;
        if (fluid.isEmpty() || fraction <= 0f) {
            return;
        }
        double height = (max[1] - min[1]) * fraction;
        double y0 = min[1];
        double y1 = min[1] + height;
        if (fluid.getFluid().getFluidType().getDensity() < 0) {
            y0 = max[1] - height;
            y1 = max[1];
        }
        box(fluid, min[0], y0, min[2], max[0], y1, max[2], poseStack, buffer, light);
    }

    public static void box(FluidStack fluid, double x0d, double y0d, double z0d, double x1d, double y1d, double z1d,
        PoseStack poseStack, MultiBufferSource buffer, int light) {
        if (fluid.isEmpty()) {
            return;
        }
        IClientFluidTypeExtensions extensions = IClientFluidTypeExtensions.of(fluid.getFluid());
        TextureAtlasSprite sprite = Minecraft.getInstance().getTextureAtlas(InventoryMenu.BLOCK_ATLAS)
            .apply(extensions.getStillTexture(fluid));
        int argb = extensions.getTintColor(fluid);
        float r = ((argb >> 16) & 0xFF) / 255f;
        float g = ((argb >> 8) & 0xFF) / 255f;
        float bl = (argb & 0xFF) / 255f;
        VertexConsumer c = buffer.getBuffer(RenderType.entityTranslucent(InventoryMenu.BLOCK_ATLAS));
        PoseStack.Pose pose = poseStack.last();
        float u0 = sprite.getU0(), u1 = sprite.getU1(), v0 = sprite.getV0(), v1 = sprite.getV1();
        float x0 = (float) x0d, y0 = (float) y0d, z0 = (float) z0d, x1 = (float) x1d, y1 = (float) y1d, z1 = (float) z1d;
        float[] uv = { u0, u1, v0, v1 };
        face(pose, c, uv, x0, y0, z0, x0, y0, z1, x1, y0, z1, x1, y0, z0, 0, -1, 0, r, g, bl, light);
        face(pose, c, uv, x0, y1, z1, x0, y1, z0, x1, y1, z0, x1, y1, z1, 0, 1, 0, r, g, bl, light);
        face(pose, c, uv, x0, y0, z1, x0, y0, z0, x0, y1, z0, x0, y1, z1, -1, 0, 0, r, g, bl, light);
        face(pose, c, uv, x1, y0, z0, x1, y0, z1, x1, y1, z1, x1, y1, z0, 1, 0, 0, r, g, bl, light);
        face(pose, c, uv, x0, y0, z0, x1, y0, z0, x1, y1, z0, x0, y1, z0, 0, 0, -1, r, g, bl, light);
        face(pose, c, uv, x1, y0, z1, x0, y0, z1, x0, y1, z1, x1, y1, z1, 0, 0, 1, r, g, bl, light);
    }

    private static void face(PoseStack.Pose pose, VertexConsumer c, float[] uv, float ax, float ay, float az, float bx,
        float by, float bz, float cx, float cy, float cz, float dx, float dy, float dz, float nx, float ny, float nz,
        float r, float g, float bl, int light) {
        vertex(pose, c, ax, ay, az, uv[0], uv[3], nx, ny, nz, r, g, bl, light);
        vertex(pose, c, bx, by, bz, uv[1], uv[3], nx, ny, nz, r, g, bl, light);
        vertex(pose, c, cx, cy, cz, uv[1], uv[2], nx, ny, nz, r, g, bl, light);
        vertex(pose, c, dx, dy, dz, uv[0], uv[2], nx, ny, nz, r, g, bl, light);
    }

    private static void vertex(PoseStack.Pose pose, VertexConsumer c, float x, float y, float z, float u, float v,
        float nx, float ny, float nz, float r, float g, float b, int light) {
        c.vertex(pose.pose(), x, y, z)
            .color(r, g, b, 1f)
            .uv(u, v)
            .overlayCoords(OverlayTexture.NO_OVERLAY)
            .uv2(light)
            .normal(pose.normal(), nx, ny, nz)
            .endVertex();
    }

    /** See the 26.x copy: 1.12.2's {@code TankSize}, shrunk, then rotated {@code turns} times by
     * {@code (x, y, z) -> (1 - z, y, x)}. */
    public static double[][] tankSize(int sx, int sy, int sz, int ex, int ey, int ez, double shX, double shY, double shZ,
        int turns) {
        double[] min = { sx / 16.0 + shX, sy / 16.0 + shY, sz / 16.0 + shZ };
        double[] max = { ex / 16.0 - shX, ey / 16.0 - shY, ez / 16.0 - shZ };
        for (int i = 0; i < turns; i++) {
            double[] a = { 1 - min[2], min[1], min[0] };
            double[] b = { 1 - max[2], max[1], max[0] };
            min = new double[] { Math.min(a[0], b[0]), Math.min(a[1], b[1]), Math.min(a[2], b[2]) };
            max = new double[] { Math.max(a[0], b[0]), Math.max(a[1], b[1]), Math.max(a[2], b[2]) };
        }
        return new double[][] { min, max };
    }
}
