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
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.util.context.ContextKey;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ExtractLevelRenderStateEvent;
import net.neoforged.neoforge.client.event.SubmitCustomGeometryEvent;
import net.neoforged.neoforge.client.event.TextureAtlasStitchedEvent;

import buildcraft.api.core.BCLog;
import buildcraft.api.core.BuildCraftAPI;

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
 * marker connector, the "possible connection" lasers between markers that could be linked. Together these replace
 * three 1.12.2 pieces: {@code lib.client.render.MarkerRenderer} (which called each connection's
 * {@code renderInWorld()}), {@code VolumeConnection#renderInWorld}/{@code PathConnection#renderInWorld} themselves,
 * and {@code core.client.RenderTickListener#renderMarkerConnector}.
 *
 * <p><b>Why a level-render event and not a {@code BlockEntityRenderer}</b> (compare {@link RenderMarkerVolume}, which
 * is one): 1.12.2 drew these from {@code RenderWorldLastEvent} via {@code DetachedRenderer}, not from a TESR, for a
 * real reason that still holds. A connection isn't owned by any one marker -- a volume box has up to eight corner
 * markers and a path any number, any of which may sit in an unloaded chunk (the client cache keeps unloaded markers'
 * positions and every connection, pushed by {@code MessageMarker}); a block entity renderer only runs for a loaded,
 * visible block entity, so hanging the box off "one of its markers" would make it flicker in and out as that one
 * marker's section was culled or unloaded. The data lives in the per-level client cache, so it is drawn per level.
 *
 * <p><b>The real 26.3 hook</b>, confirmed against the NeoForge 26.3.0.7-beta sources and the patched
 * {@code LevelRenderer}/{@code LevelExtractor}: the old "render stage" event is split the same way block entity
 * renderers are. {@link ExtractLevelRenderStateEvent} (fired from {@code LevelExtractor} once vanilla extraction is
 * done, with the {@code ClientLevel}, {@code Camera} and {@code Frustum}) is where the cache may be read and the
 * lasers compiled; the result is parked on the {@code LevelRenderState} under a {@link ContextKey}
 * ({@code BaseRenderState#setRenderData}). {@link SubmitCustomGeometryEvent} (fired from
 * {@code LevelRenderer#submitFeatures}, right after block entities and particles are submitted) then submits it
 * through the same {@code SubmitNodeCollector} a block entity renderer gets. Its {@code PoseStack} is a fresh,
 * camera-relative one ({@code new PoseStack()} in {@code submitFeatures}), so vertices are placed relative to
 * {@code cameraRenderState.pos} -- exactly what NeoForge's own {@code BlockEntityRenderBoundsDebugRenderer} does with
 * this same event pair. {@code RenderLevelStageEvent} still exists, but its stages now hand out a raw, already-open
 * {@code RenderPass} (with any buffer upload required to happen earlier, in {@code PrepareRenderBuffersEvent}), and
 * its own javadoc directs custom {@code SubmitNodeCollector} geometry to {@link SubmitCustomGeometryEvent}.
 *
 * <p>Registered with {@code @EventBusSubscriber(value = Dist.CLIENT)}: FML reads the {@code value} from the
 * annotation scan data <em>before</em> loading the class (confirmed via {@code javap -c} on
 * {@code AutomaticEventSubscriber}), so a dedicated server never loads it, and each {@code @SubscribeEvent} method is
 * routed to the mod or game bus by whether its event is an {@code IModBusEvent} -- which is why the atlas-stitch
 * check can live here too. */
@EventBusSubscriber(value = Dist.CLIENT, modid = BuildCraft.MOD_ID)
public final class RenderMarkerConnections {
    /** 1.12.2's {@code VolumeConnection}/{@code PathConnection} {@code RENDER_SCALE}. */
    private static final double RENDER_SCALE = 1 / 16.05;
    /** 1.12.2's {@code RenderTickListener} "possible" laser scale. */
    private static final double POSSIBLE_SCALE = 1 / 16.0;

    private static final ContextKey<List<CompiledLaser>> LASERS =
        new ContextKey<>(BuildCraftAPI.nameToResourceId("marker_lasers"));

    private RenderMarkerConnections() {}

    @SubscribeEvent
    public static void onExtract(ExtractLevelRenderStateEvent event) {
        Level level = event.getLevel();
        Set<LaserData_BC8> lasers = new LinkedHashSet<>();

        for (VolumeConnection connection : VolumeCache.INSTANCE.getSubCache(level).getConnections()) {
            lasers.addAll(LaserBoxRenderer.makeLaserBox(connection.getBox(), BuildCraftLaserManager.MARKER_VOLUME_CONNECTED, true));
        }
        for (PathConnection connection : PathCache.INSTANCE.getSubCache(level).getConnections()) {
            addPathLasers(lasers, connection.getMarkerPositions());
        }

        Player player = Minecraft.getInstance().player;
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
        if (!compiled.isEmpty()) {
            event.getRenderState().setRenderData(LASERS, compiled);
        }
    }

    @SubscribeEvent
    public static void onSubmit(SubmitCustomGeometryEvent event) {
        List<CompiledLaser> lasers = event.getLevelRenderState().getRenderData(LASERS);
        if (lasers == null) {
            return;
        }
        Vec3 camera = event.getLevelRenderState().cameraRenderState.pos;
        LaserRenderer_BC8.submit(event.getSubmitNodeCollector(), event.getPoseStack(), lasers, camera);
    }

    /** The classic "missing sprite" failure is silent (a magenta/black checkerboard beam, no exception), so check
     * every laser sprite straight after the block atlas is stitched, and drop any geometry compiled against the
     * previous atlas. */
    @SubscribeEvent
    public static void onAtlasStitched(TextureAtlasStitchedEvent event) {
        TextureAtlas atlas = event.getAtlas();
        if (!TextureAtlas.LOCATION_BLOCKS.equals(atlas.location())) {
            return;
        }
        LaserRenderer_BC8.clearCache();
        int found = 0;
        for (Identifier id : BuildCraftLaserManager.SPRITES) {
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
}
