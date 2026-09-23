/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * Copyright (c) 2026 Joshua Kac -- NeoForge port (BuildCraft 10)
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL
 * was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.transport.pipe.behaviour;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.BlockHitResult;

import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidUtil;
import net.minecraftforge.items.IItemHandlerModifiable;

import buildcraft.api.core.EnumPipePart;
import buildcraft.api.core.IStackFilter;
import buildcraft.api.transport.pipe.IFlowFluid;
import buildcraft.api.transport.pipe.IFlowItems;
import buildcraft.api.transport.pipe.IPipe;
import buildcraft.api.transport.pipe.IPipeHolder.PipeMessageReceiver;

import buildcraft.lib.inventory.filter.ArrayFluidFilter;
import buildcraft.lib.inventory.filter.DelegatingItemHandlerFilter;
import buildcraft.lib.inventory.filter.InvertedFluidFilter;
import buildcraft.lib.inventory.filter.InvertedStackFilter;
import buildcraft.lib.inventory.filter.StackFilter;
import buildcraft.lib.misc.EntityUtil;
import buildcraft.lib.misc.StackUtil;
import buildcraft.lib.tile.item.ItemHandlerSimple;

/**
 * The wood/diamond combo pipe -- see the 26.x copy of this class for the full account of the design. This copy
 * is a much closer, near-literal port of 1.12.2's own {@code PipeBehaviourWoodDiamond}: {@link ItemHandlerSimple}
 * here still implements {@code IItemHandlerModifiable} and the multi-slot {@code extract(IStackFilter, min, max,
 * simulate)} 1.12.2's own class used directly, so {@link #getStackFilter}/{@link #extractFluid} need no rewrite
 * against a different filter-walking shape.
 *
 * <p><b>{@code tryExtractFluidAdv}'s {@code ActionResult<FluidStack>} return becomes a plain nullable
 * {@code FluidStack}</b> (see {@code IFlowFluid}'s own javadoc): {@code null} is this target's spelling of
 * 1.12.2's {@code EnumActionResult.PASS} ("this tank doesn't implement the filtered interface, ask the plain
 * method instead"), a non-null result (even an empty-amount one) is 1.12.2's {@code SUCCESS}/{@code FAIL} --
 * "the tank answered for real, use this and stop". {@link #extractFluid}'s branching follows that translation
 * exactly.
 *
 * <p><b>Wrench handling is a defensive no-op check, not a real branch</b> -- see the 26.x copy's own javadoc for
 * why (real on 1.20.1: {@code BlockState#use} is tried before the wrench's own {@code useOn} on this target,
 * confirmed via {@code BlockPipeHolder#attemptRotation}'s own updated javadoc).
 */
public class PipeBehaviourWoodDiamond extends PipeBehaviourWood {

    public enum FilterMode {
        WHITE_LIST,
        BLACK_LIST,
        ROUND_ROBIN;

        public static FilterMode get(int index) {
            switch (index) {
                default:
                case 0:
                    return WHITE_LIST;
                case 1:
                    return BLACK_LIST;
                case 2:
                    return ROUND_ROBIN;
            }
        }
    }

    public final ItemHandlerSimple filters = new ItemHandlerSimple(9, this::onSlotChanged);
    public FilterMode filterMode = FilterMode.WHITE_LIST;
    public int currentFilter = 0;
    public boolean filterValid = false;

    public PipeBehaviourWoodDiamond(IPipe pipe) {
        super(pipe);
    }

    public PipeBehaviourWoodDiamond(IPipe pipe, CompoundTag nbt, HolderLookup.Provider registries) {
        super(pipe, nbt, registries);
        filters.deserializeNBT(nbt.getCompound("filters"));
        filterMode = FilterMode.get(nbt.getByte("mode"));
        currentFilter = nbt.getByte("currentFilter") % filters.getSlots();
        filterValid = !filters.extract(StackFilter.ALL, 1, 1, true).isEmpty();
    }

    @Override
    public CompoundTag writeToNbt(HolderLookup.Provider registries) {
        CompoundTag nbt = super.writeToNbt(registries);
        nbt.put("filters", filters.serializeNBT());
        nbt.putByte("mode", (byte) filterMode.ordinal());
        nbt.putByte("currentFilter", (byte) currentFilter);
        return nbt;
    }

