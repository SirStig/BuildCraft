/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * Copyright (c) 2026 Joshua Kac -- NeoForge port (BuildCraft 10)
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL
 * was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.transport.pipe.behaviour;

import java.util.EnumMap;
import java.util.EnumSet;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;

import buildcraft.api.core.IStackFilter;
import buildcraft.api.transport.pipe.IFlowItems;
import buildcraft.api.transport.pipe.IPipe;
import buildcraft.api.transport.pipe.IPipeHolder.PipeMessageReceiver;

import buildcraft.lib.misc.NBTUtilBC;
import buildcraft.lib.misc.StackUtil;
import buildcraft.lib.tile.item.ItemHandlerSimple;

/**
 * The emzuli pipe (another BuildCraft 8-specific material, ported strictly from reading
 * {@code PipeBehaviourEmzuli} itself, not assumed): a wooden pipe with up to four independently-coloured
 * extraction "presets" (square/circle/triangle/cross, each with its own {@link DyeColor} tag and its own item
 * filter), cycled round-robin among whichever presets are currently "active" -- {@link #extractItems} overrides
 * {@link PipeBehaviourWood}'s plain unfiltered extraction to pull only items matching the current preset's
 * filter and colour tag, then advances to the next active preset.
 *
 * <p><b>A preset only ever becomes active by redstone action ({@code ActionExtractionPreset}, a
 * {@code PipeEventStatement}), and its filter is only ever set through a dedicated GUI
 * ({@code BCTransportGuis.PIPE_EMZULI}).</b> Neither exists in this port: gates/statements are out of scope for
 * this whole module (matching every other behaviour's own {@code addActions}/{@code onActionActivate} drop), and
 * no pipe has a GUI in this port yet (see {@code BCTransportClientRegistries}'s own javadoc). The data model below
 * (slot colours, filters, the active set, round-robin selection) is ported faithfully and will extract correctly
 * the moment either piece of plumbing exists to actually populate it -- until then this behaves like an inert
 * wooden pipe that never extracts (mirroring 1.12.2's own pipe with every preset left off), a deliberate,
 * documented scope cut rather than a bug. {@code onPipeActivate}'s GUI-opening branch is dropped with it, falling
 * back to plain wrench-driven facing selection like every other {@link PipeBehaviourDirectional} subtype.
 */
public class PipeBehaviourEmzuli extends PipeBehaviourWood {

    public enum SlotIndex {
        SQUARE(DyeColor.RED),
        CIRCLE(DyeColor.GREEN),
        TRIANGLE(DyeColor.BLUE),
        CROSS(DyeColor.YELLOW);

        public static final SlotIndex[] VALUES = values();

        public final DyeColor colour;

        SlotIndex(DyeColor colour) {
            this.colour = colour;
        }

        public SlotIndex next() {
            return switch (this) {
                case SQUARE -> CIRCLE;
                case CIRCLE -> TRIANGLE;
                case TRIANGLE -> CROSS;
                case CROSS -> SQUARE;
            };
        }
    }

    public final EnumMap<SlotIndex, DyeColor> slotColours = new EnumMap<>(SlotIndex.class);
    /** Per-preset item filters. Not NBT-persisted -- see this class's own javadoc: nothing in this port can set
     * one yet, so there is nothing meaningful to save. */
    public final ItemHandlerSimple invFilters = new ItemHandlerSimple(4, null);
    private final EnumSet<SlotIndex> activeSlots;
    private final byte[] activatedTtl = new byte[SlotIndex.VALUES.length];
    @Nullable
    private SlotIndex currentSlot = null;

    private final IStackFilter filter = this::filterMatches;

    public PipeBehaviourEmzuli(IPipe pipe) {
        super(pipe);
        activeSlots = EnumSet.noneOf(SlotIndex.class);
    }

    public PipeBehaviourEmzuli(IPipe pipe, CompoundTag nbt, HolderLookup.Provider registries) {
        super(pipe, nbt, registries);
        activeSlots = NBTUtilBC.readEnumSet(nbt.get("activeSlots"), SlotIndex.class);
        currentSlot = NBTUtilBC.readEnum(nbt.get("currentSlot"), SlotIndex.class);
        for (SlotIndex index : SlotIndex.VALUES) {
            int c = nbt.getByteOr("slotColours[" + index.ordinal() + "]", (byte) 0);
            if (c > 0 && c <= 16) {
                slotColours.put(index, DyeColor.byId(c - 1));
            }
        }
    }

    @Override
    public CompoundTag writeToNbt(HolderLookup.Provider registries) {
        CompoundTag nbt = super.writeToNbt(registries);
        nbt.put("activeSlots", NBTUtilBC.writeEnumSet(activeSlots, SlotIndex.class));
        nbt.put("currentSlot", NBTUtilBC.writeEnum(currentSlot));
        for (SlotIndex index : SlotIndex.VALUES) {
            DyeColor c = slotColours.get(index);
            nbt.putByte("slotColours[" + index.ordinal() + "]", (byte) (c == null ? 0 : c.getId() + 1));
        }
        return nbt;
    }

    @Override
    protected int extractItems(IFlowItems flow, @Nullable Direction dir, int count, boolean simulate) {
        if (currentSlot == null && !activeSlots.isEmpty()) {
            currentSlot = getNextSlot();
        }
        if (currentSlot == null) {
            return 0;
        }
        int extracted = flow.tryExtractItems(count, dir, slotColours.get(currentSlot), filter, simulate);
        if (extracted > 0 && !simulate) {
            currentSlot = getNextSlot();
            pipe.getHolder().scheduleNetworkUpdate(PipeMessageReceiver.BEHAVIOUR);
        }
        return extracted;
    }

    private boolean filterMatches(@NotNull ItemStack stack) {
        if (currentSlot == null) {
            return false;
        }
        ItemStack current = invFilters.getStackInSlot(currentSlot.ordinal());
        return StackUtil.isMatchingItemOrList(current, stack);
    }

    @Override
    public void onTick() {
        super.onTick();
        if (pipe.getHolder().getPipeLevel().isClientSide()) {
            return;
        }
        for (SlotIndex index : SlotIndex.VALUES) {
            byte val = activatedTtl[index.ordinal()];
            if (val > 0) {
                val--;
                activatedTtl[index.ordinal()] = val;
            }
            if (val == 0) {
                activeSlots.remove(index);
                if (currentSlot == index) {
                    currentSlot = getNextSlot();
                    pipe.getHolder().scheduleNetworkUpdate(PipeMessageReceiver.BEHAVIOUR);
                }
            }
        }
    }

    @Nullable
    private SlotIndex getNextSlot() {
        SlotIndex current = currentSlot == null ? SlotIndex.CROSS : currentSlot;
        int i = SlotIndex.VALUES.length;
        while (i-- > 0) {
            current = current.next();
            if (activeSlots.contains(current) && !invFilters.getStackInSlot(current.ordinal()).isEmpty()) {
                return current;
            }
        }
        return null;
    }

    @Nullable
    public SlotIndex getCurrentSlot() {
        return this.currentSlot;
    }

    public EnumSet<SlotIndex> getActiveSlots() {
        return this.activeSlots;
    }
}
