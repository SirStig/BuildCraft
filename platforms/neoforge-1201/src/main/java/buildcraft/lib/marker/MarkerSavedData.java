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
import java.util.function.Function;
import java.util.function.Supplier;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.saveddata.SavedData;

import buildcraft.api.core.BCLog;

import buildcraft.lib.misc.NBTUtilBC;

/** Persists one {@link MarkerSubCache}'s marker positions and connection groupings to disk, as a flat list of
 * positions plus a list of position-groups (one group per connection) -- exactly 1.12.2's own on-disk shape,
 * carried over unchanged; nothing about a {@link MarkerConnection}'s own contents is serialised, only which
 * positions it currently holds. The concrete connection is reconstructed later from the grouping, the same way
 * 1.12.2 did.
 *
 * <p>{@code WorldSavedData}'s NBT contract narrowed on this target too, just not as far as on 26.x: {@code
 * DimensionDataStorage.computeIfAbsent} (confirmed via {@code javap}) now takes a
 * {@code Function<CompoundTag, T>} loader and a {@code Supplier<T>} "no save yet" factory as separate arguments,
 * rather than 1.12.2's reflection-based {@code WorldSavedData.loadData(clazz, name)} calling an overridden
 * {@code readFromNBT} automatically. {@link SavedData#save(CompoundTag)} is still there and still abstract, but
 * the read side has moved entirely off the object itself. Both the loader function and the save-time
 * {@code DimensionDataStorage} bound to it need a concrete, constructible {@code T} -- something a generic class
 * parameterised over {@code S}/{@code C} can never supply by itself.
 *
 * <p>This class still extends {@link SavedData} (rather than becoming a fully detached data holder) so that a
 * future concrete subtype -- in {@code buildcraft.core.marker}, not ported yet -- satisfies
 * {@code DimensionDataStorage}'s {@code <T extends SavedData>} bound through ordinary inheritance, without a
 * second, hand-written adapter class just to be a {@link SavedData}. {@link #save(CompoundTag)} needs no
 * subtype-specific knowledge, so it is implemented here directly rather than left abstract; what this class
 * cannot supply on its own is the loader, since that needs to construct a {@code T}.
 * {@link #createLoader(Supplier)} is the escape hatch, a generic static factory a concrete subtype calls with
 * nothing but its own no-arg constructor reference (e.g. {@code VolumeSavedData::new}) to get back a complete
 * {@code Function<CompoundTag, T>} for its own {@code computeIfAbsent} call.
 *
 * <p>{@link #refresh()} replaces 1.12.2's {@code writeToNBT}, which refreshed {@link #markerPositions}/
 * {@link #markerConnections} from the live {@link MarkerSubCache} inline, right before writing -- {@link #save}
 * still has exactly that hook, so it calls {@link #refresh()} itself before encoding. */
public abstract class MarkerSavedData<S extends MarkerSubCache<C>, C extends MarkerConnection<C>> extends SavedData {
    protected static final boolean DEBUG_FULL = MarkerSubCache.DEBUG_FULL;

    protected final List<BlockPos> markerPositions = new ArrayList<>();
    protected final List<List<BlockPos>> markerConnections = new ArrayList<>();
    private S subCache;

    protected MarkerSavedData() {
    }

    /** Builds the {@code Function<CompoundTag, T>} loader a concrete subtype needs for its own
     * {@code DimensionDataStorage.computeIfAbsent} call. {@code constructor} is that subtype's own no-arg
     * constructor reference -- the one piece this class cannot supply for itself. See the class javadoc for why
     * this indirection exists at all. */
    public static <S extends MarkerSubCache<C>, C extends MarkerConnection<C>, T extends MarkerSavedData<S, C>> Function<CompoundTag, T> createLoader(
        Supplier<T> constructor
    ) {
        return nbt -> {
            T data = constructor.get();

            ListTag positionList = nbt.getList("positions", Tag.TAG_INT_ARRAY);
            for (int i = 0; i < positionList.size(); i++) {
                data.markerPositions.add(NBTUtilBC.readBlockPos(positionList.get(i)));
            }

            ListTag connectionList = nbt.getList("connections", Tag.TAG_LIST);
            for (int i = 0; i < connectionList.size(); i++) {
                ListTag inner = (ListTag) connectionList.get(i);
                List<BlockPos> connection = new ArrayList<>();
                for (int j = 0; j < inner.size(); j++) {
                    connection.add(NBTUtilBC.readBlockPos(inner.get(j)));
                }
                data.markerConnections.add(connection);
            }

            if (DEBUG_FULL) {
                BCLog.logger.info("[lib.marker.full] Loaded " + data.markerPositions.size() + " position(s) and "
                    + data.markerConnections.size() + " connection(s) for " + data.getClass().getSimpleName());
            }
            return data;
        };
    }

    public final void setCache(S subCache) {
        this.subCache = subCache;
    }

    /** Pulls the live state out of {@link #subCache} into {@link #markerPositions}/{@link #markerConnections}.
     * A no-op if {@link #setCache} hasn't run yet -- that only happens for an instance freshly created by
     * {@code computeIfAbsent}'s "no save yet" {@link Supplier} before its cache is attached, and there is
     * nothing to refresh from in that window. */
    protected final void refresh() {
        if (subCache == null) {
            return;
        }
        markerPositions.clear();
        markerConnections.clear();
        markerPositions.addAll(subCache.getAllMarkers());
        for (C connection : subCache.getConnections()) {
            markerConnections.add(new ArrayList<>(connection.getMarkerPositions()));
        }
    }

    @Override
    public CompoundTag save(CompoundTag nbt) {
        refresh();

        ListTag positionList = new ListTag();
        for (BlockPos p : markerPositions) {
            positionList.add(NBTUtilBC.writeBlockPos(p));
        }
        nbt.put("positions", positionList);

        ListTag connectionList = new ListTag();
        for (List<BlockPos> connection : markerConnections) {
            ListTag inner = new ListTag();
            for (BlockPos p : connection) {
                inner.add(NBTUtilBC.writeBlockPos(p));
            }
            connectionList.add(inner);
        }
        nbt.put("connections", connectionList);

        if (DEBUG_FULL) {
            BCLog.logger.info("[lib.marker.full] Saved " + markerPositions.size() + " position(s) and "
                + markerConnections.size() + " connection(s) for " + getClass().getSimpleName());
        }

        return nbt;
    }

    /** This file has no per-field change tracking, so -- matching 1.12.2's own {@code isDirty() { return true;
     * }} -- it is always considered dirty rather than trying to track it precisely. */
    @Override
    public boolean isDirty() {
        return true;
    }
}
