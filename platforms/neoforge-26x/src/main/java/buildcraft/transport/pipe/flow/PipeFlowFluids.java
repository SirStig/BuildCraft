/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * Copyright (c) 2026 Joshua Kac -- NeoForge port (BuildCraft 10)
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL
 * was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.transport.pipe.flow;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.jetbrains.annotations.Nullable;

import net.minecraft.ChatFormatting;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

import net.neoforged.neoforge.capabilities.BlockCapability;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidType;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.resource.ResourceStack;
import net.neoforged.neoforge.transfer.transaction.SnapshotJournal;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;

import buildcraft.api.core.BCLog;
import buildcraft.api.core.EnumPipePart;
import buildcraft.api.core.FluidFilters;
import buildcraft.api.core.IFluidFilter;
import buildcraft.api.tiles.IDebuggable;
import buildcraft.api.transport.pipe.IFlowFluid;
import buildcraft.api.transport.pipe.IPipe;
import buildcraft.api.transport.pipe.IPipeHolder.PipeMessageReceiver;
import buildcraft.api.transport.pipe.PipeApi;
import buildcraft.api.transport.pipe.PipeApi.FluidTransferInfo;
import buildcraft.api.transport.pipe.PipeEventFluid;
import buildcraft.api.transport.pipe.PipeEventFluid.OnMoveToCentre;
import buildcraft.api.transport.pipe.PipeEventFluid.PreMoveToCentre;
import buildcraft.api.transport.pipe.PipeFlow;

import buildcraft.lib.misc.MathUtil;

/**
 * The fluid-moving simulation: a pipe split into seven sections (six sides plus the centre), each holding an amount
 * of the pipe's one fluid, with fluid moving side -> centre -> side -> next block a little each tick. A close port
 * of 1.12.2's own {@code PipeFlowFluids}; the movement logic ({@link #onTick()}, {@link #moveFromPipe()},
 * {@link #moveFromCenter()}, {@link #moveToCenter()}, {@link Section}) is the original's, line for line.
 *
 * <p><b>The section state machine.</b> Each section has a {@code ticksInDirection} counter: negative means fluid
 * recently came <em>in</em> through it (it may only feed the centre), positive means fluid recently went
 * <em>out</em> through it (it may only push onwards), zero means either. Every move re-arms the counter to
 * {@code -60}/{@code +60} and it decays by one per tick, so a side that stops receiving eventually becomes free to
 * output again -- this is what lets fluid move in, out, and back. Fluid entering a section is also delayed:
 * {@code incoming[]} is a ring buffer of the amounts that arrived in each of the last {@code currentDelay} ticks,
 * and that fluid cannot leave again until its slot comes round ({@link Section#getMaxDrained()}).
 *
 * <p><b>26.x: the transfer API, transaction-safe like {@code PipeFlowItems}.</b> {@link IFlowFluid} on this target
 * takes a {@link TransactionContext} in place of 1.12.2's {@code boolean simulate}, and each section is exposed to
 * neighbours as a {@code ResourceHandler<FluidResource>} under {@link Capabilities.Fluid#BLOCK}. A caller's
 * transaction may be rolled back after a section accepted fluid, so every mutation reachable from outside
 * ({@link Section#insert}, {@link #tryExtractFluid}, {@link #insertFluidsForce}, {@link #extractFluidsForce}) calls
 * {@code journal.updateSnapshots(transaction)} first; {@link #journal} snapshots the whole flow (fluid, delay, and
 * every section's amount/direction/ring buffer), which is small. The flow's own tick opens its own root
 * transaction for each push into a neighbour and only mutates its sections after that transaction committed.
 * {@code tryExtractFluidAdv} collapses into {@link #tryExtractFluid}, as the 26.x {@link IFlowFluid} javadoc
 * explains; filtering goes through {@link FluidFilters}. The current fluid is held as a {@link FluidResource}
 * (fluid + components, no amount) rather than 1.12.2's amount-carrying {@code FluidStack}, whose amount was
 * meaningless; the {@link PipeEventFluid} events, whose API still takes a {@code FluidStack}, get one built per
 * event.
 *
 * <p><b>Client sync.</b> A client-side pipe never ticks ({@code BlockPipeHolder#getTicker} is server-only), so the
 * client's copy of this flow is exactly what the last whole-tile NBT sync wrote. 1.12.2 sent a dedicated
 * {@code NET_FLUID_AMOUNTS} payload, throttled by a {@code SafeTimeTracker} to {@code BCCoreConfig.networkUpdateRate}
 * (10 ticks), whenever a section's amount or direction differed from what was last sent. This keeps exactly that
 * policy -- compare against the last-sent values each tick, sync at most once per {@link #NET_UPDATE_INTERVAL}
 * ticks -- but sends through {@code scheduleNetworkUpdate(FLOW)} (a whole-tile resync), the same route
 * {@code PipeFlowItems} uses. A pipe whose contents are not changing never syncs, and one that is flowing costs at
 * most two tile updates per second. The client-side smoothing ({@code clientAmountLast}/{@code clientAmountThis},
 * the animated flow offsets) is dropped: each sync rebuilds the client's {@code Pipe} from scratch, so there is no
 * surviving client state to interpolate against; the renderer draws the synced amounts directly.
 *
 * <p><b>Dropped:</b> {@code addDrops} (fluid dropped as a fragile fluid shard, an unported {@code BCCoreItems}
 * item -- a broken pipe's fluid is simply lost), {@code addTriggers} (gates are not ported), and the
 * {@code writePayload}/{@code readPayload} pair (see above).
 */
