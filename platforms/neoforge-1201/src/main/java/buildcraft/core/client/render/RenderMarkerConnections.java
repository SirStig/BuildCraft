/* Copyright (c) 2016 SpaceToad and the BuildCraft team
 * Copyright (c) 2026 Joshua Kac -- NeoForge port (BuildCraft 10)
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL
 * was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/. */
package buildcraft.core.client.render;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.client.event.TextureStitchEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import buildcraft.api.core.BCLog;

import buildcraft.lib.client.render.laser.CompiledLaser;
import buildcraft.lib.client.render.laser.LaserBoxRenderer;
import buildcraft.lib.client.render.laser.LaserData_BC8;
import buildcraft.lib.client.render.laser.LaserData_BC8.LaserType;
import buildcraft.lib.client.render.laser.LaserRenderer_BC8;
import buildcraft.lib.marker.MarkerCache;
import buildcraft.lib.marker.MarkerSubCache;
import buildcraft.lib.misc.VecUtil;

import buildcraft.BCCoreRegistries;
import buildcraft.BuildCraft;
import buildcraft.core.client.BuildCraftLaserManager;
import buildcraft.core.item.ItemMarkerConnector;
import buildcraft.core.marker.PathCache;
import buildcraft.core.marker.PathConnection;
import buildcraft.core.marker.VolumeCache;
import buildcraft.core.marker.VolumeConnection;

/** Draws every marker connection the client knows about -- volume boxes and paths -- plus, while the player holds a
 * marker connector, the "possible connection" lasers. See the 26.x copy of this class for what it replaces from
 * 1.12.2 and why it is a level-render event rather than a block entity renderer.
 *
 * <p><b>The real 1.20.1 hook</b>, confirmed against the real {@code forge-1.20.1-47.1.106} sources: this target has no
 * extract/submit split, so the whole thing runs in one {@link RenderLevelStageEvent} handler -- the direct
 * descendant of 1.12.2's {@code RenderWorldLastEvent}. Stage {@link RenderLevelStageEvent.Stage#AFTER_BLOCK_ENTITIES},
 * dispatched from {@code LevelRenderer#renderLevel} right after block entities are drawn with the same level
 * {@code PoseStack} they use (camera rotation applied, no translation -- each block entity translates by its own
 * position minus the camera's), so vertices are placed relative to {@code event.getCamera().getPosition()}, and
 * written into the shared {@code renderBuffers().bufferSource()} and flushed with {@code endBatch} for this render
 * type straight away. {@code LevelRenderer} does flush the whole buffer source again later in the frame, but only on
 * some paths, and it has already flushed the {@code entityCutoutNoCull} block-atlas batch before
 * {@code AFTER_ENTITIES} fires -- flushing our own batch explicitly doesn't depend on either detail.
 *
 * <p>Registered via {@code @Mod.EventBusSubscriber(value = Dist.CLIENT)}, which FML filters by dist from the scan
 * data before loading the class, so a dedicated server never loads it. {@link TextureStitchEvent.Post} is a mod-bus
 * event on this target and {@code Mod.EventBusSubscriber} registers a whole class on exactly one bus (its
 * {@code bus()} attribute -- confirmed via {@code javap} on {@code language-java-47.2.2}), so the atlas check lives in
 * the nested {@link AtlasCheck} class instead. */
