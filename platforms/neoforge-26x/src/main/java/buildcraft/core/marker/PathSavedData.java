/*
 * Copyright (c) 2016 SpaceToad and the BuildCraft team
 * Copyright (c) 2026 Joshua Kac -- NeoForge port (BuildCraft 10)
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL
 * was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.core.marker;

import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.saveddata.SavedDataType;

import buildcraft.api.core.BuildCraftAPI;

import buildcraft.lib.marker.MarkerSavedData;

/** The concrete {@link MarkerSavedData} for path markers -- the {@code T} that finally satisfies
 * {@link MarkerSavedData#createCodec(java.util.function.Supplier)}'s bound. See that method's javadoc for why
 * this indirection exists: a generic class parameterised over the sub-cache/connection types can never supply a
 * complete {@code Codec} for itself.
 *
 * <p>{@link #TYPE} is this file's {@code SavedDataType<PathSavedData>}, built from {@link #NAME} (unchanged from
 * 1.12.2, which used it as a raw {@code WorldSavedData} key) via {@link BuildCraftAPI#nameToResourceId}, the same
 * helper every other id in this port resolves through. 1.12.2's two constructors ({@code PathSavedData(String)}
 * and the {@code NAME}-defaulting no-arg one) collapse to a single no-arg constructor: the save key now lives
 * entirely in {@link #TYPE}'s id, not in an instance field a constructor could vary. */
public class PathSavedData extends MarkerSavedData<PathSubCache, PathConnection> {
    public static final String NAME = "buildcraft_marker_path";
    public static final SavedDataType<PathSavedData> TYPE = new SavedDataType<>(
        BuildCraftAPI.nameToResourceId(NAME), PathSavedData::new, createCodec(PathSavedData::new)
    );

    public void loadInto(PathSubCache subCache) {
        setCache(subCache);
        for (BlockPos p : markerPositions) {
            subCache.loadMarker(p, null);
        }
        for (List<BlockPos> list : markerConnections) {
            subCache.addConnection(new PathConnection(subCache, list));
        }
    }
}