public final class PipeFlowFluids extends PipeFlow implements IFlowFluid, IDebuggable {

    private static final int DIRECTION_COOLDOWN = 60;
    private static final int COOLDOWN_INPUT = -DIRECTION_COOLDOWN;
    private static final int COOLDOWN_OUTPUT = DIRECTION_COOLDOWN;

    /** 1.12.2's {@code BCCoreConfig.networkUpdateRate} default -- the minimum gap between two syncs. */
    public static final int NET_UPDATE_INTERVAL = 10;

    private final FluidTransferInfo fluidTransferInfo = PipeApi.getFluidTransferInfo(pipe.getDefinition());

    /** 1.12.2's own formula (its "TEMP!" comment included): a bucket, or ten ticks' transfer if that is larger. */
    public final int capacity = Math.max(FluidType.BUCKET_VOLUME, fluidTransferInfo.transferPerTick * 10);

    private final Map<EnumPipePart, Section> sections = new EnumMap<>(EnumPipePart.class);
    private FluidResource currentFluid = FluidResource.EMPTY;
    private int currentDelay;

    // Server-only, never persisted: what the client was last told (see the class javadoc).
    private FluidResource lastSentFluid = FluidResource.EMPTY;
    private long lastSyncTick = Long.MIN_VALUE;

    private final SnapshotJournal<FlowSnapshot> journal = new SnapshotJournal<>() {
        @Override
        protected FlowSnapshot createSnapshot() {
            return FlowSnapshot.of(PipeFlowFluids.this);
        }

        @Override
        protected void revertToSnapshot(FlowSnapshot snapshot) {
            snapshot.restore(PipeFlowFluids.this);
        }
    };

    public PipeFlowFluids(IPipe pipe) {
        super(pipe);
        for (EnumPipePart part : EnumPipePart.VALUES) {
            sections.put(part, new Section(part));
        }
        setFluid(FluidResource.EMPTY);
    }

    /** Needs no level (unlike {@code PipeFlowItems}' timing), so the null-level-during-chunk-load case that class
     * guards against cannot arise here. */
    public PipeFlowFluids(IPipe pipe, CompoundTag nbt, HolderLookup.Provider registries) {
        super(pipe, nbt, registries);
        for (EnumPipePart part : EnumPipePart.VALUES) {
            sections.put(part, new Section(part));
        }
        Tag fluidTag = nbt.get("fluid");
        FluidResource fluid = fluidTag == null ? FluidResource.EMPTY : FluidResource.CODEC
            .parse(registries.createSerializationContext(NbtOps.INSTANCE), fluidTag)
            .result()
            .orElse(FluidResource.EMPTY);
        setFluid(fluid);
        if (!fluid.isEmpty()) {
            for (EnumPipePart part : EnumPipePart.VALUES) {
                nbt.getCompound("tank[" + part.getIndex() + "]").ifPresent(sections.get(part)::readFromNbt);
            }
        }
    }

