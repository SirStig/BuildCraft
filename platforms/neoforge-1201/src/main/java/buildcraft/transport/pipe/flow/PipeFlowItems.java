/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * Copyright (c) 2026 Joshua Kac -- NeoForge port (BuildCraft 10)
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL
 * was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.transport.pipe.flow;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import com.google.common.collect.ImmutableList;

import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.items.IItemHandler;

import buildcraft.api.core.IStackFilter;
import buildcraft.api.inventory.IItemTransactor;
import buildcraft.api.transport.IInjectable;
import buildcraft.api.transport.pipe.IFlowItems;
import buildcraft.api.transport.pipe.IPipe;
import buildcraft.api.transport.pipe.IPipe.ConnectedType;
import buildcraft.api.transport.pipe.IPipeHolder;
import buildcraft.api.transport.pipe.PipeApi;
import buildcraft.api.transport.pipe.PipeEventItem;
import buildcraft.api.transport.pipe.PipeFlow;

import buildcraft.lib.inventory.ItemTransactorHelper;
import buildcraft.lib.inventory.NoSpaceTransactor;
import buildcraft.lib.misc.StackUtil;

/**
 * The item-moving simulation itself: an item picked up at one end of a connected pipe run travels through the
 * pipe's internal space and is deposited at the other end. A close port of 1.12.2's own {@code PipeFlowItems},
 * implementing the already-ported {@link IFlowItems} ({@code extends} the already-ported {@link IInjectable}).
 *
 * <p>Unlike the 26.x copy of this class, {@link IInjectable#injectItem}/{@link IItemTransactor}'s own
 * {@code insert}/{@code extract} keep 1.12.2's own {@code ItemStack}/{@code boolean} shape verbatim on this
 * target (1.20.1 has no transfer API at all -- see {@code IInjectable}'s own javadoc), so this file is much
 * closer to a literal transliteration of the original: no transaction/journal machinery is needed anywhere here,
 * because there is no caller-owned {@code Transaction} to stay safe under in the first place -- a
 * {@code boolean simulate} either mutates {@link #buckets} or it does not, exactly like 1.12.2 did.
 *
 * <p>See the 26.x copy of this class's own javadoc for the full, still-applicable account of what is deliberately
 * dropped (network sync, {@code addTriggers}/gates) and why -- unchanged here. The delay-bucket queue
 * ({@link #buckets}) is likewise a small, self-contained reimplementation of 1.12.2's own {@code DelayedList<E>},
 * not a port of that class under its own name -- see the 26.x copy for why.
 */
public final class PipeFlowItems extends PipeFlow implements IFlowItems {
    private static final double EXTRACT_SPEED = 0.08;
    /** Fallback speed delta for an item leaving the centre when nothing modified its speed -- see the 26.x
     * copy of this class's own javadoc for where this constant comes from. */
    private static final double FALLBACK_SPEED_DELTA = 0.008;

    /** {@code buckets.get(n)} holds every item that will be advanced after {@code n} more calls to
     * {@link #onTick()}. */
    private final List<List<TravellingItem>> buckets = new ArrayList<>();
    private final List<ItemStack> postDropCache = new ArrayList<>();

    public PipeFlowItems(IPipe pipe) {
        super(pipe);
    }

    public PipeFlowItems(IPipe pipe, CompoundTag nbt, HolderLookup.Provider registries) {
        super(pipe, nbt, registries);
        long tickNow = pipe.getHolder().getPipeLevel().getGameTime();
        for (Tag itemTag : nbt.getList("items", Tag.TAG_COMPOUND)) {
            if (itemTag instanceof CompoundTag itemCompound) {
                TravellingItem item = new TravellingItem(itemCompound, tickNow, registries);
                if (!item.stack.isEmpty()) {
                    addDelayed(item.getCurrentDelay(tickNow), item);
                }
            }
        }
    }

    @Override
    public CompoundTag writeToNbt(HolderLookup.Provider registries) {
        CompoundTag nbt = super.writeToNbt(registries);
        long tickNow = pipe.getHolder().getPipeLevel().getGameTime();
        ListTag list = new ListTag();
        for (List<TravellingItem> bucket : buckets) {
            for (TravellingItem item : bucket) {
                list.add(item.writeToNbt(tickNow, registries));
            }
        }
        nbt.put("items", list);
        return nbt;
    }

    @Override
    public void addDrops(NonNullList<ItemStack> toDrop, int fortune) {
        super.addDrops(toDrop, fortune);
        for (List<TravellingItem> bucket : buckets) {
            for (TravellingItem item : bucket) {
                if (!item.isPhantom) {
                    toDrop.add(item.stack);
                }
            }
        }
    }

