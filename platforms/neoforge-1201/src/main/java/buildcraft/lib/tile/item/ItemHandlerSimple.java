/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * Copyright (c) 2026 Joshua Kac -- NeoForge port (BuildCraft 10)
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL
 * was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.lib.tile.item;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.world.item.ItemStack;

import net.minecraftforge.common.util.INBTSerializable;
import net.minecraftforge.items.IItemHandlerModifiable;

import buildcraft.api.core.IStackFilter;

import buildcraft.lib.inventory.AbstractInvItemTransactor;
import buildcraft.lib.misc.StackUtil;

/**
 * A simple array-backed item store: BuildCraft's concrete slot-based inventory, used by essentially every
 * machine.
 *
 * <p>Keeps 1.12.2's shape almost unchanged -- this target still has {@code IItemHandler}/
 * {@code IItemHandlerModifiable} with a {@code boolean simulate} parameter, so there is no NeoForge transaction
 * API to rebuild against (contrast the 26.x port of this same file, which is a rewrite against
 * {@code StacksResourceHandler}).
 *
 * <p>{@code StackInsertionFunction} and its {@code InsertionResult} are dropped, matching the 26.x port's
 * reasoning: checked against every call site in the 1.12.2 tree, the general {@code modifyForInsertion} hook was
 * <em>only ever</em> constructed via its two factories, {@code getInsertionFunction(maxStackSize)} and
 * {@code getDefaultInserter()}. A plain {@code maxStackSize} field, capping the same merge/split arithmetic
 * inline in {@link #insertItem}, says everything the real usage needed. That also removes the
 * checker/inserter-disagreement branch 1.12.2 guarded with a crash report: with a single hardcoded insertion
 * algorithm there is nothing left for a custom inserter to disagree with {@link #canSet} about.
 *
 * <p>{@code IItemHandlerAdv} (1.12.2's {@code IItemHandler + StackInsertionChecker} marker) is kept, since
 * {@code IItemHandler} still exists here and the GUI/slot layer (deferred with the rest of {@code lib.gui})
 * consumes it by that marker type.
 *
 * <p>The "first used slot" scan 1.12.2 hand-maintained in {@code setStackInternal} is dropped: it was computed
 * but never read anywhere outside that same method, a vestigial optimisation with no observable behaviour.
 */
