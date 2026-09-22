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
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;

import buildcraft.lib.misc.NBTUtilBC;
import buildcraft.lib.misc.StackUtil;

/**
 * One item moving through a pipe's internal space -- a direct port of 1.12.2's own {@code TravellingItem},
 * server-state fields only.
 *
 * <p><b>Every client-rendering member is dropped</b>, matching this batch's "no client rendering at all" scope:
 * {@code clientItemLink}/{@code stackSize} (the render-thread-safe item link and cached count),
 * {@code interpolatePosition}/{@code getRenderPosition}/{@code getRenderDirection}/{@code isVisible} (all pure
 * render-time helpers, reached only from the unported {@code IPipeFlowRenderer}). Nothing else changed:
 * {@code ItemStack.serializeNBT()}/{@code new ItemStack(NBTTagCompound)} become {@code ItemStack.CODEC}-based
 * round trips (this target has no item-NBT constructor at all -- stacks serialise through a codec since 1.20.5),
 * and colour/side/tried persist through the already-ported {@code NBTUtilBC.writeEnum}/{@code readEnum}/
 * {@code writeEnumSet}/{@code readEnumSet}, the same helpers {@code Pipe} itself uses for its own colour field.
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
        Tag stackTag = nbt.get("stack");
        if (stackTag == null) {
            stack = ItemStack.EMPTY;
        } else {
            stack = ItemStack.CODEC
                .parse(registries.createSerializationContext(NbtOps.INSTANCE), stackTag)
                .result()
                .orElse(ItemStack.EMPTY);
        }
        colour = NBTUtilBC.readEnum(nbt.get("colour"), DyeColor.class);
        toCenter = nbt.getBooleanOr("toCenter", false);
        speed = nbt.getDoubleOr("speed", 0.05);
        if (speed < 0.001) {
            // Just to make sure that we don't have an invalid speed
            speed = 0.001;
        }
        tickStarted = nbt.getIntOr("tickStarted", 0) + tickNow;
        tickFinished = nbt.getIntOr("tickFinished", 0) + tickNow;
        timeToDest = nbt.getIntOr("timeToDest", 0);

        side = NBTUtilBC.readEnum(nbt.get("side"), Direction.class);
        if (side == null || timeToDest == 0) {
            // Older version safety net, matching 1.12.2's own fallback.
            toCenter = true;
        }
        tried = NBTUtilBC.readEnumSet(nbt.get("tried"), Direction.class);
        isPhantom = nbt.getBooleanOr("isPhantom", false);
    }

    public CompoundTag writeToNbt(long tickNow, HolderLookup.Provider registries) {
        CompoundTag nbt = new CompoundTag();
        if (!stack.isEmpty()) {
            ItemStack.CODEC
                .encodeStart(registries.createSerializationContext(NbtOps.INSTANCE), stack)
                .result()
                .ifPresent(tag -> nbt.put("stack", tag));
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