    @Override
    public boolean onPipeActivate(Player player, BlockHitResult trace, EnumPipePart part) {
        if (EntityUtil.getWrenchHand(player) != null) {
            // See this class's own javadoc: real on this target.
            return false;
        }
        return true;
    }

    private void onSlotChanged(IItemHandlerModifiable itemHandler, int slot, ItemStack before, ItemStack after) {
        if (!after.isEmpty()) {
            if (!filterValid) {
                currentFilter = slot;
                filterValid = true;
            }
        } else if (slot == currentFilter) {
            advanceFilter();
        }
    }

    private IStackFilter getStackFilter() {
        switch (filterMode) {
            default:
            case WHITE_LIST:
                if (filters.extract(s -> true, 1, 1, true).isEmpty()) {
                    return s -> true;
                }
                return new DelegatingItemHandlerFilter(StackUtil::isMatchingItemOrList, filters);
            case BLACK_LIST:
                return new InvertedStackFilter(
                    new DelegatingItemHandlerFilter(StackUtil::isMatchingItemOrList, filters));
            case ROUND_ROBIN:
                return (comparison) -> {
                    ItemStack filter = filters.getStackInSlot(currentFilter);
                    return StackUtil.isMatchingItemOrList(filter, comparison);
                };
        }
    }

    @Override
    protected int extractItems(IFlowItems flow, @Nullable Direction dir, int count, boolean simulate) {
        if (filters.getStackInSlot(currentFilter).isEmpty()) {
            advanceFilter();
        }
        int extracted = flow.tryExtractItems(1, dir, null, getStackFilter(), simulate);
        if (extracted > 0 && filterMode == FilterMode.ROUND_ROBIN && !simulate) {
            advanceFilter();
        }
        return extracted;
    }

    @Override
    protected int extractFluid(IFlowFluid flow, @Nullable Direction dir, int millibuckets, boolean simulate) {
        if (dir == null) {
            return 0;
        }
        if (filters.getStackInSlot(currentFilter).isEmpty()) {
            advanceFilter();
        }

        switch (filterMode) {
            default:
            case WHITE_LIST: {
                if (filters.extract(s -> true, 1, 1, true).isEmpty()) {
                    FluidStack extracted = flow.tryExtractFluid(millibuckets, dir, null, simulate);
                    return extracted == null ? 0 : extracted.getAmount();
                }
                // Firstly try the advanced version -- null means the tank doesn't support it, try the basic
                // version per filter slot instead (see this class's own javadoc for the ActionResult->null
                // translation).
                FluidStack extracted = flow.tryExtractFluidAdv(millibuckets, dir, new ArrayFluidFilter(filters.stacks), simulate);
                if (extracted != null) {
                    return extracted.getAmount();
                }
                for (int i = 0; i < filters.getSlots(); i++) {
                    ItemStack stack = filters.getStackInSlot(i);
                    if (stack.isEmpty()) {
                        continue;
                    }
                    FluidStack contained = FluidUtil.getFluidContained(stack).orElse(null);
                    if (contained == null) {
                        continue;
                    }
                    FluidStack fallback = flow.tryExtractFluid(millibuckets, dir, contained, simulate);
                    if (fallback != null && fallback.getAmount() > 0) {
                        return fallback.getAmount();
                    }
                }
                return 0;
            }
            case BLACK_LIST: {
                // We cannot fallback to the basic version -- only use the advanced version.
                InvertedFluidFilter filter = new InvertedFluidFilter(new ArrayFluidFilter(filters.stacks));
                FluidStack extracted = flow.tryExtractFluidAdv(millibuckets, dir, filter, simulate);
                return extracted == null ? 0 : extracted.getAmount();
            }
            case ROUND_ROBIN:
                // We can't do this -- amounts might differ and its just ugly.
                return 0;
        }
    }

    private void advanceFilter() {
        int lastFilter = currentFilter;
        filterValid = false;
        while (true) {
            currentFilter++;
            if (currentFilter >= filters.getSlots()) {
                currentFilter = 0;
            }
            if (!filters.getStackInSlot(currentFilter).isEmpty()) {
                filterValid = true;
                break;
            }
            if (currentFilter == lastFilter) {
                break;
            }
        }
        if (lastFilter != currentFilter) {
            pipe.getHolder().scheduleNetworkUpdate(PipeMessageReceiver.BEHAVIOUR);
        }
    }
}