public class ItemHandlerSimple extends AbstractInvItemTransactor
    implements IItemHandlerModifiable, IItemHandlerAdv, INBTSerializable<CompoundTag> {

    private StackInsertionChecker checker;
    private int maxStackSize = Integer.MAX_VALUE;

    @Nullable
    private StackChangeCallback callback;

    public final NonNullList<ItemStack> stacks;

    public ItemHandlerSimple(int size) {
        this(size, (slot, stack) -> true, null);
    }

    public ItemHandlerSimple(int size, int maxStackSize) {
        this(size);
        setMaxStackSize(maxStackSize);
    }

    public ItemHandlerSimple(int size, @Nullable StackChangeCallback callback) {
        this(size, (slot, stack) -> true, callback);
    }

    public ItemHandlerSimple(int size, StackInsertionChecker checker, @Nullable StackChangeCallback callback) {
        stacks = NonNullList.withSize(size, StackUtil.EMPTY);
        this.checker = checker;
        this.callback = callback;
    }

    public void setChecker(StackInsertionChecker checker) {
        this.checker = checker;
    }

    public void setMaxStackSize(int maxStackSize) {
        this.maxStackSize = maxStackSize;
    }

    public void setCallback(@Nullable StackChangeCallback callback) {
        this.callback = callback;
    }

    @Override
    public CompoundTag serializeNBT() {
        CompoundTag nbt = new CompoundTag();
        ListTag list = new ListTag();
        for (ItemStack stack : stacks) {
            list.add(stack.save(new CompoundTag()));
        }
        nbt.put("items", list);
        return nbt;
    }

    @Override
    public void deserializeNBT(CompoundTag nbt) {
        ListTag list = nbt.getList("items", net.minecraft.nbt.Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size() && i < getSlots(); i++) {
            setStackInternal(i, ItemStack.of(list.getCompound(i)));
        }
        for (int i = list.size(); i < getSlots(); i++) {
            setStackInternal(i, StackUtil.EMPTY);
        }
    }

    @Override
    public int getSlots() {
        return stacks.size();
    }

    private boolean badSlotIndex(int slot) {
        return slot < 0 || slot >= stacks.size();
    }

    @Override
    protected boolean isEmpty(int slot) {
        if (badSlotIndex(slot)) return true;
        return stacks.get(slot).isEmpty();
    }

    @Override
    @NotNull
    public ItemStack getStackInSlot(int slot) {
        if (badSlotIndex(slot)) return StackUtil.EMPTY;
        return asValid(stacks.get(slot));
    }

    @Override
    @NotNull
    public ItemStack insertItem(int slot, @NotNull ItemStack stack, boolean simulate) {
        if (badSlotIndex(slot)) {
            return stack;
        }
        if (!canSet(slot, stack)) {
            return stack;
        }
        ItemStack current = stacks.get(slot);
        if (!canSet(slot, current)) {
            // A bit odd, but can happen if the filter changed
            return stack;
        }
        if (stack.isEmpty()) {
            return StackUtil.EMPTY;
        }

        ItemStack toSet, toReturn;
        if (current.isEmpty()) {
            int maxSize = Math.min(maxStackSize, stack.getMaxStackSize());
            if (stack.getCount() <= maxSize) {
                toSet = stack.copy();
                toReturn = StackUtil.EMPTY;
            } else {
                ItemStack copy = stack.copy();
                toSet = copy.split(maxSize);
                toReturn = copy;
            }
        } else if (current.getCount() >= maxStackSize) {
            return stack;
        } else if (StackUtil.canMerge(current, stack)) {
            ItemStack complete = current.copy();
            int count = current.getCount() + stack.getCount();
            int maxSize = Math.min(maxStackSize, complete.getMaxStackSize());
            if (count <= maxSize) {
                complete.setCount(count);
                toSet = complete;
                toReturn = StackUtil.EMPTY;
            } else {
                complete.setCount(maxSize);
                ItemStack leftOver = stack.copy();
                leftOver.setCount(count - maxSize);
                toSet = complete;
                toReturn = leftOver;
            }
        } else {
            return stack;
        }

        if (!simulate) {
            setStackInternal(slot, toSet);
            if (callback != null) {
                callback.onStackChange(this, slot, current, toSet);
            }
        }
        return asValid(toReturn);
    }

    @Override
    @NotNull
    protected ItemStack insert(int slot, @NotNull ItemStack stack, boolean simulate) {
        return insertItem(slot, stack, simulate);
    }

    @Override
    @NotNull
    public ItemStack extractItem(int slot, int amount, boolean simulate) {
        if (badSlotIndex(slot)) return StackUtil.EMPTY;
        // You can ALWAYS extract. if you couldn't then you could never take out items from anywhere
        ItemStack current = stacks.get(slot);
        if (current.isEmpty()) return StackUtil.EMPTY;
        if (current.getCount() < amount) {
            if (simulate) {
                return asValid(current.copy());
            }
            setStackInternal(slot, StackUtil.EMPTY);
            if (callback != null) {
                callback.onStackChange(this, slot, current, StackUtil.EMPTY);
            }
            // no need to copy as we no longer have it
            return current;
        } else {
            ItemStack before = current;
            current = current.copy();
            ItemStack split = current.split(amount);
            if (!simulate) {
                if (current.getCount() <= 0) current = StackUtil.EMPTY;
                setStackInternal(slot, current);
                if (callback != null) {
                    callback.onStackChange(this, slot, before, current);
                }
            }
            return split;
        }
    }

    @Override
    @NotNull
    protected ItemStack extract(int slot, IStackFilter filter, int min, int max, boolean simulate) {
        if (badSlotIndex(slot)) return StackUtil.EMPTY;
        if (min <= 0) min = 1;
        if (max < min) return StackUtil.EMPTY;
        ItemStack current = stacks.get(slot);
        ItemStack before = current.copy();
        if (current.getCount() < min) return StackUtil.EMPTY;
        if (filter.matches(asValid(current))) {
            if (simulate) {
                ItemStack copy = current.copy();
                return copy.split(max);
            }
            ItemStack split = current.split(max);
            if (current.getCount() <= 0) {
                stacks.set(slot, StackUtil.EMPTY);
            }
            if (callback != null) {
                callback.onStackChange(this, slot, before, stacks.get(slot));
            }
            return split;
        }
        return StackUtil.EMPTY;
    }

    @Override
    public void setStackInSlot(int slot, @NotNull ItemStack stack) {
        if (badSlotIndex(slot)) {
            // Its safe to throw here
            throw new IndexOutOfBoundsException("Slot index out of range: " + slot);
        }
        ItemStack before = stacks.get(slot);
        setStackInternal(slot, stack);
        if (callback != null) {
            callback.onStackChange(this, slot, before, asValid(stack));
        }
    }

    @Override
    public final boolean canSet(int slot, @NotNull ItemStack stack) {
        ItemStack copied = asValid(stack);
        if (copied.isEmpty()) {
            return true;
        }
        return checker.canSet(slot, copied);
    }

    /** 1.12.2's {@code IItemHandler} had no such method to implement; this target's does, with no default body,
     * so it is wired straight to {@link #canSet}, the same check {@link #insertItem} already runs. */
    @Override
    public boolean isItemValid(int slot, @NotNull ItemStack stack) {
        return canSet(slot, stack);
    }

    private void setStackInternal(int slot, @NotNull ItemStack stack) {
        stacks.set(slot, asValid(stack));
    }

    @Override
    public int getSlotLimit(int slot) {
        return 64;
    }

    @Override
    public String toString() {
        return "ItemHandlerSimple " + stacks;
    }
}
