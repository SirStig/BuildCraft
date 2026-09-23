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
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.phys.BlockHitResult;

import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.fluid.FluidUtil;
import net.neoforged.neoforge.transfer.resource.ResourceStack;
import net.neoforged.neoforge.transfer.transaction.Transaction;

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
import buildcraft.lib.misc.EntityUtil;
import buildcraft.lib.misc.StackUtil;
import buildcraft.lib.tile.item.ItemHandlerSimple;

/**
 * The wood/diamond combo pipe -- a filtered wooden pipe (the wooden pipe's active-face MJ-powered extraction,
 * gated by a 9-slot whitelist/blacklist/round-robin filter). A close port of 1.12.2's own
 * {@code PipeBehaviourWoodDiamond}, adapted for two API shapes this port has already established elsewhere:
 *
 * <ul>
 * <li><b>Fluid extraction goes through {@link IFlowFluid#tryExtractFluid}'s single, filter-taking overload</b>
 * (see that interface's own javadoc) instead of 1.12.2's separate basic/{@code Adv} pair -- the {@code PASS}
 * fallback case from the original ("the tank doesn't implement the filtered interface, try the basic method")
 * cannot arise here, since every handler is filterable. {@code filters.stacks} (a public field 1.12.2 read
 * directly for {@link ArrayFluidFilter}) does not exist on {@link ItemHandlerSimple} here -- {@link #filterStacks()}
 * rebuilds the same {@code ItemStack[]} through {@code getStackInSlot}, matching {@code DelegatingItemHandlerFilter}'s
 * own already-ported "walk every slot" shape.</li>
 * <li><b>Wrench handling is a defensive no-op check, not a real branch.</b> 1.12.2's own
 * {@code onPipeActivate} called {@code super.onPipeActivate(...)} (the directional wrench-cycle) when a wrench was
 * held. On this port wrench-driven facing cycling happens entirely through
 * {@code BlockPipeHolder#attemptRotation} (see {@link PipeBehaviourDirectional}'s own javadoc) -- a route that
 * runs <em>before</em> this method is ever reached on 26.x (the wrench's own {@code useOn} already returns a
 * non-{@code PASS} result for a directional pipe, so the empty-hand fallback this method answers is never tried).
 * The check is kept anyway, returning {@code false} while a wrench is held, purely so this class behaves
 * correctly on 1.20.1 too, where {@code BlockState#use} is tried <em>before</em> the wrench's own {@code useOn}
 * -- see that platform's copy of {@code BlockPipeHolder#attemptRotation} for the confirmed ordering divergence.
 * Without this check, a wrench click on 1.20.1 would open the filter GUI instead of ever reaching
 * {@code attemptRotation}.</li>
 * <li><b>No {@code IItemPluggable} check.</b> 1.12.2's own branch existed to let a pluggable item handle its own
 * click instead of opening the GUI underneath it. No {@code PipePluggable} type is registered anywhere in this
 * port yet ({@code TilePipeHolder#getPluggable} always returns {@code null} -- see that class's own javadoc), so
 * there is nothing for this branch to defer to; dropped rather than guarding against a case that cannot occur.</li>
 * </ul>
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
        ValueInput input = TagValueInput.create(ProblemReporter.DISCARDING, registries, nbt.getCompoundOrEmpty("filters"));
        filters.deserialize(input);
        filterMode = FilterMode.get(nbt.getByteOr("mode", (byte) 0));
        currentFilter = nbt.getByteOr("currentFilter", (byte) 0) % filters.size();
        filterValid = !filters.getStackInSlot(currentFilter).isEmpty();
    }

    @Override
    public CompoundTag writeToNbt(HolderLookup.Provider registries) {
        CompoundTag nbt = super.writeToNbt(registries);
        TagValueOutput output = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, registries);
        filters.serialize(output);
        nbt.put("filters", output.buildResult());
        nbt.putByte("mode", (byte) filterMode.ordinal());
        nbt.putByte("currentFilter", (byte) currentFilter);
        return nbt;
    }

    @Override
    public boolean onPipeActivate(Player player, BlockHitResult trace, EnumPipePart part) {
        if (EntityUtil.getWrenchHand(player) != null) {
            // See this class's own javadoc: real on 1.20.1, redundant-but-harmless on 26.x.
            return false;
        }
        return true;
    }

    private void onSlotChanged(ItemHandlerSimple itemHandler, int slot, ItemStack before, ItemStack after) {
        if (!after.isEmpty()) {
            if (!filterValid) {
                currentFilter = slot;
                filterValid = true;
            }
        } else if (slot == currentFilter) {
            advanceFilter();
        }
    }

    private boolean hasAnyFilter() {
        for (int i = 0; i < filters.size(); i++) {
            if (!filters.getStackInSlot(i).isEmpty()) {
                return true;
            }
        }
        return false;
    }

    private ItemStack[] filterStacks() {
        ItemStack[] stacks = new ItemStack[filters.size()];
        for (int i = 0; i < stacks.length; i++) {
            stacks[i] = filters.getStackInSlot(i);
        }
        return stacks;
    }

    private IStackFilter getStackFilter() {
        switch (filterMode) {
            default:
            case WHITE_LIST:
                if (!hasAnyFilter()) {
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

        if (filterMode == FilterMode.ROUND_ROBIN) {
            // Amounts might differ between filters and it's just ugly -- unimplemented in 1.12.2 too.
            return 0;
        }

        try (Transaction transaction = Transaction.openRoot()) {
            ResourceStack<FluidResource> extracted;
            if (filterMode == FilterMode.WHITE_LIST) {
                if (!hasAnyFilter()) {
                    extracted = flow.tryExtractFluid(millibuckets, dir, null, transaction);
                } else {
                    extracted = flow.tryExtractFluid(millibuckets, dir, new ArrayFluidFilter(filterStacks()), transaction);
                    if (extracted == null || extracted.isEmpty()) {
                        for (int i = 0; i < filters.size(); i++) {
                            ItemStack stack = filters.getStackInSlot(i);
                            if (stack.isEmpty()) {
                                continue;
                            }
                            FluidStack contained = FluidUtil.getFirstStackContained(stack);
                            if (contained.isEmpty()) {
                                continue;
                            }
                            extracted = flow.tryExtractFluid(
                                millibuckets, dir, new ArrayFluidFilter(FluidResource.of(contained)), transaction);
                            if (extracted != null && !extracted.isEmpty()) {
                                break;
                            }
                        }
                    }
                }
            } else {
                // BLACK_LIST -- no basic-method fallback, matching 1.12.2 (there is no unfiltered "not these" query).
                InvertedFluidFilter filter = new InvertedFluidFilter(new ArrayFluidFilter(filterStacks()));
                extracted = flow.tryExtractFluid(millibuckets, dir, filter, transaction);
            }
            if (extracted == null || extracted.isEmpty()) {
                return 0;
            }
            if (!simulate) {
                transaction.commit();
            }
            return extracted.amount();
        }
    }

    private void advanceFilter() {
        int lastFilter = currentFilter;
        filterValid = false;
        while (true) {
            currentFilter++;
            if (currentFilter >= filters.size()) {
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