@Mod.EventBusSubscriber(modid = BuildCraft.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class RenderMarkerConnections {
    /** 1.12.2's {@code VolumeConnection}/{@code PathConnection} {@code RENDER_SCALE}. */
    private static final double RENDER_SCALE = 1 / 16.05;
    /** 1.12.2's {@code RenderTickListener} "possible" laser scale. */
    private static final double POSSIBLE_SCALE = 1 / 16.0;

    private RenderMarkerConnections() {}

    @SubscribeEvent
    public static void onRenderLevelStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_BLOCK_ENTITIES) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        Level level = mc.level;
        if (level == null) {
            return;
        }
        Set<LaserData_BC8> lasers = new LinkedHashSet<>();

        for (VolumeConnection connection : VolumeCache.INSTANCE.getSubCache(level).getConnections()) {
            lasers.addAll(LaserBoxRenderer.makeLaserBox(connection.getBox(), BuildCraftLaserManager.MARKER_VOLUME_CONNECTED, true));
        }
        for (PathConnection connection : PathCache.INSTANCE.getSubCache(level).getConnections()) {
            addPathLasers(lasers, connection.getMarkerPositions());
        }

        Player player = mc.player;
        if (player != null && isHoldingConnector(player)) {
            for (MarkerCache<?> cache : MarkerCache.CACHES) {
                addPossibleLasers(lasers, cache.getSubCache(level), player);
            }
        }

        if (lasers.isEmpty()) {
            return;
        }
        Frustum frustum = event.getFrustum();
        List<CompiledLaser> compiled = new ArrayList<>(lasers.size());
        for (LaserData_BC8 data : lasers) {
            CompiledLaser laser = LaserRenderer_BC8.compile(data);
            if (frustum.isVisible(laser.bounds)) {
                compiled.add(laser);
            }
        }
        if (compiled.isEmpty()) {
            return;
        }
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        LaserRenderer_BC8.render(event.getPoseStack(), buffers, compiled, event.getCamera().getPosition());
        buffers.endBatch(LaserRenderer_BC8.renderType());
    }

    /** 1.12.2's {@code PathConnection#renderInWorld}: one laser per consecutive pair (the positions list already
     * repeats the first marker at the end for a loop), each end pulled 1/8 of a block towards the other so the laser
     * starts at the marker's surface rather than its centre. */
    private static void addPathLasers(Set<LaserData_BC8> lasers, List<BlockPos> positions) {
        BlockPos last = null;
        for (BlockPos p : positions) {
            if (last != null) {
                Vec3 from = Vec3.atCenterOf(last);
                Vec3 to = Vec3.atCenterOf(p);
                lasers.add(new LaserData_BC8(BuildCraftLaserManager.MARKER_PATH_CONNECTED, pullTowards(from, to),
                    pullTowards(to, from), RENDER_SCALE));
            }
            last = p;
        }
    }

    /** 1.12.2's {@code RenderTickListener#renderMarkerCache}: every valid-but-unmade connection, each pair once, in
     * the cache's own "possible" colour -- or {@code MARKER_DEFAULT_POSSIBLE} for the one the player is currently
     * aiming along (the one a right-click with the connector would make). */
    private static void addPossibleLasers(Set<LaserData_BC8> lasers, MarkerSubCache<?> cache, Player player) {
        for (BlockPos a : cache.getAllMarkers()) {
            for (BlockPos b : cache.getValidConnections(a)) {
                if (a.asLong() > b.asLong()) {
                    // Only render each pair once
                    continue;
                }
                Vec3 start = VecUtil.convertCenter(a);
                Vec3 end = VecUtil.convertCenter(b);

                LaserType laserType = cache.getPossibleLaserType();
                if (laserType == null || ItemMarkerConnector.doesInteract(a, b, player)) {
                    laserType = BuildCraftLaserManager.MARKER_DEFAULT_POSSIBLE;
                }
                lasers.add(new LaserData_BC8(laserType, pullTowards(start, end), pullTowards(end, start), POSSIBLE_SCALE));
            }
        }
    }

    private static Vec3 pullTowards(Vec3 from, Vec3 to) {
        return from.add(to.subtract(from).normalize().scale(0.125));
    }

    private static boolean isHoldingConnector(Player player) {
        return player.getMainHandItem().is(BCCoreRegistries.MARKER_CONNECTOR.get())
            || player.getOffhandItem().is(BCCoreRegistries.MARKER_CONNECTOR.get());
    }

    /** The classic "missing sprite" failure is silent (a magenta/black checkerboard beam, no exception), so check
     * every laser sprite straight after the block atlas is stitched, and drop any geometry compiled against the
     * previous atlas. */
    @Mod.EventBusSubscriber(modid = BuildCraft.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
    public static final class AtlasCheck {
        private AtlasCheck() {}

        @SubscribeEvent
        public static void onAtlasStitched(TextureStitchEvent.Post event) {
            TextureAtlas atlas = event.getAtlas();
            if (!TextureAtlas.LOCATION_BLOCKS.equals(atlas.location())) {
                return;
            }
            LaserRenderer_BC8.clearCache();
            int found = 0;
            for (ResourceLocation id : BuildCraftLaserManager.SPRITES) {
                TextureAtlasSprite sprite = atlas.getSprite(id);
                if (LaserRenderer_BC8.isMissing(sprite)) {
                    BCLog.logger.warn("[lib.laser] Laser sprite " + id + " is missing from the block atlas!");
                } else {
                    found++;
                }
            }
            BCLog.logger.info("[lib.laser] " + found + "/" + BuildCraftLaserManager.SPRITES.size()
                + " laser sprites present in " + atlas.location());
        }
    }
}