    // IFlowItems

    @Override
    public int tryExtractItems(int count, Direction from, @Nullable DyeColor colour, IStackFilter filter, boolean simulate) {
        if (pipe.getHolder().getPipeLevel().isClientSide()) {
            throw new IllegalStateException("Cannot extract items on the client side!");
        }
        if (from == null) {
            return 0;
        }

        BlockEntity tile = pipe.getConnectedTile(from);
        IItemTransactor trans = ItemTransactorHelper.getTransactor(tile, from.getOpposite());

        ItemStack possible = trans.extract(filter, 1, count, true);
        if (possible.isEmpty()) {
            return 0;
        }
        if (possible.getCount() > possible.getMaxStackSize()) {
            possible = possible.copy();
            possible.setCount(possible.getMaxStackSize());
            count = possible.getMaxStackSize();
        }

        IPipeHolder holder = pipe.getHolder();
        PipeEventItem.TryInsert tryInsert = new PipeEventItem.TryInsert(holder, this, colour, from, possible);
        holder.fireEvent(tryInsert);
        if (tryInsert.isCanceled() || tryInsert.accepted <= 0) {
            return 0;
        }

        count = Math.min(count, tryInsert.accepted);

        ItemStack stack = trans.extract(filter, count, count, simulate);
        if (stack.isEmpty()) {
            throw new IllegalStateException(
                "The transactor " + trans + " returned an empty itemstack from a known good request!"
            );
        }

        if (!simulate) {
            insertItemEvents(stack, colour, EXTRACT_SPEED, from);
        }

        return count;
    }

    @Override
    public void sendPhantomItem(
        @NotNull ItemStack stack, @Nullable Direction from, @Nullable Direction to, @Nullable DyeColor colour
    ) {
        if (from == null && to == null) {
            return;
        }
        long now = pipe.getHolder().getPipeLevel().getGameTime();

        TravellingItem firstItem = new TravellingItem(stack);
        firstItem.isPhantom = true;
        firstItem.toCenter = to == null || from != null;
        firstItem.colour = colour;
        firstItem.side = from == null ? to : from;
        firstItem.speed = EXTRACT_SPEED;
        firstItem.genTimings(now, getPipeLength(firstItem.side));
        addDelayed(firstItem.timeToDest, firstItem);

        if (from != null && to != null) {
            TravellingItem secondItem = new TravellingItem(stack);
            secondItem.isPhantom = true;
            secondItem.toCenter = false;
            secondItem.colour = colour;
            secondItem.side = to;
            secondItem.speed = EXTRACT_SPEED;
            secondItem.genTimings(firstItem.tickFinished, getPipeLength(secondItem.side));
            addDelayed(secondItem.timeToDest, secondItem);
        }
    }

    // PipeFlow

    /** Exposes {@link IInjectable}/{@link IFlowItems} itself under {@link PipeApi#CAP_INJECTABLE}, and wraps
     * this flow behind a classic {@link IItemHandler} under {@link ForgeCapabilities#ITEM_HANDLER} -- the same
     * vanilla-interop token every vanilla container answers, and the one {@code ItemTransactorHelper} already
     * knows how to fall back to -- so a real hopper (or an already-ported machine such as {@code TileChute}) can
     * push items into this pipe without knowing BuildCraft exists. See the 26.x copy of this method for the same
     * two-token dispatch, adapted to this target's capability shape. */
    @Override
    @Nullable
    @SuppressWarnings("unchecked")
    public <T> T getCapability(Capability<T> capability, @Nullable Direction facing) {
        if (capability == PipeApi.CAP_INJECTABLE) {
            return (T) this;
        } else if (capability == ForgeCapabilities.ITEM_HANDLER && facing != null) {
            return (T) new PipeItemHandler(this, facing);
        } else {
            return super.getCapability(capability, facing);
        }
    }

    @Override
    public boolean canConnect(Direction face, PipeFlow other) {
        return other instanceof IFlowItems;
    }

    @Override
    public boolean canConnect(Direction face, BlockEntity oTile) {
        return ItemTransactorHelper.getTransactor(oTile, face.getOpposite()) != NoSpaceTransactor.INSTANCE;
    }

    @Override
    public void onTick() {
        Level level = pipe.getHolder().getPipeLevel();
        List<TravellingItem> toTick = advance();
        long currentTime = level.getGameTime();

        for (TravellingItem item : toTick) {
            if (item.tickFinished > currentTime) {
                addDelayed((int) (item.tickFinished - currentTime), item);
                continue;
            }
            if (item.isPhantom) {
                postDropCache.add(item.stack);
                continue;
            }
            if (level.isClientSide()) {
                continue;
            }
            if (item.toCenter) {
                onItemReachCenter(item);
            } else {
                onItemReachEnd(item);
            }
        }
    }