    /** Same keys as 1.12.2 ({@code fluid}, and {@code tank[0..6]} indexed by {@link EnumPipePart#getIndex()}, 6 being
     * the centre) -- including the original's misleading {@code capacity} key, which holds a section's current
     * <em>amount</em>. {@code lastSentAmount} is not written: it is transient sync state here. */
    @Override
    public CompoundTag writeToNbt(HolderLookup.Provider registries) {
        CompoundTag nbt = super.writeToNbt(registries);
        if (!currentFluid.isEmpty()) {
            FluidResource.CODEC
                .encodeStart(registries.createSerializationContext(NbtOps.INSTANCE), currentFluid)
                .result()
                .ifPresent(tag -> nbt.put("fluid", tag));
            for (EnumPipePart part : EnumPipePart.VALUES) {
                CompoundTag subTag = new CompoundTag();
                sections.get(part).writeToNbt(subTag);
                nbt.put("tank[" + part.getIndex() + "]", subTag);
            }
        }
        return nbt;
    }

    @Override
    public boolean canConnect(Direction face, PipeFlow other) {
        return other instanceof IFlowFluid;
    }

    /** 1.12.2: {@code oTile.hasCapability(CAP_FLUIDS, face.getOpposite())}. */
    @Override
    public boolean canConnect(Direction face, BlockEntity oTile) {
        Level level = pipe.getHolder().getPipeLevel();
        return level.getCapability(Capabilities.Fluid.BLOCK, oTile.getBlockPos(), face.getOpposite()) != null;
    }

    /** One {@link Section} per face under {@link Capabilities.Fluid#BLOCK}; none for a null side (1.12.2 handed
     * out the centre section there, but its {@code fill} then refused everything, since {@code isConnected(null)}
     * is always false). */
    @Override
    @Nullable
    @SuppressWarnings("unchecked")
    public <T> T getCapability(BlockCapability<T, Direction> capability, @Nullable Direction facing) {
        if (capability == Capabilities.Fluid.BLOCK && facing != null) {
            return (T) sections.get(EnumPipePart.fromFacing(facing));
        }
        return super.getCapability(capability, facing);
    }

    public boolean doesContainFluid() {
        for (EnumPipePart part : EnumPipePart.VALUES) {
            if (sections.get(part).amount > 0) {
                return true;
            }
        }
        return false;
    }

    // IFlowFluid

    @Override
    @Nullable
    public ResourceStack<FluidResource> tryExtractFluid(
        int millibuckets, Direction from, @Nullable IFluidFilter filter, TransactionContext transaction
    ) {
        if (from == null || millibuckets <= 0) {
            return null;
        }
        ResourceHandler<FluidResource> fluidHandler =
            pipe.getHolder().getCapabilityFromPipe(from, Capabilities.Fluid.BLOCK);
        if (fluidHandler == null) {
            return null;
        }
        Section section = sections.get(EnumPipePart.fromFacing(from));
        Section middle = sections.get(EnumPipePart.CENTER);
        millibuckets = Math.min(millibuckets, capacity * 2 - section.amount - middle.amount);
        if (millibuckets <= 0) {
            return null;
        }
        // A pipe holding a fluid only takes more of the same one, as 1.12.2's extractors did by draining against
        // currentFluid whenever it was set.
        IFluidFilter effective = filter == null ? fluid -> true : filter;
        if (!currentFluid.isEmpty()) {
            FluidResource current = currentFluid;
            effective = effective.and(current::equals);
        }
        FluidResource found = FluidFilters.findExtractable(fluidHandler, effective, millibuckets, transaction);
        if (found == null) {
            return null;
        }
        int extracted = fluidHandler.extract(found, millibuckets, transaction);
        if (extracted <= 0) {
            return null;
        }
        journal.updateSnapshots(transaction);
        if (currentFluid.isEmpty()) {
            setFluid(found);
        }
        int reallyFilled = section.fillInternal(extracted, true);
        int leftOver = extracted - reallyFilled;
        reallyFilled += middle.fillInternal(leftOver, true);
        section.ticksInDirection = COOLDOWN_INPUT;
        if (reallyFilled != extracted) {
            BCLog.logger.warn(
                "[tryExtractFluid] Filled " + reallyFilled + " != extracted " + extracted + " (handler = "
                    + fluidHandler.getClass() + ") @" + pipe.getHolder().getPipePos()
            );
        }
        return new ResourceStack<>(found, extracted);
    }

