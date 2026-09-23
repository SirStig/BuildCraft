/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * Copyright (c) 2026 Joshua Kac -- NeoForge port (BuildCraft 10)
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL
 * was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.transport.plug;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;

import buildcraft.api.transport.pipe.IPipeHolder;
import buildcraft.api.transport.pipe.PipeEventHandler;
import buildcraft.api.transport.pipe.PipeEventItem;
import buildcraft.api.transport.pluggable.PipePluggable;
import buildcraft.api.transport.pluggable.PluggableDefinition;

import buildcraft.lib.misc.NBTUtilBC;

import buildcraft.transport.item.ItemPluggableLens;

/**
 * Port of 1.12.2's {@code buildcraft.silicon.plug.PluggableLens}: a non-blocking plug that colours (a lens) or
 * colours-and-filters (a filter) items passing through this face, matching {@code IWireEmitter}'s own dye colour
 * vocabulary rather than anything to do with light.
 *
 * <p>A lens ({@code isFilter == false}) stamps {@link #colour} onto every item entering or leaving through
 * {@link #side} that has no colour of its own yet ({@link #beforeInsert}/{@link #reachEnd}); a filter
 * ({@code isFilter == true}) instead only ever lets items of exactly {@link #colour} (or colourless items)
 * through this face, both for insertion ({@link #tryInsert}) and for routing priority
 * ({@link #sideCheck}/{@link #sideCheckAnyPos}) -- all four handlers ported line-for-line onto this port's own,
 * already-real {@link PipeEventItem} hierarchy (see {@code PipeFlowItems}, which already fires every one of
 * these events; nothing about the event shapes themselves needed changing).
 *
 * <p><b>Scope cuts:</b> {@code getModelRenderKey}/{@code KeyPlugLens} (the rendering rewrite, dropped everywhere
 * in this batch -- see {@link PluggableBlocker}'s own javadoc) and the bespoke {@code writeCreationPayload}/
 * {@code readPayload} network constructor (this port's whole-tile NBT sync already covers a placed lens's
 * colour/filter state -- see {@link PluggableGate}'s own javadoc for the same reasoning applied to gates).
 */
public class PluggableLens extends PipePluggable {
    private static final AABB[] BOXES = new AABB[6];

    static {
        double ll = 0 / 16.0;
        double lu = 2 / 16.0;
        double ul = 14 / 16.0;
        double uu = 16 / 16.0;

        double min = 3 / 16.0;
        double max = 13 / 16.0;

        BOXES[Direction.DOWN.get3DDataValue()] = new AABB(min, ll, min, max, lu, max);
        BOXES[Direction.UP.get3DDataValue()] = new AABB(min, ul, min, max, uu, max);
        BOXES[Direction.NORTH.get3DDataValue()] = new AABB(min, min, ll, max, max, lu);
        BOXES[Direction.SOUTH.get3DDataValue()] = new AABB(min, min, ul, max, max, uu);
        BOXES[Direction.WEST.get3DDataValue()] = new AABB(ll, min, min, lu, max, max);
        BOXES[Direction.EAST.get3DDataValue()] = new AABB(ul, min, min, uu, max, max);
    }

    @Nullable
    public final DyeColor colour;
    public final boolean isFilter;

    public PluggableLens(
        PluggableDefinition def, IPipeHolder holder, Direction side, @Nullable DyeColor colour, boolean isFilter
    ) {
        super(def, holder, side);
        this.colour = colour;
        this.isFilter = isFilter;
    }

    public PluggableLens(PluggableDefinition def, IPipeHolder holder, Direction side, CompoundTag nbt) {
        super(def, holder, side);
        this.colour = NBTUtilBC.readEnum(nbt.get("colour"), DyeColor.class);
        this.isFilter = nbt.getBoolean("filter");
    }

    @Override
    public CompoundTag writeToNbt(HolderLookup.Provider registries) {
        CompoundTag nbt = super.writeToNbt(registries);
        nbt.put("colour", NBTUtilBC.writeEnum(colour));
        nbt.putBoolean("filter", isFilter);
        return nbt;
    }

    // PipePluggable

    @Override
    public AABB getBoundingBox() {
        return BOXES[side.get3DDataValue()];
    }

    @Override
    public boolean isBlocking() {
        return false;
    }

    @Override
    public ItemStack getPickStack() {
        return ItemPluggableLens.getStack(colour, isFilter);
    }

    // Item events

    @PipeEventHandler
    public void tryInsert(PipeEventItem.TryInsert tryInsert) {
        if (isFilter && tryInsert.from == side) {
            DyeColor itemColour = tryInsert.colour;
            if (itemColour != null && itemColour != colour) {
                tryInsert.cancel();
            }
        }
    }

    @PipeEventHandler
    public void sideCheck(PipeEventItem.SideCheck event) {
        if (isFilter) {
            if (event.colour == colour) {
                event.increasePriority(side);
            } else if (event.colour != null) {
                event.disallow(side);
            } else {
                event.decreasePriority(side);
            }
        }
    }

    /** Called from either *this* pipe, or the neighbouring pipe as given in {@code compareSide} -- ported as-is
     * from the original, which never actually calls this from anywhere else either (confirmed: no caller exists
     * anywhere in the 8.0.1 source this was ported from). Kept rather than dropped since it is genuine,
     * self-contained {@code PluggableLens} behaviour that some other class may yet be ported to call. */
    void sideCheckAnyPos(PipeEventItem.SideCheck event, Direction compareSide) {
        // Note that this should *never* use "this.side" as it may be wrong!
        if (isFilter) {
            if (event.colour == colour) {
                event.increasePriority(compareSide);
            } else if (event.colour != null) {
                if (compareSide == side) {
                    event.disallow(compareSide);
                }
            } else {
                event.decreasePriority(compareSide);
            }
        }
    }

    @PipeEventHandler
    public void beforeInsert(PipeEventItem.OnInsert event) {
        if (!isFilter && event.from == side) {
            event.colour = colour;
        }
    }

    @PipeEventHandler
    public void reachEnd(PipeEventItem.ReachEnd event) {
        if (!isFilter && event.to == side) {
            event.colour = colour;
        }
    }
}
