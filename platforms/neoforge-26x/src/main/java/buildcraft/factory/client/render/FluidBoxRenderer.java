/*
 * Copyright (c) 2026 Joshua Kac -- NeoForge port (BuildCraft 10)
 *
 * This file is part of the BuildCraft 10 port and is distributed under the terms of the MIT License.
 * Please check the contents of the license, which should be located as "LICENSE.PORT" in the BuildCraft
 * source code distribution.
 */
package buildcraft.factory.client.render;

import java.util.List;

import org.jetbrains.annotations.Nullable;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.Minecraft;
import net.minecraft.client.color.block.BlockTintSource;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.block.FluidModel;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.level.material.FluidState;

import net.neoforged.neoforge.transfer.fluid.FluidResource;

import buildcraft.lib.fluid.Tank;

/**
 * The shared fluid-box drawing behind {@link RenderDistiller} and {@link RenderHeatExchange}: the 26.x stand-in for
 * the parts of 1.12.2's {@code FluidRenderer.renderFluid} those two renderers used, built on exactly the sprite/tint
 * lookup ({@code FluidStateModelSet}, nullable {@code tintSource()}), non-culling {@code entityTranslucent} render
 * type and quad layout {@code buildcraft.factory.tile.RenderTileTank} already verified for this target -- see that
 * class's javadoc. Boxes are captured during state extraction ({@link #tankBox}/{@link #box}) and drawn in
 * {@code submit} ({@link #submit}), so {@code submit} never touches a block entity.
 *
 * <p>{@link #tankBox} reproduces {@code FluidRenderer.renderFluid}'s fill rule: the fluid occupies
 * {@code amount / capacity} of the box's height from the bottom, or -- for a gaseous fluid (1.12.2's
 * {@code isGaseous}, negative density; here {@code FluidType#getDensity() < 0}, which the searing/boiled
 * BuildCraft fluids have) -- from the top down.
 */
public final class FluidBoxRenderer {

    private FluidBoxRenderer() {}

    /** One captured fluid box, in block-local coordinates (may extend past the block). */
    public record Box(Identifier atlas, float u0, float u1, float v0, float v1, int tint, float x0, float y0, float z0,
        float x1, float y1, float z1) {}

    @Nullable
    public static Box tankBox(Tank tank, double[] min, double[] max) {
        FluidResource fluid = tank.getFluidType();
        int capacity = tank.getCapacity();
        float fraction = capacity > 0 ? Mth.clamp(tank.getAmountAsInt(0) / (float) capacity, 0f, 1f) : 0f;
        if (fluid.isEmpty() || fraction <= 0f) {
            return null;
        }
        double height = (max[1] - min[1]) * fraction;
        double y0 = min[1];
        double y1 = min[1] + height;
        if (fluid.getFluidType().getDensity() < 0) {
            y0 = max[1] - height;
            y1 = max[1];
        }
        return box(fluid, min[0], y0, min[2], max[0], y1, max[2]);
    }

    @Nullable
    public static Box box(FluidResource fluid, double x0, double y0, double z0, double x1, double y1, double z1) {
        if (fluid.isEmpty()) {
            return null;
        }
        FluidState fluidState = fluid.getFluid().defaultFluidState();
        FluidModel model = Minecraft.getInstance().getModelManager().getFluidStateModelSet().get(fluidState);
        TextureAtlasSprite sprite = model.stillMaterial().sprite();
        // Nullable: vanilla's own lava model has no tint source at all (FluidStateModelSet.LAVA_MODEL).
        BlockTintSource tintSource = model.tintSource();
        int tint = tintSource == null ? 0xFFFFFFFF : tintSource.color(fluidState.createLegacyBlock());
        return new Box(sprite.atlasLocation(), sprite.getU0(), sprite.getU1(), sprite.getV0(), sprite.getV1(), tint,
            (float) x0, (float) y0, (float) z0, (float) x1, (float) y1, (float) z1);
    }

    public static void submit(List<Box> boxes, int light, PoseStack poseStack, SubmitNodeCollector collector) {
        if (boxes.isEmpty()) {
            return;
        }
        // Every fluid still sprite lives on the block atlas, so one custom-geometry submission covers them all.
        collector.submitCustomGeometry(poseStack, RenderTypes.entityTranslucent(boxes.get(0).atlas()), (pose, consumer) -> {
            for (Box box : boxes) {
                renderBox(pose, consumer, box, light);
            }
        });
    }

    private static void renderBox(PoseStack.Pose pose, VertexConsumer c, Box b, int light) {
        float r = ((b.tint >> 16) & 0xFF) / 255f;
        float g = ((b.tint >> 8) & 0xFF) / 255f;
        float bl = (b.tint & 0xFF) / 255f;
        float x0 = b.x0, y0 = b.y0, z0 = b.z0, x1 = b.x1, y1 = b.y1, z1 = b.z1;
        // -Y, +Y, -X, +X, -Z, +Z -- the same vertex order as RenderTileTank.
        face(pose, c, b, x0, y0, z0, x0, y0, z1, x1, y0, z1, x1, y0, z0, 0, -1, 0, r, g, bl, light);
        face(pose, c, b, x0, y1, z1, x0, y1, z0, x1, y1, z0, x1, y1, z1, 0, 1, 0, r, g, bl, light);
        face(pose, c, b, x0, y0, z1, x0, y0, z0, x0, y1, z0, x0, y1, z1, -1, 0, 0, r, g, bl, light);
        face(pose, c, b, x1, y0, z0, x1, y0, z1, x1, y1, z1, x1, y1, z0, 1, 0, 0, r, g, bl, light);
        face(pose, c, b, x0, y0, z0, x1, y0, z0, x1, y1, z0, x0, y1, z0, 0, 0, -1, r, g, bl, light);
        face(pose, c, b, x1, y0, z1, x0, y0, z1, x0, y1, z1, x1, y1, z1, 0, 0, 1, r, g, bl, light);
    }

    private static void face(PoseStack.Pose pose, VertexConsumer c, Box b, float ax, float ay, float az, float bx,
        float by, float bz, float cx, float cy, float cz, float dx, float dy, float dz, float nx, float ny, float nz,
        float r, float g, float bl, int light) {
        vertex(pose, c, ax, ay, az, b.u0, b.v1, nx, ny, nz, r, g, bl, light);
        vertex(pose, c, bx, by, bz, b.u1, b.v1, nx, ny, nz, r, g, bl, light);
        vertex(pose, c, cx, cy, cz, b.u1, b.v0, nx, ny, nz, r, g, bl, light);
        vertex(pose, c, dx, dy, dz, b.u0, b.v0, nx, ny, nz, r, g, bl, light);
    }

    private static void vertex(PoseStack.Pose pose, VertexConsumer c, float x, float y, float z, float u, float v,
        float nx, float ny, float nz, float r, float g, float b, int light) {
        c.addVertex(pose, x, y, z)
            .setColor(r, g, b, 1f)
            .setUv(u, v)
            .setOverlay(OverlayTexture.NO_OVERLAY)
            .setLight(light)
            .setNormal(pose, nx, ny, nz);
    }

    /** 1.12.2's {@code TankSize(sx, sy, sz, ex, ey, ez)} in pixels, shrunk by {@code sx/sy/sz} blocks per side,
     * then rotated {@code turns} times by {@code TankSize#rotateY} ({@code (x, y, z) -> (1 - z, y, x)}). Returns
     * {@code {min, max}}. */
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