    @Override
    public int insertFluidsForce(
        FluidResource fluid, int amount, @Nullable Direction from, TransactionContext transaction
    ) {
        Section s = sections.get(EnumPipePart.CENTER);
        if (fluid.isEmpty() || amount <= 0) {
            return 0;
        }
        if (!currentFluid.isEmpty() && !currentFluid.equals(fluid)) {
            return 0;
        }
        if (s.fill(amount, false) <= 0) {
            return 0;
        }
        // (Past this point the fill below cannot come back 0: an empty pipe's sections were already reset by
        // setFluid(EMPTY), so setFluid does not change what fill(amount, false) just answered.)
        journal.updateSnapshots(transaction);
        if (currentFluid.isEmpty()) {
            setFluid(fluid);
        }
        int filled = s.fill(amount, true);
        if (from != null) {
            sections.get(EnumPipePart.fromFacing(from)).ticksInDirection = COOLDOWN_INPUT;
        }
        return filled;
    }

    @Override
    @Nullable
    public ResourceStack<FluidResource> extractFluidsForce(
        int min, int max, @Nullable Direction section, TransactionContext transaction
    ) {
        if (min > max) {
            throw new IllegalArgumentException("Minimum (" + min + ") > maximum (" + max + ")");
        }
        if (max < 0 || currentFluid.isEmpty()) {
            return null;
        }
        Section s = sections.get(EnumPipePart.fromFacing(section));
        if (s.amount < min) {
            return null;
        }
        int amount = MathUtil.clamp(s.amount, min, max);
        FluidResource fluid = currentFluid;
        journal.updateSnapshots(transaction);
        s.amount -= amount;
        if (s.amount == 0) {
            boolean isEmpty = true;
            for (Section s2 : sections.values()) {
                isEmpty &= s2.amount == 0;
            }
            if (isEmpty) {
                setFluid(FluidResource.EMPTY);
            }
        }
        return new ResourceStack<>(fluid, amount);
    }

    // IDebuggable

    @Override
    public void getDebugInfo(List<String> left, List<String> right, @Nullable Direction side) {
        left.add(" - FluidType = " + (currentFluid.isEmpty() ? "empty" : currentFluid.getHoverName().getString()));
        for (EnumPipePart part : EnumPipePart.VALUES) {
            Section section = sections.get(part);
            String amount = (section.amount > 0 ? ChatFormatting.GREEN.toString() : "") + section.amount
                + ChatFormatting.RESET + "mB";
            left.add(
                " - " + (part.face == null ? "center" : part.face.getSerializedName()) + " = " + amount + " "
                    + section.getCurrentDirection() + " (" + section.ticksInDirection + ") "
                    + Arrays.toString(section.incoming)
            );
        }
    }

    // Rendering

    /** The fluid to draw, or {@link FluidResource#EMPTY}. On the client this is what the last sync wrote. */
    public FluidResource getFluidForRender() {
        return currentFluid;
    }

    /** Section amounts indexed by {@link EnumPipePart#getIndex()} (6 = centre). 1.12.2 interpolated these between
     * syncs; see the class javadoc for why this reads the synced values directly. */
    public int[] getAmountsForRender() {
        int[] arr = new int[7];
        for (EnumPipePart part : EnumPipePart.VALUES) {
            arr[part.getIndex()] = sections.get(part).amount;
        }
        return arr;
    }

    // Internal logic

    /** 1.12.2 left the viscosity-scaled delay commented out; so does this port -- the delay is the pipe's own. */
    private void setFluid(FluidResource fluid) {
        currentFluid = fluid;
        currentDelay = (int) fluidTransferInfo.transferDelayMultiplier;
        for (Section section : sections.values()) {
            section.incoming = new int[currentDelay];
            section.incomingTotalCache = 0;
            section.currentTime = 0;
            section.ticksInDirection = 0;
        }
    }