    @Override
    public void postPluggableTick() {
        postDropCache.clear();
    }

    private void onItemReachCenter(TravellingItem item) {
        IPipeHolder holder = pipe.getHolder();
        PipeEventItem.ReachCenter reachCenter =
            new PipeEventItem.ReachCenter(holder, this, item.colour, item.stack, item.side);
        holder.fireEvent(reachCenter);
        if (reachCenter.getStack().isEmpty()) {
            return;
        }

        PipeEventItem.SideCheck sideCheck =
            new PipeEventItem.SideCheck(holder, this, reachCenter.colour, reachCenter.from, reachCenter.getStack());
        sideCheck.disallow(reachCenter.from);
        for (Direction face : Direction.values()) {
            if (item.tried.contains(face) || !pipe.isConnected(face)) {
                sideCheck.disallow(face);
            }
        }
        holder.fireEvent(sideCheck);

        List<EnumSet<Direction>> order = sideCheck.getOrder();
        if (order.isEmpty()) {
            PipeEventItem.TryBounce tryBounce =
                new PipeEventItem.TryBounce(holder, this, reachCenter.colour, reachCenter.from, reachCenter.getStack());
            holder.fireEvent(tryBounce);
            if (tryBounce.canBounce) {
                order = ImmutableList.of(EnumSet.of(reachCenter.from));
            } else {
                dropItem(reachCenter.getStack(), item.side.getOpposite(), item.speed);
                return;
            }
        }

        PipeEventItem.ItemEntry entry =
            new PipeEventItem.ItemEntry(reachCenter.colour, reachCenter.getStack(), reachCenter.from);
        PipeEventItem.Split split = new PipeEventItem.Split(holder, this, order, entry);
        holder.fireEvent(split);
        ImmutableList<PipeEventItem.ItemEntry> entries = ImmutableList.copyOf(split.items);

        PipeEventItem.FindDest findDest = new PipeEventItem.FindDest(holder, this, order, entries);
        holder.fireEvent(findDest);

        long now = holder.getPipeLevel().getGameTime();
        for (PipeEventItem.ItemEntry itemEntry : findDest.items) {
            if (itemEntry.stack.isEmpty()) {
                continue;
            }
            PipeEventItem.ModifySpeed modifySpeed = new PipeEventItem.ModifySpeed(holder, this, itemEntry, item.speed);

            final double newSpeed;
            if (holder.fireEvent(modifySpeed)) {
                double target = modifySpeed.targetSpeed;
                double maxDelta = modifySpeed.maxSpeedChange;
                if (item.speed < target) {
                    newSpeed = Math.min(target, item.speed + maxDelta);
                } else if (item.speed > target) {
                    newSpeed = Math.max(target, item.speed - maxDelta);
                } else {
                    newSpeed = item.speed;
                }
            } else if (item.speed > 0.03) {
                newSpeed = Math.max(0.03, item.speed - FALLBACK_SPEED_DELTA);
            } else {
                newSpeed = item.speed;
            }

            List<Direction> destinations = itemEntry.to;
            if (destinations == null || destinations.isEmpty()) {
                destinations = findDest.generateRandomOrder();
            }
            if (destinations.isEmpty()) {
                dropItem(itemEntry.stack, item.side.getOpposite(), newSpeed);
            } else {
                TravellingItem newItem = new TravellingItem(itemEntry.stack);
                newItem.tried.addAll(item.tried);
                newItem.toCenter = false;
                newItem.colour = itemEntry.colour;
                newItem.side = destinations.get(0);
                newItem.speed = newSpeed;
                newItem.genTimings(now, getPipeLength(newItem.side));
                addDelayed(newItem.timeToDest, newItem);
            }
        }
    }

