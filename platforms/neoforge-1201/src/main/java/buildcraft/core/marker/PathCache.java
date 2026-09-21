/*
 * Copyright (c) 2016 SpaceToad and the BuildCraft team
 * Copyright (c) 2026 Joshua Kac -- NeoForge port (BuildCraft 10)
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL
 * was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.core.marker;

import net.minecraft.world.level.Level;

import buildcraft.lib.marker.MarkerCache;

/** The {@link MarkerCache} for path (waypoint) markers. Ported unchanged aside from the usual {@code World} ->
 * {@link Level} rename -- see {@code buildcraft.lib.marker}'s own progress entry in PORTING.md for everything
 * that framework already handles. Nothing yet calls {@link MarkerCache#registerCache} with {@link #INSTANCE};
 * see this package's PORTING.md entry. */
public class PathCache extends MarkerCache<PathSubCache> {
    public static final PathCache INSTANCE = new PathCache();

    public PathCache() {
        super("path");
    }

    @Override
    protected PathSubCache createSubCache(Level level) {
        return new PathSubCache(level);
    }
}