    @Override
    public void onTick() {
        Level level = pipe.getHolder().getPipeLevel();
        if (level.isClientSide()) {
            return;
        }

        if (!currentFluid.isEmpty()) {
            int totalFluid = 0;
            boolean canOutput = false;

            for (EnumPipePart part : EnumPipePart.VALUES) {
                Section section = sections.get(part);
                section.currentTime = (section.currentTime + 1) % currentDelay;
                section.advanceForMovement();
                totalFluid += section.amount;
                if (section.getCurrentDirection().canOutput()) {
                    canOutput = true;
                }
            }
            if (totalFluid == 0) {
                setFluid(FluidResource.EMPTY);
            } else {
                // Fluid movement is split into 3 parts
                // - move from pipe (to other tiles)
                // - move from center (to sides)
                // - move into center (from sides)
                if (canOutput) {
                    moveFromPipe();
                }
                moveFromCenter();
                moveToCenter();
            }

            // tick cooldowns
            for (EnumPipePart part : EnumPipePart.VALUES) {
                Section section = sections.get(part);
                if (section.ticksInDirection > 0) {
                    section.ticksInDirection--;
                } else if (section.ticksInDirection < 0) {
                    section.ticksInDirection++;
                }
            }
        }

        boolean send = !currentFluid.equals(lastSentFluid);
        for (EnumPipePart part : EnumPipePart.VALUES) {
            Section section = sections.get(part);
            if (section.amount != section.lastSentAmount
                || section.lastSentDirection != Dir.get(section.ticksInDirection)) {
                send = true;
                break;
            }
        }
        long now = level.getGameTime();
        if (send && now - lastSyncTick >= NET_UPDATE_INTERVAL) {
            lastSyncTick = now;
            lastSentFluid = currentFluid;
            for (Section section : sections.values()) {
                section.lastSentAmount = section.amount;
                section.lastSentDirection = Dir.get(section.ticksInDirection);
            }
            pipe.getHolder().scheduleNetworkUpdate(PipeMessageReceiver.FLOW);
        }
    }

    private void moveFromPipe() {
        for (EnumPipePart part : EnumPipePart.FACES) {
            Section section = sections.get(part);
            if (section.getCurrentDirection().canOutput()) {
                int maxDrain = section.drainInternal(fluidTransferInfo.transferPerTick, false);
                if (maxDrain <= 0) {
                    continue;
                }
                PipeEventFluid.SideCheck sideCheck =
                    new PipeEventFluid.SideCheck(pipe.getHolder(), this, currentFluid.toStack(maxDrain));
                sideCheck.disallowAllExcept(part.face);
                pipe.getHolder().fireEvent(sideCheck);
                if (sideCheck.getOrder().size() == 1) {
                    ResourceHandler<FluidResource> fluidHandler =
                        pipe.getHolder().getCapabilityFromPipe(part.face, Capabilities.Fluid.BLOCK);
                    if (fluidHandler == null) {
                        continue;
                    }
                    int filled;
                    try (Transaction transaction = Transaction.openRoot()) {
                        filled = fluidHandler.insert(currentFluid, maxDrain, transaction);
                        if (filled > 0) {
                            transaction.commit();
                        }
                    }
                    if (filled > 0) {
                        section.drainInternal(filled, true);
                        section.ticksInDirection = COOLDOWN_OUTPUT;
                    }
                }
            }
        }
    }

    private void moveFromCenter() {
        Section center = sections.get(EnumPipePart.CENTER);
        // Split liquids moving to output equally based on flowrate, how much each side can accept and available
        // liquid
        int totalAvailable = center.getMaxDrained();
        if (totalAvailable < 1) {
            return;
        }

        int flowRate = fluidTransferInfo.transferPerTick;
        Set<Direction> realDirections = EnumSet.noneOf(Direction.class);

        // Move liquid from the center to the output sides
        for (Direction direction : Direction.values()) {
            Section section = sections.get(EnumPipePart.fromFacing(direction));
            if (!section.getCurrentDirection().canOutput()) {
                continue;
            }
            if (section.getMaxFilled() > 0
                && pipe.getHolder().getCapabilityFromPipe(direction, Capabilities.Fluid.BLOCK) != null) {
                realDirections.add(direction);
            }
        }

        if (realDirections.size() > 0) {
            PipeEventFluid.SideCheck sideCheck =
                new PipeEventFluid.SideCheck(pipe.getHolder(), this, currentFluid.toStack(totalAvailable));
            sideCheck.disallowAllExcept(realDirections);
            pipe.getHolder().fireEvent(sideCheck);

            EnumSet<Direction> set = sideCheck.getOrder();

            List<Direction> random = new ArrayList<>(set);
            Collections.shuffle(random);

            float min = Math.min(flowRate * realDirections.size(), totalAvailable)
                / (float) flowRate / realDirections.size();

            for (Direction direction : random) {
                Section section = sections.get(EnumPipePart.fromFacing(direction));
                int available = section.fill(flowRate, false);
                int amountToPush = (int) (available * min);
                if (amountToPush < 1) {
                    amountToPush++;
                }

                amountToPush = center.drainInternal(amountToPush, false);
                if (amountToPush > 0) {
                    int filled = section.fill(amountToPush, true);
                    if (filled > 0) {
                        center.drainInternal(filled, true);
                        section.ticksInDirection = COOLDOWN_OUTPUT;
                    }
                }
            }
        }
    }

