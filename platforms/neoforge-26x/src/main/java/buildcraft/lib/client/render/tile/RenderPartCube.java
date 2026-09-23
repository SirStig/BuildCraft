/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * Copyright (c) 2026 Joshua Kac -- NeoForge port (BuildCraft 10)
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL
 * was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.lib.client.render.tile;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;

/** Draws a single small tinted cube -- 1.12.2's own {@code RenderPartCube}, "a variable sized element (like LED)
 * that can render somewhere in a TESR", reused so far by {@code buildcraft.factory.client.render.RenderMiningWell}/
 * {@code RenderPump} for their power/status indicator lights.
 *
 * <p><b>A stateless static method, not a reusable mutable instance like 1.12.2's own class.</b> The original held
 * a mutable {@code MutableVertex center} that a caller repositioned/recoloured/relit every frame before calling
 * {@code render(BufferBuilder)}; that shape existed to amortise a few field writes against 1.12.2's own
 * {@code BufferBuilder} draw call. Neither of this port's two {@code BlockEntityRenderer} contracts wants a
 * long-lived mutable render object at all -- 26.x computes everything fresh into an immutable render state every
 * frame ({@code extractRenderState}), and 1.20.1's classic {@code render(...)} already hands over every needed
 * parameter directly -- so a plain static method with real parameters is the natural fit, matching
 * {@code RenderTileTank}'s own hand-built {@code face}/{@code vertex} box helpers rather than reintroducing
 * {@code MutableVertex}.
 *
 * <p><b>The white texture is different from 1.12.2's.</b> The original tinted {@code ModelLoader.White.INSTANCE},
 * a runtime-generated pure-white 1x1 texture with no packaged asset behind it at all -- there is nothing to copy
 * byte-for-byte, and this target has no equivalent built-in white sprite. {@code minecraft:block/white_concrete}
 * (a real, already-atlas-stitched vanilla block texture, near-flat and light enough to read as "white" once
 * tinted) stands in for it instead, chosen specifically so no new PNG asset or atlas-source entry is needed
 * purely to draw a coloured light. Passing a fully-saturated colour multiplies out concrete's faint texture noise
 * to something visually indistinguishable from a flat tint at this cube's tiny (1/16 block) on-screen size. */
public final class RenderPartCube {

    private RenderPartCube() {}

    /** Draws one cube of the given half-extent centred on ({@code cx}, {@code cy}, {@code cz}) (block-local
     * coordinates, i.e. whatever frame {@code pose} is already in), every face using the whole of {@code sprite}'s
     * UV rectangle tinted by {@code argb} (alpha included) and lit by the already-packed {@code light}. */
    public static void render(PoseStack.Pose pose, VertexConsumer consumer, TextureAtlasSprite sprite, double cx,
        double cy, double cz, double half, int argb, int light) {
        float r = ((argb >> 16) & 0xFF) / 255f;
        float g = ((argb >> 8) & 0xFF) / 255f;
        float b = (argb & 0xFF) / 255f;
        float a = ((argb >>> 24) & 0xFF) / 255f;
        float u0 = sprite.getU0();
        float u1 = sprite.getU1();
        float v0 = sprite.getV0();
        float v1 = sprite.getV1();
        float minX = (float) (cx - half);
        float maxX = (float) (cx + half);
        float minY = (float) (cy - half);
        float maxY = (float) (cy + half);
        float minZ = (float) (cz - half);
        float maxZ = (float) (cz + half);
        int overlay = OverlayTexture.NO_OVERLAY;

        // -Y (bottom)
        face(pose, consumer, minX, minY, minZ, minX, minY, maxZ, maxX, minY, maxZ, maxX, minY, minZ, 0, -1, 0, u0,
            v0, u1, v1, r, g, b, a, light, overlay);
        // +Y (top)
        face(pose, consumer, minX, maxY, maxZ, minX, maxY, minZ, maxX, maxY, minZ, maxX, maxY, maxZ, 0, 1, 0, u0,
            v0, u1, v1, r, g, b, a, light, overlay);
        // -X (west)
        face(pose, consumer, minX, minY, maxZ, minX, minY, minZ, minX, maxY, minZ, minX, maxY, maxZ, -1, 0, 0, u0,
            v0, u1, v1, r, g, b, a, light, overlay);
        // +X (east)
        face(pose, consumer, maxX, minY, minZ, maxX, minY, maxZ, maxX, maxY, maxZ, maxX, maxY, minZ, 1, 0, 0, u0,
            v0, u1, v1, r, g, b, a, light, overlay);
        // -Z (north)
        face(pose, consumer, minX, minY, minZ, maxX, minY, minZ, maxX, maxY, minZ, minX, maxY, minZ, 0, 0, -1, u0,
            v0, u1, v1, r, g, b, a, light, overlay);
        // +Z (south)
        face(pose, consumer, maxX, minY, maxZ, minX, minY, maxZ, minX, maxY, maxZ, maxX, maxY, maxZ, 0, 0, 1, u0,
            v0, u1, v1, r, g, b, a, light, overlay);
    }

    private static void face(PoseStack.Pose pose, VertexConsumer consumer, float x0, float y0, float z0, float x1,
        float y1, float z1, float x2, float y2, float z2, float x3, float y3, float z3, float nx, float ny,
        float nz, float u0, float v0, float u1, float v1, float r, float g, float b, float a, int light,
        int overlay) {
        vertex(pose, consumer, x0, y0, z0, u0, v1, nx, ny, nz, r, g, b, a, light, overlay);
        vertex(pose, consumer, x1, y1, z1, u1, v1, nx, ny, nz, r, g, b, a, light, overlay);
        vertex(pose, consumer, x2, y2, z2, u1, v0, nx, ny, nz, r, g, b, a, light, overlay);
        vertex(pose, consumer, x3, y3, z3, u0, v0, nx, ny, nz, r, g, b, a, light, overlay);
    }

    private static void vertex(PoseStack.Pose pose, VertexConsumer consumer, float x, float y, float z, float u,
        float v, float nx, float ny, float nz, float r, float g, float b, float a, int light, int overlay) {
        consumer.addVertex(pose, x, y, z)
            .setColor(r, g, b, a)
            .setUv(u, v)
            .setOverlay(overlay)
            .setLight(light)
            .setNormal(pose, nx, ny, nz);
    }
}
