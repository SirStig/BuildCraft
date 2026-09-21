/*
 * Copyright (c) 2016 SpaceToad and the BuildCraft team
 * Copyright (c) 2026 Joshua Kac -- NeoForge port (BuildCraft 10)
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL
 * was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.core.marker;

import java.util.List;
import java.util.function.Function;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;

import buildcraft.lib.marker.MarkerSavedData;

/** The concrete {@link MarkerSavedData} for volume markers -- the {@code T} that finally satisfies
 * {@link MarkerSavedData#createLoader(java.util.function.Supplier)}'s bound. See that method's javadoc for why
 * this indirection exists: a generic class parameterised over the sub-cache/connection types can never supply a
 * complete loader for itself.
 *
 * <p>{@link #LOADER} is the {@code Function<CompoundTag, VolumeSavedData>} {@link VolumeSubCache} passes to
 * {@code DimensionDataStorage.computeIfAbsent} alongside {@link #NAME} (unchanged from 1.12.2, which used it as a
 * raw {@code WorldSavedData} key). 1.12.2's two constructors ({@code VolumeSavedData(String)} and the
 * {@code NAME}-defaulting no-arg one) collapse to a single no-arg constructor: the save key now lives entirely in
 * the string passed to {@code computeIfAbsent}, not in an instance field a constructor could vary. */
public class VolumeSavedData extends MarkerSavedData<VolumeSubCache, VolumeConnection> {
    public static final String NAME = "buildcraft_marker_volume";
    public static final Function<CompoundTag, VolumeSavedData> LOADER = createLoader(VolumeSavedData::new);

    public void loadInto(VolumeSubCache subCache) {
        setCache(subCache);
        for (BlockPos p : markerPositions) {
            subCache.loadMarker(p, null);
        }
        for (List<BlockPos> list : markerConnections) {
            subCache.addConnection(new VolumeConnection(subCache, list));
        }
    }
}