    private void moveToCenter() {
        int transferInCount = 0;
        Section center = sections.get(EnumPipePart.CENTER);
        int spaceAvailable = capacity - center.amount;
        if (spaceAvailable <= 0 || center.getMaxFilled() <= 0) {
            return;
        }
        int flowRate = fluidTransferInfo.transferPerTick;

        List<EnumPipePart> faces = new ArrayList<>();
        Collections.addAll(faces, EnumPipePart.FACES);
        Collections.shuffle(faces);

        int[] inputPerTick = new int[6];
        for (EnumPipePart part : faces) {
            Section section = sections.get(part);
            inputPerTick[part.getIndex()] = 0;
            if (section.getCurrentDirection().canInput()) {
                inputPerTick[part.getIndex()] = section.drainInternal(flowRate, false);
                if (inputPerTick[part.getIndex()] > 0) {
                    transferInCount++;
                }
            }
        }

        int[] totalOffered = Arrays.copyOf(inputPerTick, 6);
        PreMoveToCentre preMove = new PreMoveToCentre(
            pipe.getHolder(), this, currentFluid.toStack(Math.max(1, center.amount)),
            Math.min(flowRate, spaceAvailable), totalOffered, inputPerTick
        );
        // Event handlers edit the array in-place
        pipe.getHolder().fireEvent(preMove);

        int[] fluidLeavingSide = new int[6];

        // Work out how much fluid should leave
        int left = Math.min(flowRate, spaceAvailable);
        float min = Math.min(flowRate * transferInCount, spaceAvailable) / (float) flowRate / transferInCount;
        for (EnumPipePart part : EnumPipePart.FACES) {
            Section section = sections.get(part);
            // Move liquid from input sides to the centre
            int i = part.getIndex();
            if (inputPerTick[i] > 0) {
                int amountToDrain = (int) (inputPerTick[i] * min);
                if (amountToDrain < 1) {
                    amountToDrain++;
                }
                if (amountToDrain > left) {
                    amountToDrain = left;
                }
                int amountToPush = section.drainInternal(amountToDrain, false);
                if (amountToPush > 0) {
                    fluidLeavingSide[i] = amountToPush;
                    left -= amountToPush;
                }
            }
        }

        int[] fluidEnteringCentre = Arrays.copyOf(fluidLeavingSide, 6);
        OnMoveToCentre move = new OnMoveToCentre(
            pipe.getHolder(), this, currentFluid.toStack(Math.max(1, center.amount)), fluidLeavingSide,
            fluidEnteringCentre
        );
        pipe.getHolder().fireEvent(move);

        for (EnumPipePart part : EnumPipePart.FACES) {
            Section section = sections.get(part);
            int i = part.getIndex();
            int leaving = fluidLeavingSide[i];
            if (leaving > 0) {
                int actuallyDrained = section.drainInternal(leaving, true);
                if (actuallyDrained != leaving) {
                    throw new IllegalStateException(
                        "Couldn't drain " + leaving + " from " + part + ", only drained " + actuallyDrained
                    );
                }
                if (actuallyDrained > 0) {
                    section.ticksInDirection = COOLDOWN_INPUT;
                }
                int entering = fluidEnteringCentre[i];
                if (entering > 0) {
                    int actuallyFilled = center.fill(entering, true);
                    if (actuallyFilled != entering) {
                        throw new IllegalStateException(
                            "Couldn't fill " + entering + " from " + part + ", only filled " + actuallyFilled
                        );
                    }
                }
            }
        }
    }

