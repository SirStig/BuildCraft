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
import java.util.function.Supplier;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.saveddata.SavedData;

import buildcraft.api.core.BCLog;

/** Persists one {@link MarkerSubCache}'s marker positions and connection groupings to disk, as a flat list of
 * positions plus a list of position-groups (one group per connection) -- exactly 1.12.2's own on-disk shape,
 * carried over unchanged; nothing about a {@link MarkerConnection}'s own contents is serialised, only which
 * positions it currently holds. The concrete connection is reconstructed later from the grouping, the same way
 * 1.12.2 did.
 *
 * <p>{@code WorldSavedData}'s NBT contract is gone: {@link SavedData} on this target has nothing left but
 * {@code setDirty()}/{@code isDirty()} (confirmed via {@code javap} -- no overridable read/write method survives
 * at all). Persistence is now {@link Codec}-driven, through a separate {@code SavedDataType<T>} record (a
 * constructor {@link Supplier}, a {@code Codec<T>}, and an id) that a {@code DimensionDataStorage} consults when
 * a save is loaded or written. That record needs a <em>complete</em> {@code Codec<T>} for a <em>concrete,
 * constructible</em> {@code T} -- something a generic class parameterised over {@code S}/{@code C} can never
 * supply by itself, since it doesn't know what {@code T} actually is.
 *
 * <p>This class still extends {@link SavedData} (rather than becoming a fully detached data holder) precisely
 * so a future concrete subtype -- in {@code buildcraft.core.marker}, not ported yet -- satisfies
 * {@code SavedDataType<T extends SavedData>}'s bound through ordinary inheritance, without needing a second,
 * hand-written adapter class just to be a {@link SavedData}. What it cannot supply on its own is the
 * {@link Codec}; {@link #createCodec(Supplier)} is the escape hatch, a generic static factory a concrete
 * subtype calls with nothing but its own no-arg constructor reference (e.g. {@code VolumeSavedData::new}) to get
 * back a complete {@code Codec<T>} for its own {@code SavedDataType}. Everything this class' codec needs to
 * encode/decode -- {@link #markerPositions}/{@link #markerConnections} -- is already representable with
 * {@link BlockPos#CODEC} (confirmed present via {@code javap}) alone, so no connection-specific codec is needed
 * either.
 *
 * <p>{@link #refresh()} replaces 1.12.2's {@code writeToNBT}, which refreshed {@link #markerPositions}/
 * {@link #markerConnections} from the live {@link MarkerSubCache} inline, right before writing. There's no
 * per-instance "about to be saved" hook to put that in any more (the codec's {@code forGetter}s are the closest
 * equivalent moment a save actually reads this object's state), so {@link #createCodec}'s two {@code forGetter}s
 * both call {@link #refresh()} before returning their field -- idempotent, so calling it twice per encode costs
 * an extra pass, not correctness. */
public abstract class MarkerSavedData<S extends MarkerSubCache<C>, C extends MarkerConnection<C>> extends SavedData {
    protected static final boolean DEBUG_FULL = MarkerSubCache.DEBUG_FULL;

    protected final List<BlockPos> markerPositions = new ArrayList<>();
    protected final List<List<BlockPos>> markerConnections = new ArrayList<>();
    private S subCache;

    protected MarkerSavedData() {
    }

    /** Builds the {@link Codec} a concrete subtype needs for its own {@code SavedDataType}. {@code constructor}
     * is that subtype's own no-arg constructor reference -- the one piece this class cannot supply for itself.
     * See the class javadoc for why this indirection exists at all. */
    public static <S extends MarkerSubCache<C>, C extends MarkerConnection<C>, T extends MarkerSavedData<S, C>> Codec<T> createCodec(
        Supplier<T> constructor
    ) {
        return RecordCodecBuilder.create(instance -> instance.group(
            BlockPos.CODEC.listOf().fieldOf("positions").forGetter(data -> {
                data.refresh();
                return data.markerPositions;
            }),
            BlockPos.CODEC.listOf().listOf().fieldOf("connections").forGetter(data -> {
                data.refresh();
                return data.markerConnections;
            })
        ).apply(instance, (positions, connections) -> {
            T data = constructor.get();
            data.markerPositions.addAll(positions);
            for (List<BlockPos> connection : connections) {
                data.markerConnections.add(new ArrayList<>(connection));
            }
            if (DEBUG_FULL) {
                BCLog.logger.info("[lib.marker.full] Loaded " + data.markerPositions.size() + " position(s) and "
                    + data.markerConnections.size() + " connection(s) for " + data.getClass().getSimpleName());
            }
            return data;
        }));
    }

    public final void setCache(S subCache) {
        this.subCache = subCache;
    }

    /** Pulls the live state out of {@link #subCache} into {@link #markerPositions}/{@link #markerConnections}.
     * A no-op if {@link #setCache} hasn't run yet -- that only happens for an instance freshly created by the
     * {@code SavedDataType}'s constructor {@link Supplier} before its cache is attached, and there is nothing
     * to refresh from in that window. */
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

    /** This file has no per-field change tracking, so -- matching 1.12.2's own {@code isDirty() { return true;
     * }} -- it is always considered dirty rather than trying to track it precisely. */
    @Override
    public boolean isDirty() {
        return true;
    }
}
