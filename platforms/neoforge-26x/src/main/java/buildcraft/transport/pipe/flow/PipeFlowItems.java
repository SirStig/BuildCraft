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

import net.minecraft.core.BlockPos;
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

import net.neoforged.neoforge.capabilities.BlockCapability;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.resource.ResourceStack;
import net.neoforged.neoforge.transfer.transaction.SnapshotJournal;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;

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
 * pipe's internal space and is deposited at the other end -- into another connected pipe segment, or into a real
 * inventory once it reaches the end of the run. A close port of 1.12.2's own {@code PipeFlowItems}, implementing
 * the already-ported {@link IFlowItems} (itself {@code extends} the already-ported {@link IInjectable}).
 *
 * <p><b>The single biggest genuine platform divergence in this whole batch lives here.</b> 1.12.2's
 * {@code injectItem(ItemStack, boolean doAdd, ...)} took a whole stack and a simulate flag and handed back the
 * leftover stack. On 26.x {@link IInjectable#injectItem} (and so {@link #injectItem}) is reshaped around the
 * transfer API instead: an {@link ItemResource} plus a plain {@code int} count, and a {@link TransactionContext}
 * that this method must never commit itself -- the caller (typically a vanilla hopper's own transaction, reached
 * through {@code BlockPipeHolder}'s {@code Capabilities.Item.BLOCK} registration) decides whether the effect
 * sticks. That means this method's own mutation of {@link #buckets} has to be genuinely transaction-safe: if the
 * caller's transaction is rolled back after this method returns a non-zero accepted count, the item must not
 * silently remain queued here while the source inventory also gets its stack back. {@link #journal} is a small,
 * self-contained {@link SnapshotJournal} over {@link #buckets} for exactly this -- the same mechanism NeoForge's
 * own {@code ItemStacksResourceHandler} uses internally for its slot array, just hand-rolled here because this
 * flow's own state is a moving delay-bucket queue, not a fixed slot array. Every mutating entry point
 * ({@link #injectItem}, {@link #tryExtractItems} when not simulating) calls {@code journal.updateSnapshots(
 * transaction)} immediately before mutating -- see each method's own body.
 *
 * <p>1.20.1's copy of this class looks far closer to the original: that target's {@code IFlowItems}/
 * {@code IInjectable} keep 1.12.2's own {@code ItemStack}/{@code boolean doAdd} shape verbatim (1.20.1 has no
 * transfer API at all), so no journal/transaction machinery is needed there -- {@code insertItemEvents} mutates
 * {@link #buckets} directly, exactly like 1.12.2 did.
 *
 * <p><b>Deliberately dropped, all for the same "no client rendering in this batch" reason already established
 * across this whole module:</b> the network constructor and {@code readPayload}/{@code writePayload}/
 * {@code sendItemDataToClient} (1.12.2's item-creation packet, routed through the unported
 * {@code PipeItemMessageQueue}/{@code BuildCraftObjectCaches}), and the client-only {@code getAllItemsForRender}.
 * Persistence goes through {@link #writeToNbt(HolderLookup.Provider)}/the NBT constructor only.
 *
 * <p><b>Also dropped: {@code addTriggers}</b> (a {@code @PipeEventHandler} registering
 * {@code BCTransportStatements.TRIGGER_ITEMS_TRAVERSING}) -- gates/statements are out of this batch's scope
 * entirely (see the module-level scope notes), and {@code BCTransportStatements} itself is not ported.
 *
 * <p>The delay-bucket queue ({@link #buckets}) is a small, self-contained reimplementation of 1.12.2's own
 * {@code buildcraft.lib.misc.data.DelayedList<E>} rather than a port of that class under its own name: nothing
 * else in this batch's scope needs a generic delayed queue, and {@code DelayedList} was not itself part of this
 * batch's file list, so its (tiny, Minecraft-free) logic is folded directly into this file instead of adding a
 * new public {@code buildcraft.lib} utility class this task never asked for.
 */
public final class PipeFlowItems extends PipeFlow implements IFlowItems {
    private static final double EXTRACT_SPEED = 0.08;
    /** Fallback speed delta for an item leaving the centre when nothing (no behaviour) modified its speed --
     * borrowed from 1.12.2's own {@code PipeBehaviourStone.SPEED_DELTA} constant, which is not ported in this
     * batch (only cobblestone's own behaviour is); the numeric fallback is kept so an item entering a pipe with
     * no {@code ModifySpeed} handler at all still settles towards a sane speed instead of never slowing down. */
    private static final double FALLBACK_SPEED_DELTA = 0.008;

    /** {@code buckets.get(n)} holds every item that will be advanced after {@code n} more calls to
     * {@link #onTick()} -- the delay-bucket queue described in this class's own javadoc. */
    private final List<List<TravellingItem>> buckets = new ArrayList<>();
    private final List<ItemStack> postDropCache = new ArrayList<>();

    private final SnapshotJournal<List<List<TravellingItem>>> journal = new SnapshotJournal<>() {
        @Override
        protected List<List<TravellingItem>> createSnapshot() {
            List<List<TravellingItem>> copy = new ArrayList<>(buckets.size());
            for (List<TravellingItem> bucket : buckets) {
                copy.add(new ArrayList<>(bucket));
            }
            return copy;
        }

        @Override
        protected void revertToSnapshot(List<List<TravellingItem>> snapshot) {
            buckets.clear();
            buckets.addAll(snapshot);
        }
    };

    public PipeFlowItems(IPipe pipe) {
        super(pipe);
    }

    public PipeFlowItems(IPipe pipe, CompoundTag nbt, HolderLookup.Provider registries) {
        super(pipe, nbt, registries);
        // getPipeLevel() is genuinely null here on a real disk-loaded chunk (confirmed live: BlockEntity#load runs
        // before BlockEntity#setLevel during vanilla chunk deserialization, not after, so this constructor -- run
        // from TilePipeHolder's own loadAdditional -- has no level yet). TravellingItem's own tickStarted/
        // tickFinished are stored as *relative* offsets from whatever tickNow writeToNbt used, so any consistent
        // placeholder works here without corrupting the pipe's own material/connection data the way an uncaught
        // NullPointerException did (every pipe on every platform silently lost its Pipe entirely on world reload).
        // The only cost of the placeholder is that an item genuinely mid-transit at save time reads as "already
        // arrived" the instant this tile starts ticking for real, rather than finishing its remaining travel time.
        long tickNow = pipe.getHolder().getPipeLevel() != null ? pipe.getHolder().getPipeLevel().getGameTime() : 0;
        for (Tag itemTag : nbt.getListOrEmpty("items")) {
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
        // Same null guard as the NBT-loading constructor above, for the symmetric (unconfirmed but plausible) case
        // of a tool that copies a placed tile's NBT into an ItemStack without ever attaching it to a level.
        long tickNow = pipe.getHolder().getPipeLevel() != null ? pipe.getHolder().getPipeLevel().getGameTime() : 0;
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
        Level level = pipe.getHolder().getPipeLevel();
        if (level.isClientSide()) {
            throw new IllegalStateException("Cannot extract items on the client side!");
        }
        if (from == null || count <= 0) {
            return 0;
        }

        BlockPos neighbourPos = pipe.getHolder().getPipePos().relative(from);
        IItemTransactor trans = ItemTransactorHelper.getTransactor(level, neighbourPos, from.getOpposite());

        try (Transaction transaction = Transaction.openRoot()) {
            ResourceStack<ItemResource> possible;
            try (Transaction peek = Transaction.open(transaction)) {
                // Never committed: this is only a peek at what the neighbour could give up.
                possible = trans.extract(filter, 1, count, peek);
            }
            if (possible == null || possible.isEmpty()) {
                return 0;
            }

            ItemStack possibleStack = possible.resource().toStack(possible.amount());
            IPipeHolder holder = pipe.getHolder();
            PipeEventItem.TryInsert tryInsert = new PipeEventItem.TryInsert(holder, this, colour, from, possibleStack);
            holder.fireEvent(tryInsert);
            if (tryInsert.isCanceled() || tryInsert.accepted <= 0) {
                return 0;
            }
            int accepted = Math.min(possible.amount(), tryInsert.accepted);

            ItemResource resource = possible.resource();
            ResourceStack<ItemResource> extracted = trans.extract(resource::matches, accepted, accepted, transaction);
            if (extracted == null || extracted.amount() != accepted) {
                throw new IllegalStateException(
                    "The transactor " + trans + " didn't respect the extraction amount it just offered!"
                );
            }

            if (!simulate) {
                journal.updateSnapshots(transaction);
                insertItemEvents(extracted.resource().toStack(extracted.amount()), colour, EXTRACT_SPEED, from);
                transaction.commit();
            }
            return accepted;
        }
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

    /** Exposes {@link IInjectable}/{@link IFlowItems} itself under {@link PipeApi#CAP_INJECTABLE} (BuildCraft's
     * own pipe-to-pipe token, for any other pipe mod that wants it directly), and wraps this flow behind a plain
     * {@code ResourceHandler<ItemResource>} under {@link Capabilities#Item}{@code .BLOCK} -- NeoForge's own
     * vanilla-interop token, the same one every vanilla container answers -- so a real hopper (or an already-
     * ported machine such as {@code TileChute}, which reaches every neighbour through exactly this token via
     * {@code ItemTransactorHelper}) can push items into this pipe without knowing BuildCraft exists. This is a
     * direct structural port of 1.12.2's own {@code PipeFlowItems#getCapability}, which did the same two-token
     * dispatch against {@code PipeApi.CAP_INJECTABLE}/{@code CapUtil.CAP_ITEM_TRANSACTOR}. */
    @Override
    @Nullable
    @SuppressWarnings("unchecked")
    public <T> T getCapability(BlockCapability<T, Direction> capability, @Nullable Direction facing) {
        if (capability == PipeApi.CAP_INJECTABLE) {
            return (T) this;
        } else if (capability == Capabilities.Item.BLOCK && facing != null) {
            return (T) new PipeItemResourceHandler(this, facing);
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
        Level level = pipe.getHolder().getPipeLevel();
        BlockPos pos = oTile.getBlockPos();
        return ItemTransactorHelper.getTransactor(level, pos, face.getOpposite()) != NoSpaceTransactor.INSTANCE;
    }

    @Override
    public void onTick() {
        Level level = pipe.getHolder().getPipeLevel();
        List<TravellingItem> toTick = advance();
        long currentTime = level.getGameTime();

        for (TravellingItem item : toTick) {
            if (item.tickFinished > currentTime) {
                // Can happen if something ticks this tile multiple times in a single real tick
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
                    int accepted;
                    try (Transaction transaction = Transaction.openRoot()) {
                        accepted = oFlow.injectItem(
                            ItemResource.of(excess), excess.getCount(), oppositeSide, item.colour, item.speed,
                            transaction
                        );
                        if (accepted > 0) {
                            transaction.commit();
                        }
                    }
                    excess = accepted > 0 ? excess.copyWithCount(excess.getCount() - accepted) : excess;
                    excess = fireEventEjectIntoPipe(oFlow, item.side, before, excess);
                }
            } else if (type == ConnectedType.TILE) {
                BlockEntity tile = pipe.getConnectedTile(item.side);
                if (tile != null) {
                    Level level = holder.getPipeLevel();
                    BlockPos neighbourPos = holder.getPipePos().relative(item.side);
                    IItemTransactor transactor = ItemTransactorHelper.getTransactor(level, neighbourPos, oppositeSide);
                    ItemStack before = excess;
                    int inserted;
                    try (Transaction transaction = Transaction.openRoot()) {
                        inserted = transactor.insert(ItemResource.of(excess), excess.getCount(), false, transaction);
                        if (inserted > 0) {
                            transaction.commit();
                        }
                    }
                    excess = inserted > 0 ? excess.copyWithCount(excess.getCount() - inserted) : excess;
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
        BlockPos pos = holder.getPipePos();

        double x = pos.getX() + 0.5 + motion.getStepX() * 0.5;
        double y = pos.getY() + 0.5 + motion.getStepY() * 0.5;
        double z = pos.getZ() + 0.5 + motion.getStepZ() * 0.5;
        double dropSpeed = (speed + 0.01) * 2;
        ItemEntity ent = new ItemEntity(level, x, y, z, stack);
        ent.setDeltaMovement(motion.getStepX() * dropSpeed, motion.getStepY() * dropSpeed, motion.getStepZ() * dropSpeed);

        PipeEventItem.Drop drop = new PipeEventItem.Drop(holder, this, ent);
        holder.fireEvent(drop);
        if (ent.getItem().isEmpty() || !ent.isAlive() && ent.getItem().isEmpty()) {
            return;
        }

        level.addFreshEntity(ent);
    }

    @Override
    public boolean canInjectItems(Direction from) {
        return pipe.isConnected(from);
    }

    @Override
    public int injectItem(
        ItemResource resource, int count, Direction from, @Nullable DyeColor color, double speed,
        TransactionContext transaction
    ) {
        if (pipe.getHolder().getPipeLevel().isClientSide()) {
            throw new IllegalStateException("Cannot inject items on the client side!");
        }
        if (resource.isEmpty() || count <= 0 || !canInjectItems(from)) {
            return 0;
        }
        if (speed < 0.01) {
            speed = 0.01;
        }

        ItemStack stack = resource.toStack(count);
        PipeEventItem.TryInsert tryInsert = new PipeEventItem.TryInsert(pipe.getHolder(), this, color, from, stack);
        pipe.getHolder().fireEvent(tryInsert);
        if (tryInsert.isCanceled() || tryInsert.accepted <= 0) {
            return 0;
        }
        int accepted = Math.min(count, tryInsert.accepted);

        journal.updateSnapshots(transaction);
        insertItemEvents(resource.toStack(accepted), color, speed, from);

        return accepted;
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
            // Find a reasonable alternative (as it's not allowed to be null)
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
        // Explicitly don't send this item to the client: there's no renderer to draw it, and it needs to
        // travel 0 distance anyway.
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
        // Note that this counts all items (including phantom items, which is fine). This only works because
        // this list is only expanded to add elements, and elements are only removed in advance().
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
                // Tiny distance for fully pushing items in.
                return 0.5 + 0.25;
            }
            return 0.5;
        } else {
            return 0.25;
        }
    }

    // Delay-bucket queue -- see this class's own javadoc.

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

    /** Wraps a {@link PipeFlowItems} behind the vanilla-interop {@code ResourceHandler<ItemResource>} shape --
     * see {@link #getCapability}'s own javadoc for why this exists. There is exactly one virtual slot (this flow
     * has no fixed slots of its own; it is a moving queue), and every insert forwards straight to
     * {@link #injectItem}, which does the real accept/reject decision and is itself transaction-safe (see this
     * class's own javadoc for {@link #journal}). Extraction is not supported -- nothing in this batch's scope
     * needs another mod to pull items back out of a pipe from outside it. */
    private static final class PipeItemResourceHandler implements ResourceHandler<ItemResource> {
        private final PipeFlowItems flow;
        private final Direction side;

        PipeItemResourceHandler(PipeFlowItems flow, Direction side) {
            this.flow = flow;
            this.side = side;
        }

        @Override
        public int size() {
            return 1;
        }

        @Override
        public ItemResource getResource(int slot) {
            return ItemResource.EMPTY;
        }

        @Override
        public long getAmountAsLong(int slot) {
            return 0;
        }

        @Override
        public long getCapacityAsLong(int slot, ItemResource resource) {
            return resource.isEmpty() ? 64 : resource.getMaxStackSize();
        }

        @Override
        public boolean isValid(int slot, ItemResource resource) {
            return slot == 0 && !resource.isEmpty();
        }

        @Override
        public int insert(int slot, ItemResource resource, int amount, TransactionContext transaction) {
            if (slot != 0) {
                return 0;
            }
            return flow.injectItem(resource, amount, side, null, -1, transaction);
        }

        @Override
        public int extract(int slot, ItemResource resource, int amount, TransactionContext transaction) {
            return 0;
        }
    }
}