    /**
     * One section of this pipe, and the {@code ResourceHandler<FluidResource>} a neighbour sees on that face --
     * 1.12.2's {@code Section implements IFluidHandler}. Insertion only, as in 1.12.2 (whose {@code drain}
     * overloads returned null): {@link #extract} always returns 0, and fluid only ever leaves a pipe by the pipe
     * pushing it. The one slot reports the pipe's fluid and this section's amount, where 1.12.2 reported no tanks
     * at all -- a {@code ResourceHandler} with zero slots cannot be inserted into through the default
     * {@code insert(resource, amount, transaction)}, which walks the slots.
     */
    final class Section implements ResourceHandler<FluidResource> {
        final EnumPipePart part;

        int amount = 0;

        int lastSentAmount = 0;

        Dir lastSentDirection = Dir.NONE;

        int currentTime = 0;

        /** Map of [time] -> [amount inserted]. Used to implement the delayed fluid travelling. */
        int[] incoming = new int[1];

        int incomingTotalCache = 0;

        /** If 0 then fluids can move from this in either direction. If less than 0 then fluids can only move into
         * this section from other tiles, and outputs to other sections. If greater than 0 then fluids can only move
         * out of this section into other tiles. */
        int ticksInDirection = 0;

        Section(EnumPipePart part) {
            this.part = part;
        }

        void writeToNbt(CompoundTag nbt) {
            nbt.putShort("capacity", (short) amount);
            nbt.putShort("ticksInDirection", (short) ticksInDirection);
            for (int i = 0; i < incoming.length; ++i) {
                nbt.putShort("in[" + i + "]", (short) incoming[i]);
            }
        }

        void readFromNbt(CompoundTag nbt) {
            this.amount = nbt.getShortOr("capacity", (short) 0);
            this.ticksInDirection = nbt.getShortOr("ticksInDirection", (short) 0);

            incomingTotalCache = 0;
            for (int i = 0; i < incoming.length; ++i) {
                incomingTotalCache += incoming[i] = nbt.getShortOr("in[" + i + "]", (short) 0);
            }
        }

        /** @return The maximum amount of fluid that can be inserted into this pipe on this tick. */
        int getMaxFilled() {
            int availableTotal = capacity - amount;
            int availableThisTick = fluidTransferInfo.transferPerTick - incoming[currentTime];
            return Math.min(availableTotal, availableThisTick);
        }

        /** @return The maximum amount of fluid that can be extracted out of this pipe this tick. */
        int getMaxDrained() {
            return Math.min(amount - incomingTotalCache, fluidTransferInfo.transferPerTick);
        }

        /** @return The fluid filled */
        int fill(int maxFill, boolean doFill) {
            int amountToFill = Math.min(getMaxFilled(), maxFill);
            if (amountToFill <= 0) {
                return 0;
            }
            if (doFill) {
                incoming[currentTime] += amountToFill;
                incomingTotalCache += amountToFill;
                amount += amountToFill;
            }
            return amountToFill;
        }

        int fillInternal(int maxFill, boolean doFill) {
            int amountToFill = Math.min(capacity - amount, maxFill);
            if (amountToFill <= 0) {
                return 0;
            }
            if (doFill) {
                incoming[currentTime] += amountToFill;
                incomingTotalCache += amountToFill;
                amount += amountToFill;
            }
            return amountToFill;
        }

        /** @return The amount drained */
        int drainInternal(int maxDrain, boolean doDrain) {
            maxDrain = Math.min(maxDrain, getMaxDrained());
            if (maxDrain <= 0) {
                return 0;
            } else {
                if (doDrain) {
                    amount -= maxDrain;
                }
                return maxDrain;
            }
        }

        void advanceForMovement() {
            incomingTotalCache -= incoming[currentTime];
            incoming[currentTime] = 0;
        }

        Dir getCurrentDirection() {
            return Dir.get(ticksInDirection);
        }

        // ResourceHandler<FluidResource>

        @Override
        public int size() {
            return 1;
        }