    private void onItemReachEnd(TravellingItem item) {
        IPipeHolder holder = pipe.getHolder();
        PipeEventItem.ReachEnd reachEnd = new PipeEventItem.ReachEnd(holder, this, item.colour, item.stack, item.side);
        holder.fireEvent(reachEnd);
        item.colour = reachEnd.colour;
        item.stack = reachEnd.getStack();
        ItemStack excess = item.stack;
        if (excess.isEmpty()) {
            return;
        }
        if (pipe.isConnected(item.side)) {
            ConnectedType type = pipe.getConnectedType(item.side);
            Direction oppositeSide = item.side.getOpposite();
            if (type == ConnectedType.PIPE) {
                IPipe oPipe = pipe.getConnectedPipe(item.side);
                if (oPipe != null && oPipe.getFlow() instanceof IFlowItems oFlow) {
                    ItemStack before = excess;
                    excess = oFlow.injectItem(excess.copy(), true, oppositeSide, item.colour, item.speed);
                    excess = fireEventEjectIntoPipe(oFlow, item.side, before, excess);
                }
            } else if (type == ConnectedType.TILE) {
                BlockEntity tile = pipe.getConnectedTile(item.side);
                if (tile != null) {
                    IItemTransactor transactor = ItemTransactorHelper.getTransactor(tile, oppositeSide);
                    ItemStack before = excess;
                    excess = transactor.insert(excess.copy(), false, false);
                    excess = fireEventEjectIntoTile(tile, item.side, before, excess);
                }
            }
        }
        if (excess.isEmpty()) {
            postDropCache.add(item.stack);
            return;
        }
        item.tried.add(item.side);
        item.toCenter = true;
        item.stack = excess;
        item.genTimings(holder.getPipeLevel().getGameTime(), getPipeLength(item.side));
        addDelayed(item.timeToDest, item);
    }

    private ItemStack fireEventEjectIntoPipe(IFlowItems oFlow, Direction to, ItemStack before, ItemStack excess) {
        IPipeHolder holder = this.pipe.getHolder();
        return fireEventEjected(holder, new PipeEventItem.Ejected.IntoPipe(holder, this, before, excess, to, oFlow));
    }

    private ItemStack fireEventEjectIntoTile(BlockEntity tile, Direction to, ItemStack before, ItemStack excess) {
        IPipeHolder holder = this.pipe.getHolder();
        return fireEventEjected(holder, new PipeEventItem.Ejected.IntoTile(holder, this, before, excess, to, tile));
    }

    private static ItemStack fireEventEjected(IPipeHolder holder, PipeEventItem.Ejected event) {
        holder.fireEvent(event);
        return event.getExcess();
    }

    private void dropItem(ItemStack stack, Direction motion, double speed) {
        if (stack.isEmpty()) {
            return;
        }

        IPipeHolder holder = pipe.getHolder();
        Level level = holder.getPipeLevel();
        var pos = holder.getPipePos();

        double x = pos.getX() + 0.5 + motion.getStepX() * 0.5;
        double y = pos.getY() + 0.5 + motion.getStepY() * 0.5;
        double z = pos.getZ() + 0.5 + motion.getStepZ() * 0.5;
        double dropSpeed = (speed + 0.01) * 2;
        ItemEntity ent = new ItemEntity(level, x, y, z, stack);
        ent.setDeltaMovement(motion.getStepX() * dropSpeed, motion.getStepY() * dropSpeed, motion.getStepZ() * dropSpeed);

        PipeEventItem.Drop drop = new PipeEventItem.Drop(holder, this, ent);
        holder.fireEvent(drop);
        if (ent.getItem().isEmpty()) {
            return;
        }

        level.addFreshEntity(ent);
    }

    @Override
    public boolean canInjectItems(Direction from) {
        return pipe.isConnected(from);
    }

    @NotNull
    @Override
    public ItemStack injectItem(@NotNull ItemStack stack, boolean doAdd, Direction from, @Nullable DyeColor colour, double speed) {
        if (pipe.getHolder().getPipeLevel().isClientSide()) {
            throw new IllegalStateException("Cannot inject items on the client side!");
        }
        if (!canInjectItems(from)) {
            return stack;
        }
        if (speed < 0.01) {
            speed = 0.01;
        }

        PipeEventItem.TryInsert tryInsert = new PipeEventItem.TryInsert(pipe.getHolder(), this, colour, from, stack);
        pipe.getHolder().fireEvent(tryInsert);
        if (tryInsert.isCanceled() || tryInsert.accepted <= 0) {
            return stack;
        }
        ItemStack toSplit = stack.copy();
        ItemStack toInsert = toSplit.split(tryInsert.accepted);

        if (doAdd) {
            insertItemEvents(toInsert, colour, speed, from);
        }

        return toSplit;
    }

