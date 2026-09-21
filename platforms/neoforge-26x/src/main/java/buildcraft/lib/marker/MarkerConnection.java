/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * Copyright (c) 2026 Joshua Kac -- NeoForge port (BuildCraft 10)
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL
 * was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.lib.marker;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import org.jetbrains.annotations.Nullable;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;

import buildcraft.lib.tile.TileMarker;

/** A single connected group of markers of one {@link MarkerCache} type -- for example, one volume box or one
 * pipeline of path markers.
 *
 * <p>{@code TextFormatting} is {@link ChatFormatting} now (see PORTING.md's method-rename table); nothing here
 * needs more than its {@code toString()} escape sequence, so the 26.x-specific loss of {@code isColor()}/
 * {@code getColor()}/{@code getChar()}/{@code getName()} (see {@code ColourUtil}'s class javadoc) doesn't affect
 * this file.
 *
 * <p>{@link #renderInWorld()} was {@code @SideOnly(Side.CLIENT)} in 1.12.2. That annotation is gone, the same
 * situation {@link buildcraft.api.tiles.IDebuggable} already documents: the method is simply only ever called
 * from client code, so it stays abstract and plain rather than annotated. */
public abstract class MarkerConnection<C extends MarkerConnection<C>> {
    public final MarkerSubCache<C> subCache;

    public MarkerConnection(MarkerSubCache<C> subCache) {
        this.subCache = subCache;
    }

    /** Removes the specified marker from this connection. This should be called via
     * {@link MarkerSubCache#removeMarker(BlockPos)}. This may need to remove itself and split itself up (if the resulting
     * connection is invalid). */
    public abstract void removeMarker(BlockPos pos);

    public abstract Collection<BlockPos> getMarkerPositions();

    public abstract void renderInWorld();

    public void getDebugInfo(BlockPos caller, List<String> left) {
        Collection<BlockPos> positions = getMarkerPositions();
        List<BlockPos> list = new ArrayList<>(positions);
        if (positions instanceof Set) {
            Collections.sort(list);
        }
        for (BlockPos pos : list) {
            TileMarker<C> marker = subCache.getMarker(pos);
            String s = "  " + pos + " [";
            if (marker == null) {
                s += ChatFormatting.RED + "U";
            } else {
                s += ChatFormatting.GREEN + "L";
            }
            if (pos.equals(caller)) {
                s += ChatFormatting.BLACK + "S";
            } else {
                s += ChatFormatting.AQUA + "C";
            }
            s += getTypeInfo(pos, marker);
            s += ChatFormatting.RESET + "]";
            left.add(s);
        }
    }

    protected String getTypeInfo(BlockPos pos, @Nullable TileMarker<C> value) {
        return "";
    }
}