        @Override
        public FluidResource getResource(int slot) {
            return slot == 0 ? currentFluid : FluidResource.EMPTY;
        }

        @Override
        public long getAmountAsLong(int slot) {
            return slot == 0 ? amount : 0;
        }

        @Override
        public long getCapacityAsLong(int slot, FluidResource resource) {
            return slot == 0 ? capacity : 0;
        }

        @Override
        public boolean isValid(int slot, FluidResource resource) {
            return slot == 0 && !resource.isEmpty() && (currentFluid.isEmpty() || currentFluid.equals(resource));
        }

        /** 1.12.2's {@code Section#fill(FluidStack, boolean)}: only through a connected face that is not currently
         * outputting, only the pipe's current fluid (or any, if it is empty), and only if no {@code TryInsert}
         * handler cancels -- the hook the iron pipe uses to refuse fluid on its output face. */
        @Override
        public int insert(int slot, FluidResource resource, int maxAmount, TransactionContext transaction) {
            if (slot != 0 || resource.isEmpty() || maxAmount <= 0 || part.face == null) {
                return 0;
            }
            if (!getCurrentDirection().canInput() || !pipe.isConnected(part.face)) {
                return 0;
            }
            PipeEventFluid.TryInsert tryInsert = new PipeEventFluid.TryInsert(
                pipe.getHolder(), PipeFlowFluids.this, part.face, resource.toStack(maxAmount)
            );
            pipe.getHolder().fireEvent(tryInsert);
            if (tryInsert.isCanceled()) {
                return 0;
            }
            if (!currentFluid.isEmpty() && !currentFluid.equals(resource)) {
                return 0;
            }
            // Simulate first, so a refusal touches nothing -- not even the transaction's journal. (An empty pipe's
            // sections were reset by setFluid(EMPTY), so this answer does not change when setFluid runs below.)
            if (fill(maxAmount, false) <= 0) {
                return 0;
            }
            journal.updateSnapshots(transaction);
            if (currentFluid.isEmpty()) {
                setFluid(resource);
            }
            int filled = fill(maxAmount, true);
            if (filled > 0) {
                ticksInDirection = COOLDOWN_INPUT;
            }
            return filled;
        }

        @Override
        public int extract(int slot, FluidResource resource, int maxAmount, TransactionContext transaction) {
            return 0;
        }
    }

    /** Enum used for the current direction that a fluid is flowing. */
    enum Dir {
        IN,
        NONE,
        OUT;

        public boolean canInput() {
            return this != OUT;
        }

        public boolean canOutput() {
            return this != IN;
        }

        public static Dir get(int dir) {
            if (dir == 0) {
                return Dir.NONE;
            } else if (dir < 0) {
                return IN;
            } else {
                return OUT;
            }
        }
    }

    /** Everything {@link #journal} has to be able to put back: the fluid, the delay, and each section's state. */
    private record FlowSnapshot(
        FluidResource fluid, int delay, int[] amount, int[] ticksInDirection, int[] currentTime, int[] incomingTotal,
        int[][] incoming
    ) {
        static FlowSnapshot of(PipeFlowFluids flow) {
            int n = EnumPipePart.VALUES.length;
            int[] amount = new int[n];
            int[] ticks = new int[n];
            int[] time = new int[n];
            int[] total = new int[n];
            int[][] incoming = new int[n][];
            for (EnumPipePart part : EnumPipePart.VALUES) {
                Section s = flow.sections.get(part);
                int i = part.getIndex();
                amount[i] = s.amount;
                ticks[i] = s.ticksInDirection;
                time[i] = s.currentTime;
                total[i] = s.incomingTotalCache;
                incoming[i] = s.incoming.clone();
            }
            return new FlowSnapshot(flow.currentFluid, flow.currentDelay, amount, ticks, time, total, incoming);
        }

        void restore(PipeFlowFluids flow) {
            flow.currentFluid = fluid;
            flow.currentDelay = delay;
            for (EnumPipePart part : EnumPipePart.VALUES) {
                Section s = flow.sections.get(part);
                int i = part.getIndex();
                s.amount = amount[i];
                s.ticksInDirection = ticksInDirection[i];
                s.currentTime = currentTime[i];
                s.incomingTotalCache = incomingTotal[i];
                s.incoming = incoming[i];
            }
        }
    }
}
