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

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.item.ItemStacksResourceHandler;

import buildcraft.lib.misc.StackUtil;

/**
 * A simple array-backed item store: BuildCraft's concrete slot-based inventory, used by essentially every
 * machine.
 *
 * <p>This is a rewrite against NeoForge's own {@code StacksResourceHandler} base ({@code ItemStacksResourceHandler}
 * is its {@link ItemStack} specialisation), not a line-by-line port of the 1.12.2 class, because that base
 * class already solves the three hard parts of this file by itself:
 *
 * <ul>
 * <li><b>Transaction safety.</b> {@code StacksResourceHandler} snapshots and rolls back each slot through
 *     NeoForge's own {@code SnapshotJournal}-based bookkeeping. 1.12.2's {@code insertItem}/{@code extractItem}
 *     had to build a "does this actually work" simulation-then-commit dance in-line (throwing a
 *     {@code ReportedException} crash report if a checker and inserter disagreed); on this target that
 *     bookkeeping is inherited, not written.</li>
 * <li><b>NBT persistence.</b> {@code StacksResourceHandler implements ValueIOSerializable}, wired to
 *     {@code ValueInput}/{@code ValueOutput} already -- the modern block entity serialisation hook (see
 *     PORTING.md's NBT/data-components note). 1.12.2's hand-written {@code serializeNBT}/{@code deserializeNBT}
 *     (a raw {@code NBTTagList} of item compounds) has nothing left to do; {@code INBTSerializable}, which they
 *     implemented, does not exist on this target either.</li>
 * <li><b>Per-slot capacity.</b> {@code getCapacity(slot, resource)} already exists as an extension point. 1.12.2
 *     needed a whole separate abstraction, {@code StackInsertionFunction}, to express "cap this slot's stack
 *     size" -- a generic {@code modifyForInsertion(slot, addingTo, toInsert)} hook that, checked against every
 *     call site in the 1.12.2 tree, was <em>only ever</em> constructed via its two factories,
 *     {@code getInsertionFunction(maxStackSize)} and {@code getDefaultInserter()}. Nothing used the general
 *     form. {@code StackInsertionFunction} and its {@code InsertionResult} are dropped rather than ported: a
 *     plain {@code maxStackSize} field feeding {@code getCapacity} says everything the real usage needed, and
 *     is what the modern API expects capacity limiting to look like.</li>
 * </ul>
 *
 * <p>What genuinely carries over is BuildCraft's own two extension points, {@link StackInsertionChecker} (which
 * of two now-different slot filters should accept an incoming resource) and {@link StackChangeCallback} (react
 * to a slot changing, typically to mark a block entity dirty) -- both redefined in {@code ItemResource} terms
 * since {@code IItemHandler}/{@code IItemHandlerModifiable} do not exist here; see each interface's own javadoc.
 * {@code IItemHandlerAdv}, 1.12.2's {@code IItemHandler + StackInsertionChecker} marker interface, is not ported
 * either -- there is no {@code IItemHandler} left for it to combine with, and nothing needs a marker beyond
 * implementing {@link StackInsertionChecker} directly.
 *
 * <p>The public field {@code stacks} from 1.12.2 is gone too: {@code StacksResourceHandler.stacks} is
 * {@code protected}, and outside callers go through {@link #getStackInSlot(int)} /
 * {@link #getResource(int)} instead of reaching into the backing list directly. The "first used slot" scan
 * optimisation 1.12.2 hand-maintained for its transactor is also dropped -- purely a micro-optimisation, no
 * behaviour depends on it, and BuildCraft's inventories are not large enough for it to matter.
 */
public class ItemHandlerSimple extends ItemStacksResourceHandler {

    private StackInsertionChecker checker;
    private int maxStackSize = Item.ABSOLUTE_MAX_STACK_SIZE;

    @Nullable
    private StackChangeCallback callback;

    public ItemHandlerSimple(int size) {
        this(size, (slot, resource) -> true, null);
    }

    public ItemHandlerSimple(int size, int maxStackSize) {
        this(size);
        setMaxStackSize(maxStackSize);
    }

    public ItemHandlerSimple(int size, @Nullable StackChangeCallback callback) {
        this(size, (slot, resource) -> true, callback);
    }

    public ItemHandlerSimple(int size, StackInsertionChecker checker, @Nullable StackChangeCallback callback) {
        super(size);
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
    public boolean isValid(int index, ItemResource resource) {
        return checker.canSet(index, resource);
    }

    @Override
    protected int getCapacity(int index, ItemResource resource) {
        return Math.min(maxStackSize, super.getCapacity(index, resource));
    }

    @Override
    protected void onContentsChanged(int index, ItemStack previousContents) {
        super.onContentsChanged(index, previousContents);
        if (callback != null) {
            callback.onStackChange(this, index, previousContents, getStackInSlot(index));
        }
    }

    /** @return A copy of the stack in the given slot, or {@link StackUtil#EMPTY} if the slot is out of range. */
    @NotNull
    public ItemStack getStackInSlot(int slot) {
        if (slot < 0 || slot >= size()) {
            return StackUtil.EMPTY;
        }
        ItemResource resource = getResource(slot);
        return resource.isEmpty() ? StackUtil.EMPTY : resource.toStack(getAmountAsInt(slot));
    }

    /** Directly overwrites the given slot, bypassing {@link #isValid} and capacity -- matches 1.12.2's
     * {@code setStackInSlot}, which had the same unchecked contract. */
    public void setStackInSlot(int slot, @NotNull ItemStack stack) {
        if (slot < 0 || slot >= size()) {
            throw new IndexOutOfBoundsException("Slot index out of range: " + slot);
        }
        if (stack.isEmpty()) {
            set(slot, ItemResource.EMPTY, 0);
        } else {
            set(slot, ItemResource.of(stack), stack.getCount());
        }
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("ItemHandlerSimple [");
        for (int i = 0; i < size(); i++) {
            if (i > 0) sb.append(", ");
            sb.append(getStackInSlot(i));
        }
        return sb.append(']').toString();
    }
}
