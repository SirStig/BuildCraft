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
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.MissingTextureAtlasSprite;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.data.AtlasIds;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;

/** The public entry point for drawing BuildCraft lasers on 26.x. Two steps, matching this target's own
 * extract/{@code submit} split:
 *
 * <ol>
 * <li><b>Extract</b> (anywhere the real level may still be read -- a {@code BlockEntityRenderer#extractRenderState},
 * or an {@code ExtractLevelRenderStateEvent} handler): turn each {@link LaserData_BC8} into a {@link CompiledLaser}
 * with {@link #compile(LaserData_BC8)}, and keep the resulting list in the render state.</li>
 * <li><b>Submit</b> ({@code BlockEntityRenderer#submit}, or a {@code SubmitCustomGeometryEvent} handler): hand that
 * list to {@link #submit(SubmitNodeCollector, PoseStack, List, Vec3)} along with whatever world position the
 * {@link PoseStack} is currently sitting at -- the block entity's own {@code blockPos} inside a
 * {@code BlockEntityRenderer}, the camera position in the level-wide {@code SubmitCustomGeometryEvent}.</li>
 * </ol>
 *
 * <p>Replaces 1.12.2's {@code renderLaserStatic} (GL display list / VBO) and {@code renderLaserDynamic} (append to a
 * {@code BufferBuilder}): there is only one way to draw now, so there is only one path. What survives is 1.12.2's
 * own compiled-laser cache ({@code COMPILED_STATIC_LASERS}): same key (the {@link LaserData_BC8} value), same
 * 5-second expire-after-write, which also serves the same second purpose it did in 1.12.2 -- baked light is
 * refreshed at most every 5 seconds rather than every frame.
 *
 * <p>Drawn with {@link RenderTypes#entityCutout(Identifier)} over the block atlas: every laser sprite is fully opaque
 * or fully transparent (checked pixel-by-pixel; only {@code marker_path_connected} has transparent pixels at all),
 * which is exactly an alpha-cutout draw -- the same thing 1.12.2's default GL state (alpha test on, blending off)
 * produced. Confirmed against the real 26.3 {@code RenderPipelines#ENTITY_CUTOUT}: {@code withCull(false)} (so both
 * sides of every quad draw, which is what the original {@code doubleFace} option was for) and
 * {@code PER_FACE_LIGHTING} (the directional shading that replaces 1.12.2's baked-in diffuse colour). */
public final class LaserRenderer_BC8 {

    private static final Cache<LaserData_BC8, CompiledLaser> COMPILED_LASERS = CacheBuilder.newBuilder()
        .expireAfterWrite(5, TimeUnit.SECONDS)
        .maximumSize(16384)
        .build();

    private LaserRenderer_BC8() {}

    public static RenderType renderType() {
        return RenderTypes.entityCutout(TextureAtlas.LOCATION_BLOCKS);
    }

    /** Looks up a laser sprite in the block atlas. Returns the atlas' own missing sprite (never null) if it isn't
     * there -- see {@link #isMissing}. */
    public static TextureAtlasSprite getSprite(Identifier sprite) {
        return Minecraft.getInstance().getAtlasManager().getAtlasOrThrow(AtlasIds.BLOCKS).getSprite(sprite);
    }

    public static boolean isMissing(TextureAtlasSprite sprite) {
        return MissingTextureAtlasSprite.getLocation().equals(sprite.contents().name());
    }

    /** Compiles (or fetches the cached compilation of) {@code data} against the current client level. Must be
     * called while the level may be read -- i.e. during extraction, not {@code submit}. */
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

    /** Submits every laser in {@code lasers} as one piece of custom geometry. {@code origin} is the world position
     * the {@code poseStack} is currently translated to (see the class javadoc). The list is captured by the
     * submitted lambda and replayed later, so it must not be mutated afterwards. */
    public static void submit(SubmitNodeCollector collector, PoseStack poseStack, List<CompiledLaser> lasers,
        Vec3 origin) {
        if (lasers.isEmpty()) {
            return;
        }
        final double ox = origin.x, oy = origin.y, oz = origin.z;
        collector.submitCustomGeometry(poseStack, renderType(), (pose, consumer) -> {
            for (CompiledLaser laser : lasers) {
                emit(pose, consumer, laser, ox, oy, oz);
            }
        });
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
            consumer.addVertex(pose, bx + d[o], by + d[o + 1], bz + d[o + 2])
                .setColor(1f, 1f, 1f, 1f)
                .setUv(d[o + 3], d[o + 4])
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(light[i])
                .setNormal(pose, d[o + 5], d[o + 6], d[o + 7]);
        }
    }
}
