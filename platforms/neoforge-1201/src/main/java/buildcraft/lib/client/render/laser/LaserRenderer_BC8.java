/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * Copyright (c) 2026 Joshua Kac -- NeoForge port (BuildCraft 10)
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL
 * was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.lib.client.render.laser;

import java.util.List;
import java.util.concurrent.TimeUnit;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.MissingTextureAtlasSprite;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.phys.Vec3;

/** The public entry point for drawing BuildCraft lasers on 1.20.1: {@link #compile(LaserData_BC8)} each
 * {@link LaserData_BC8} into a {@link CompiledLaser}, then {@link #render(PoseStack, MultiBufferSource, List, Vec3)}
 * them, passing the world position the {@link PoseStack} is currently translated to -- the block entity's own
 * position inside a classic {@code BlockEntityRenderer#render} (whose pose stack Forge's dispatcher has already
 * translated there), the camera position inside a {@code RenderLevelStageEvent} handler.
 *
 * <p>This target keeps the classic immediate-mode renderer contract, so compile and render happen in the same call
 * here; the separate compile step still exists so the API has the same shape as the 26.x copy (where the level may
 * only be read during extraction) and so 1.12.2's compiled-laser cache ({@code COMPILED_STATIC_LASERS}: same key,
 * same 5-second expire-after-write, which also refreshes the baked light at most every 5 seconds) can sit between
 * the two. See the 26.x copy's javadoc for the rest of the design.
 *
 * <p>Drawn with {@link RenderType#entityCutoutNoCull(ResourceLocation)} over the block atlas -- this target's name for
 * the same non-culling alpha-cutout entity type 26.x calls {@code entityCutout} (confirmed via {@code javap} on
 * both). Every laser sprite is fully opaque or fully transparent per pixel, so cutout reproduces 1.12.2's alpha-test
 * draw exactly; no-cull covers the original's {@code doubleFace} option; the entity shader's normal-based lighting
 * stands in for the original's baked diffuse colour. */
public final class LaserRenderer_BC8 {

    private static final Cache<LaserData_BC8, CompiledLaser> COMPILED_LASERS = CacheBuilder.newBuilder()
        .expireAfterWrite(5, TimeUnit.SECONDS)
        .maximumSize(16384)
        .build();

    private LaserRenderer_BC8() {}

    public static RenderType renderType() {
        return RenderType.entityCutoutNoCull(InventoryMenu.BLOCK_ATLAS);
    }

    /** Looks up a laser sprite in the block atlas. Returns the atlas' own missing sprite (never null) if it isn't
     * there -- see {@link #isMissing}. */
    public static TextureAtlasSprite getSprite(ResourceLocation sprite) {
        return Minecraft.getInstance().getTextureAtlas(InventoryMenu.BLOCK_ATLAS).apply(sprite);
    }

    public static boolean isMissing(TextureAtlasSprite sprite) {
        return MissingTextureAtlasSprite.getLocation().equals(sprite.contents().name());
    }

    /** Compiles (or fetches the cached compilation of) {@code data} against the current client level. */
    public static CompiledLaser compile(LaserData_BC8 data) {
        CompiledLaser compiled = COMPILED_LASERS.getIfPresent(data);
        if (compiled == null) {
            compiled = CompiledLaser.compile(data, Minecraft.getInstance().level, LaserRenderer_BC8::getSprite);
            COMPILED_LASERS.put(data, compiled);
        }
        return compiled;
    }

    /** Drops every cached compilation -- e.g. after the block atlas is re-stitched, so no stale UVs survive the
     * 5-second expiry window. */
    public static void clearCache() {
        COMPILED_LASERS.invalidateAll();
    }

    /** Draws every laser in {@code lasers}. {@code origin} is the world position the {@code poseStack} is currently
     * translated to (see the class javadoc). */
    public static void render(PoseStack poseStack, MultiBufferSource buffers, List<CompiledLaser> lasers, Vec3 origin) {
        if (lasers.isEmpty()) {
            return;
        }
        PoseStack.Pose pose = poseStack.last();
        VertexConsumer consumer = buffers.getBuffer(renderType());
        for (CompiledLaser laser : lasers) {
            emit(pose, consumer, laser, origin.x, origin.y, origin.z);
        }
    }

    /** Writes one compiled laser's quads into {@code consumer} (which must be a quad-mode, {@code NEW_ENTITY}-format
     * consumer, as every entity render type is), relative to {@code (ox, oy, oz)}. */
    public static void emit(PoseStack.Pose pose, VertexConsumer consumer, CompiledLaser laser, double ox, double oy,
        double oz) {
        float bx = (float) (laser.originX - ox);
        float by = (float) (laser.originY - oy);
        float bz = (float) (laser.originZ - oz);
        float[] d = laser.vertexData;
        int[] light = laser.lightData;
        for (int i = 0; i < laser.vertexCount; i++) {
            int o = i * CompiledLaser.STRIDE;
            consumer.vertex(pose.pose(), bx + d[o], by + d[o + 1], bz + d[o + 2])
                .color(1f, 1f, 1f, 1f)
                .uv(d[o + 3], d[o + 4])
                .overlayCoords(OverlayTexture.NO_OVERLAY)
                .uv2(light[i])
                .normal(pose.normal(), d[o + 5], d[o + 6], d[o + 7])
                .endVertex();
        }
    }
}
