/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * Copyright (c) 2026 Joshua Kac -- NeoForge port (BuildCraft 10)
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL
 * was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.transport.pipe.flow;

import java.util.EnumSet;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;

import buildcraft.lib.misc.NBTUtilBC;
import buildcraft.lib.misc.StackUtil;

/**
 * One item moving through a pipe's internal space -- a direct port of 1.12.2's own {@code TravellingItem},
 * server-state fields only. See the 26.x copy of this class for the full account of every client-rendering
 * member dropped -- unchanged here.
 *
 * <p>The one genuine per-platform divergence: item serialisation. 1.20.1 has no transfer API and no data
 * components, so a stack round-trips through the classic {@code ItemStack#save(CompoundTag)}/
 * {@code ItemStack.of(CompoundTag)} pair, exactly like 1.12.2's own {@code serializeNBT()}/
 * {@code new ItemStack(NBTTagCompound)} -- just renamed. The constructor still takes a
 * {@code HolderLookup.Provider}, unused on this target, purely so {@code PipeFlowItems}' own NBT constructor
 * (which does need one, to match {@code PipeFlow}'s shared two-platform constructor shape) has one signature to
 * call on both targets.
 */
public class TravellingItem {
    /** The server itemstack. */
    @NotNull
    ItemStack stack;
    @Nullable
    DyeColor colour;
    boolean toCenter;
    double speed = 0.05;
    /** Absolute times (relative to {@code level.getGameTime()}) with when an item started to when it finishes. */
    long tickStarted, tickFinished;
    /** Relative time (from {@link #tickStarted}) until an event needs to be fired or this item needs changing. */
    int timeToDest;
    /** If {@link #toCenter} is true then this represents the side that the item is coming from, otherwise this
     * represents the side that the item is going to. */
    @Nullable
    Direction side;
    /** Every face this item has already tried and failed to go through. */
    EnumSet<Direction> tried = EnumSet.noneOf(Direction.class);
    /** If true then events won't be fired for this, and this item won't be dropped by the pipe. However it
     * still affects things like {@code doesContainItems}. */
    boolean isPhantom = false;

    public TravellingItem(@NotNull ItemStack stack) {
        this.stack = stack;
    }

    public TravellingItem(CompoundTag nbt, long tickNow, HolderLookup.Provider registries) {
        stack = nbt.contains("stack") ? ItemStack.of(nbt.getCompound("stack")) : ItemStack.EMPTY;
        colour = NBTUtilBC.readEnum(nbt.get("colour"), DyeColor.class);
        toCenter = nbt.getBoolean("toCenter");
        speed = nbt.getDouble("speed");
        if (speed < 0.001) {
            // Just to make sure that we don't have an invalid speed
            speed = 0.001;
        }
        tickStarted = nbt.getInt("tickStarted") + tickNow;
        tickFinished = nbt.getInt("tickFinished") + tickNow;
        timeToDest = nbt.getInt("timeToDest");

        side = NBTUtilBC.readEnum(nbt.get("side"), Direction.class);
        if (side == null || timeToDest == 0) {
            // Older version safety net, matching 1.12.2's own fallback.
            toCenter = true;
        }
        tried = NBTUtilBC.readEnumSet(nbt.get("tried"), Direction.class);
        isPhantom = nbt.getBoolean("isPhantom");
    }

    public CompoundTag writeToNbt(long tickNow, HolderLookup.Provider registries) {
        CompoundTag nbt = new CompoundTag();
        if (!stack.isEmpty()) {
            nbt.put("stack", stack.save(new CompoundTag()));
        }
        nbt.put("colour", NBTUtilBC.writeEnum(colour));
        nbt.putBoolean("toCenter", toCenter);
        nbt.putDouble("speed", speed);
        nbt.putInt("tickStarted", (int) (tickStarted - tickNow));
        nbt.putInt("tickFinished", (int) (tickFinished - tickNow));
        nbt.putInt("timeToDest", timeToDest);
        nbt.put("side", NBTUtilBC.writeEnum(side));
        nbt.put("tried", NBTUtilBC.writeEnumSet(tried, Direction.class));
        if (isPhantom) {
            nbt.putBoolean("isPhantom", true);
        }
        return nbt;
    }

    public int getCurrentDelay(long tickNow) {
        long diff = tickFinished - tickNow;
        if (diff < 0) {
            return 0;
        } else {
            return (int) diff;
        }
    }

    public void genTimings(long now, double distance) {
        tickStarted = now;
        timeToDest = (int) Math.ceil(distance / speed);
        tickFinished = now + timeToDest;
    }

    public boolean canMerge(TravellingItem with) {
        if (isPhantom || with.isPhantom) {
            return false;
        }
        return toCenter == with.toCenter
            && colour == with.colour
            && side == with.side
            && Math.abs(tickFinished - with.tickFinished) < 4
            && stack.getMaxStackSize() >= stack.getCount() + with.stack.getCount()
            && StackUtil.canMerge(stack, with.stack);
    }

    /** Attempts to merge the two travelling items together, if they are close enough. */
    public boolean mergeWith(TravellingItem with) {
        if (canMerge(with)) {
            this.stack.grow(with.stack.getCount());
            return true;
        }
        return false;
    }
}
