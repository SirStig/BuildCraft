/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * Copyright (c) 2026 Joshua Kac -- NeoForge port (BuildCraft 10)
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL
 * was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.lib.marker;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;

import buildcraft.api.core.BCDebugging;
import buildcraft.api.core.BCLog;

/** Top-level registry of every marker-connection cache type BuildCraft has (one per marker "kind" -- volume
 * markers and path markers are each their own {@link MarkerCache}), plus the per-dimension {@link MarkerSubCache}
 * lookup shared by every registered type.
 *
 * <p>1.12.2 keyed the per-dimension maps by {@code World.provider.getDimension()}, a plain {@code int}. Dimension
 * ids are {@link ResourceKey}&lt;{@link Level}&gt; now, not integers, so {@code cacheClient}/{@code cacheServer}
 * are keyed by that instead. {@code World.isRemote} is {@link Level#isClientSide()}, and {@code EntityPlayerMP}
 * is {@link ServerPlayer}.
 *
 * <p>{@link #registerCache}'s 1.12.2 guard (throwing if
 * {@code Loader.instance().hasReachedState(LoaderState.POSTINITIALIZATION)}, i.e. "you registered this too
 * late") has no equivalent modern lifecycle check exposed the same way, and nothing in this tree calls
 * {@link #registerCache} before mod construction finishes anyway -- its only 1.12.2 callers were in
 * {@code buildcraft.core}/{@code buildcraft.builders}, neither ported yet. Dropped rather than replaced with a
 * speculative substitute; a real guard can come back once there is a real out-of-order registration to guard
 * against. */
public abstract class MarkerCache<S extends MarkerSubCache<?>> {
    public static final boolean DEBUG = BCDebugging.shouldDebugLog("lib.markers");
    public static final List<MarkerCache<?>> CACHES = new ArrayList<>();

    public final String name;

    private final Map<ResourceKey<Level>, S> cacheClient = new ConcurrentHashMap<>();
    private final Map<ResourceKey<Level>, S> cacheServer = new ConcurrentHashMap<>();

    public MarkerCache(String name) {
        this.name = name;
    }

    public static void registerCache(MarkerCache<?> cache) {
        CACHES.add(cache);
        if (DEBUG) {
            BCLog.logger.info("[lib.markers] Registered a cache " + cache.name + " with an ID of " + (CACHES.size() - 1));
        }
    }

    public static void postInit() {
        if (DEBUG) {
            BCLog.logger.info("[lib.markers] Sorted list of cache types:");
            for (int i = 0; i < CACHES.size(); i++) {
                final MarkerCache<?> cache = CACHES.get(i);
                BCLog.logger.info("  " + i + " = " + cache.name);
            }
            BCLog.logger.info("[lib.markers] Total of " + CACHES.size() + " cache types");
        }
    }

    public static void onPlayerJoinWorld(ServerPlayer player) {
        for (MarkerCache<?> cache : CACHES) {
            Level level = player.level();
            cache.getSubCache(level).onPlayerJoinWorld(player);
        }
    }

    public static void onWorldUnload(Level level) {
        for (MarkerCache<?> cache : CACHES) {
            cache.onWorldUnloadImpl(level);
        }
    }

    private void onWorldUnloadImpl(Level level) {
        Map<ResourceKey<Level>, S> cache = level.isClientSide() ? cacheClient : cacheServer;
        cache.remove(level.dimension());
    }

    protected abstract S createSubCache(Level level);

    public S getSubCache(Level level) {
        Map<ResourceKey<Level>, S> cache = level.isClientSide() ? cacheClient : cacheServer;
        return cache.computeIfAbsent(level.dimension(), k -> createSubCache(level));
    }
}