    @Override
    public void insertItemsForce(@NotNull ItemStack stack, Direction from, @Nullable DyeColor colour, double speed) {
        Level level = pipe.getHolder().getPipeLevel();
        if (level.isClientSide()) {
            throw new IllegalStateException("Cannot inject items on the client side!");
        }
        if (stack.isEmpty()) {
            return;
        }
        if (speed < 0.01) {
            speed = 0.01;
        }
        long now = level.getGameTime();
        TravellingItem item = new TravellingItem(stack);
        if (from == null) {
            for (Direction f : Direction.values()) {
                if (!pipe.isConnected(f)) {
                    item.side = f;
                    break;
                }
            }
            if (item.side == null) {
                item.side = Direction.UP;
            }
        } else {
            item.side = from;
        }
        item.toCenter = true;
        item.speed = speed;
        item.colour = colour;
        item.genTimings(now, 0);
        if (from != null) {
            item.tried.add(from);
        }
        addDelayed(item.timeToDest, item);
    }

    /** Used internally to split up manual insertions from controlled extractions. */
    private void insertItemEvents(@NotNull ItemStack toInsert, @Nullable DyeColor colour, double speed, Direction from) {
        IPipeHolder holder = pipe.getHolder();

        PipeEventItem.OnInsert onInsert = new PipeEventItem.OnInsert(holder, this, colour, toInsert, from);
        holder.fireEvent(onInsert);

        if (onInsert.getStack().isEmpty()) {
            return;
        }

        long now = pipe.getHolder().getPipeLevel().getGameTime();

        TravellingItem item = new TravellingItem(toInsert);
        item.side = from;
        item.toCenter = true;
        item.speed = speed;
        item.colour = onInsert.colour;
        item.stack = onInsert.getStack();
        item.genTimings(now, getPipeLength(from));
        item.tried.add(from);
        addItemTryMerge(item);
    }

    private void addItemTryMerge(TravellingItem item) {
        for (List<TravellingItem> bucket : buckets) {
            for (TravellingItem existing : bucket) {
                if (existing.mergeWith(item)) {
                    return;
                }
            }
        }
        addDelayed(item.timeToDest, item);
    }

    public boolean doesContainItems() {
        return getMaxDelay() > 0 || !postDropCache.isEmpty();
    }

    public boolean containsItemMatching(ItemStack filter) {
        if (filter.isEmpty()) {
            return doesContainItems();
        }
        for (List<TravellingItem> bucket : buckets) {
            for (TravellingItem item : bucket) {
                if (StackUtil.matchesStackOrList(filter, item.stack)) {
                    return true;
                }
            }
        }
        for (ItemStack stack : postDropCache) {
            if (StackUtil.matchesStackOrList(filter, stack)) {
                return true;
            }
        }
        return false;
    }

    double getPipeLength(@Nullable Direction side) {
        if (side == null) {
            return 0;
        }
        if (pipe.isConnected(side)) {
            if (pipe.getConnectedType(side) == ConnectedType.TILE) {
                return 0.5 + 0.25;
            }
            return 0.5;
        } else {
            return 0.25;
        }
    }

    // Delay-bucket queue -- see the 26.x copy of this class's own javadoc.

    private void addDelayed(int delay, TravellingItem item) {
        if (delay < 0) {
            delay = 0;
        }
        while (buckets.size() < delay + 1) {
            buckets.add(new ArrayList<>());
        }
        buckets.get(delay).add(item);
    }

    private List<TravellingItem> advance() {
        if (buckets.isEmpty()) {
            return List.of();
        }
        return buckets.remove(0);
    }

    private int getMaxDelay() {
        return buckets.size();
    }

    /** Wraps a {@link PipeFlowItems} behind the classic {@link IItemHandler} shape -- see {@link #getCapability}'s
     * own javadoc for why this exists. There is exactly one virtual slot; every insert forwards straight to
     * {@link #injectItem}. Extraction is not supported, matching the 26.x copy of this class's own adapter. */
    private static final class PipeItemHandler implements IItemHandler {
        private final PipeFlowItems flow;
        private final Direction side;

        PipeItemHandler(PipeFlowItems flow, Direction side) {
            this.flow = flow;
            this.side = side;
        }

        @Override
        public int getSlots() {
            return 1;
        }

        @NotNull
        @Override
        public ItemStack getStackInSlot(int slot) {
            return ItemStack.EMPTY;
        }

        @NotNull
        @Override
        public ItemStack insertItem(int slot, @NotNull ItemStack stack, boolean simulate) {
            if (slot != 0 || stack.isEmpty()) {
                return stack;
            }
            return flow.injectItem(stack, !simulate, side, null, -1);
        }

        @NotNull
        @Override
        public ItemStack extractItem(int slot, int amount, boolean simulate) {
            return ItemStack.EMPTY;
        }

        @Override
        public int getSlotLimit(int slot) {
            return 64;
        }

        @Override
        public boolean isItemValid(int slot, @NotNull ItemStack stack) {
            return true;
        }
    }
}
