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

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import net.minecraft.ChatFormatting;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidType;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.fluids.capability.IFluidHandler.FluidAction;

import buildcraft.api.core.BCLog;
import buildcraft.api.core.EnumPipePart;
import buildcraft.api.core.IFluidFilter;
import buildcraft.api.core.IFluidHandlerAdv;
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
 * The fluid-moving simulation -- see the 26.x copy of this class for the full account of the section state machine,
 * the client-sync policy (at most one whole-tile sync per {@link #NET_UPDATE_INTERVAL} ticks, only while something
 * changed) and what is dropped ({@code addDrops}' fragile fluid shard, {@code addTriggers}, the payload pair).
 *
 * <p>This target keeps 1.12.2's shape almost verbatim: {@link IFlowFluid} here still takes {@code boolean simulate}
 * and a {@link FluidStack}, each section is a classic {@link IFluidHandler} exposed under
 * {@link ForgeCapabilities#FLUID_HANDLER}, and neighbours are reached with
 * {@code getCapabilityFromPipe(side, FLUID_HANDLER)} -- so no transaction journal is needed, simulation is the
 * {@link FluidAction} flag, exactly as 1.12.2 did it. {@code tryExtractFluidAdv} stays a separate method, including
 * 1.12.2's fall-back for a tank that does not implement {@link IFluidHandlerAdv} (walk its tanks and drain the
 * first match); it returns null only where 1.12.2 returned {@code PASS} -- no fluid handler on that side at all.
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
    /** The pipe's one fluid, or {@link FluidStack#EMPTY}. Its amount is meaningless (1.12.2's was too). */
    private FluidStack currentFluid = FluidStack.EMPTY;
    private int currentDelay;

    // Server-only, never persisted: what the client was last told.
    private FluidStack lastSentFluid = FluidStack.EMPTY;
    private long lastSyncTick = Long.MIN_VALUE;

    public PipeFlowFluids(IPipe pipe) {
        super(pipe);
        for (EnumPipePart part : EnumPipePart.VALUES) {
            sections.put(part, new Section(part));
        }
        setFluid(FluidStack.EMPTY);
    }

    public PipeFlowFluids(IPipe pipe, CompoundTag nbt, HolderLookup.Provider registries) {
        super(pipe, nbt, registries);
        for (EnumPipePart part : EnumPipePart.VALUES) {
            sections.put(part, new Section(part));
        }
        FluidStack fluid = nbt.contains("fluid") ? FluidStack.loadFluidStackFromNBT(nbt.getCompound("fluid"))
            : FluidStack.EMPTY;
        setFluid(fluid);
        if (!fluid.isEmpty()) {
            for (EnumPipePart part : EnumPipePart.VALUES) {
                String key = "tank[" + part.getIndex() + "]";
                if (nbt.contains(key)) {
                    sections.get(part).readFromNbt(nbt.getCompound(key));
                }
            }
        }
    }

    /** 1.12.2's keys, as on 26.x ({@code tank[i].capacity} is a section's <em>amount</em>). The {@code fluid} tag is
     * a classic {@code FluidStack} tag, so it needs an amount: the pipe's total is written there. */
    @Override
    public CompoundTag writeToNbt(HolderLookup.Provider registries) {
        CompoundTag nbt = super.writeToNbt(registries);
        if (!currentFluid.isEmpty()) {
            int total = 0;
            for (Section section : sections.values()) {
                total += section.amount;
            }
            nbt.put("fluid", new FluidStack(currentFluid, Math.max(1, total)).writeToNBT(new CompoundTag()));
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

    @Override
    public boolean canConnect(Direction face, BlockEntity oTile) {
        return oTile.getCapability(ForgeCapabilities.FLUID_HANDLER, face.getOpposite()).isPresent();
    }

    /** One {@link Section} per face; none for a null side -- see the 26.x copy. */
    @Override
    @Nullable
    @SuppressWarnings("unchecked")
    public <T> T getCapability(Capability<T> capability, @Nullable Direction facing) {
        if (capability == ForgeCapabilities.FLUID_HANDLER && facing != null) {
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
    public FluidStack tryExtractFluid(int millibuckets, Direction from, @Nullable FluidStack filter, boolean simulate) {
        FluidExtractor extractor = (mb, c, handler) -> {
            FluidStack f = filter == null ? c : filter;
            if (!c.isEmpty() && !c.isFluidEqual(f)) {
                return FluidStack.EMPTY;
            }
            return extractSimple(mb, f, handler, simulate);
        };
        FluidStack result = tryExtractFluidInternal(millibuckets, from, extractor, simulate);
        return result == null || result.isEmpty() ? null : result;
    }

    @Override
    @Nullable
    public FluidStack tryExtractFluidAdv(int millibuckets, Direction from, IFluidFilter filter, boolean simulate) {
        FluidExtractor extractor = (mb, c, handler) -> {
            if (!c.isEmpty()) {
                if (!filter.matches(c)) {
                    return FluidStack.EMPTY;
                }
                return extractSimple(mb, c, handler, simulate);
            }
            if (handler instanceof IFluidHandlerAdv handlerAdv) {
                // This will likely be cheaper
                return handlerAdv.drain(filter, mb, simulate ? FluidAction.SIMULATE : FluidAction.EXECUTE);
            }

            // Search for the first valid fluid
            for (int tank = 0; tank < handler.getTanks(); tank++) {
                FluidStack contents = handler.getFluidInTank(tank);
                if (!contents.isEmpty() && filter.matches(contents)) {
                    FluidStack extracted = extractSimple(mb, contents, handler, simulate);
                    if (!extracted.isEmpty()) {
                        return extracted;
                    }
                }
            }
            return FluidStack.EMPTY;
        };
        return tryExtractFluidInternal(millibuckets, from, extractor, simulate);
    }

    @FunctionalInterface
    private interface FluidExtractor {
        FluidStack extract(int millibuckets, FluidStack current, IFluidHandler handler);
    }

    /** @return The extracted fluid, {@link FluidStack#EMPTY} if nothing was (1.12.2's {@code FAIL}), or null if
     *         there is no fluid handler on that side (1.12.2's {@code PASS}). */
    @Nullable
    private FluidStack tryExtractFluidInternal(
        int millibuckets, Direction from, FluidExtractor extractor, boolean simulate
    ) {
        if (from == null || millibuckets <= 0) {
            return FluidStack.EMPTY;
        }
        IFluidHandler fluidHandler = pipe.getHolder().getCapabilityFromPipe(from, ForgeCapabilities.FLUID_HANDLER);
        if (fluidHandler == null) {
            return null;
        }
        Section section = sections.get(EnumPipePart.fromFacing(from));
        Section middle = sections.get(EnumPipePart.CENTER);
        millibuckets = Math.min(millibuckets, capacity * 2 - section.amount - middle.amount);
        if (millibuckets <= 0) {
            return FluidStack.EMPTY;
        }
        FluidStack toAdd = extractor.extract(millibuckets, currentFluid, fluidHandler);
        if (toAdd == null || toAdd.isEmpty()) {
            return FluidStack.EMPTY;
        }
        millibuckets = toAdd.getAmount();
        if (currentFluid.isEmpty() && !simulate) {
            setFluid(toAdd);
        }
        int reallyFilled = section.fillInternal(millibuckets, !simulate);
        int leftOver = millibuckets - reallyFilled;
        reallyFilled += middle.fillInternal(leftOver, !simulate);
        if (!simulate) {
            section.ticksInDirection = COOLDOWN_INPUT;
        }
        if (reallyFilled != millibuckets) {
            BCLog.logger.warn(
                "[tryExtractFluidAdv] Filled " + reallyFilled + " != extracted " + millibuckets + " (handler = "
                    + fluidHandler.getClass() + ") @" + pipe.getHolder().getPipePos()
            );
        }
        return toAdd;
    }

    /** 1.12.2 compared the filter against itself here ({@code !filter.isFluidEqual(filter)}), so its "drained the
     * wrong fluid" check could never fire; this compares against what was actually drained. */
    private static FluidStack extractSimple(
        int millibuckets, FluidStack filter, IFluidHandler handler, boolean simulate
    ) {
        FluidAction action = simulate ? FluidAction.SIMULATE : FluidAction.EXECUTE;
        if (filter.isEmpty()) {
            return handler.drain(millibuckets, action);
        }
        FluidStack drained = handler.drain(new FluidStack(filter, millibuckets), action);
        if (!drained.isEmpty() && !drained.isFluidEqual(filter)) {
            throw new IllegalStateException(
                "Drained fluid did not equal filter fluid! (Filter = " + filter.getFluid() + ", actually drained = "
                    + drained.getFluid() + ", IFluidHandler = " + handler.getClass() + "(" + handler + "))"
            );
        }
        return drained;
    }

    @Override
    public int insertFluidsForce(FluidStack fluid, @Nullable Direction from, boolean simulate) {
        Section s = sections.get(EnumPipePart.CENTER);
        if (fluid == null || fluid.isEmpty()) {
            return 0;
        }
        if (!currentFluid.isEmpty() && !currentFluid.isFluidEqual(fluid)) {
            return 0;
        }
        if (currentFluid.isEmpty() && !simulate) {
            setFluid(fluid);
        }
        int filled = s.fill(fluid.getAmount(), !simulate);
        if (filled == 0) {
            return 0;
        }
        if (simulate) {
            return filled;
        }
        if (from != null) {
            sections.get(EnumPipePart.fromFacing(from)).ticksInDirection = COOLDOWN_INPUT;
        }
        return filled;
    }

    @Override
    @Nullable
    public FluidStack extractFluidsForce(int min, int max, @Nullable Direction section, boolean simulate) {
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
        FluidStack fluid = new FluidStack(currentFluid, amount);
        if (!simulate) {
            s.amount -= amount;
            if (s.amount == 0) {
                boolean isEmpty = true;
                for (Section s2 : sections.values()) {
                    isEmpty &= s2.amount == 0;
                }
                if (isEmpty) {
                    setFluid(FluidStack.EMPTY);
                }
            }
        }
        return fluid;
    }

    // IDebuggable

    @Override
    public void getDebugInfo(List<String> left, List<String> right, @Nullable Direction side) {
        left.add(" - FluidType = " + (currentFluid.isEmpty() ? "empty" : currentFluid.getDisplayName().getString()));
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

    /** The fluid to draw, or {@link FluidStack#EMPTY}. On the client this is what the last sync wrote. */
    public FluidStack getFluidForRender() {
        return currentFluid;
    }

    /** Section amounts indexed by {@link EnumPipePart#getIndex()} (6 = centre). */
    public int[] getAmountsForRender() {
        int[] arr = new int[7];
        for (EnumPipePart part : EnumPipePart.VALUES) {
            arr[part.getIndex()] = sections.get(part).amount;
        }
        return arr;
    }

    // Internal logic

    private void setFluid(FluidStack fluid) {
        currentFluid = fluid.isEmpty() ? FluidStack.EMPTY : new FluidStack(fluid, 1);
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
                setFluid(FluidStack.EMPTY);
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

        boolean send = !currentFluid.isFluidEqual(lastSentFluid) || currentFluid.isEmpty() != lastSentFluid.isEmpty();
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
                    new PipeEventFluid.SideCheck(pipe.getHolder(), this, new FluidStack(currentFluid, maxDrain));
                sideCheck.disallowAllExcept(part.face);
                pipe.getHolder().fireEvent(sideCheck);
                if (sideCheck.getOrder().size() == 1) {
                    IFluidHandler fluidHandler =
                        pipe.getHolder().getCapabilityFromPipe(part.face, ForgeCapabilities.FLUID_HANDLER);
                    if (fluidHandler == null) {
                        continue;
                    }
                    int filled = fluidHandler.fill(new FluidStack(currentFluid, maxDrain), FluidAction.EXECUTE);
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
                && pipe.getHolder().getCapabilityFromPipe(direction, ForgeCapabilities.FLUID_HANDLER) != null) {
                realDirections.add(direction);
            }
        }

        if (realDirections.size() > 0) {
            PipeEventFluid.SideCheck sideCheck =
                new PipeEventFluid.SideCheck(pipe.getHolder(), this, new FluidStack(currentFluid, totalAvailable));
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
            pipe.getHolder(), this, currentFluid, Math.min(flowRate, spaceAvailable), totalOffered, inputPerTick
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
            pipe.getHolder(), this, currentFluid, fluidLeavingSide, fluidEnteringCentre
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

    /** One section of this pipe, and the {@link IFluidHandler} a neighbour sees on that face -- 1.12.2's
     * {@code Section implements IFluidHandler}. Fill only; both {@code drain}s return
     * {@link FluidStack#EMPTY}, as 1.12.2's returned null. The one tank reports the pipe's fluid and this
     * section's amount (1.12.2 reported no tank properties). */
    final class Section implements IFluidHandler {
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
            this.amount = nbt.getShort("capacity");
            this.ticksInDirection = nbt.getShort("ticksInDirection");

            incomingTotalCache = 0;
            for (int i = 0; i < incoming.length; ++i) {
                incomingTotalCache += incoming[i] = nbt.getShort("in[" + i + "]");
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

        // IFluidHandler

        @Override
        public int getTanks() {
            return 1;
        }

        @Override
        public @NotNull FluidStack getFluidInTank(int tank) {
            return tank != 0 || amount <= 0 || currentFluid.isEmpty() ? FluidStack.EMPTY
                : new FluidStack(currentFluid, amount);
        }

        @Override
        public int getTankCapacity(int tank) {
            return tank == 0 ? capacity : 0;
        }

        @Override
        public boolean isFluidValid(int tank, @NotNull FluidStack stack) {
            return tank == 0 && !stack.isEmpty() && (currentFluid.isEmpty() || currentFluid.isFluidEqual(stack));
        }

        /** 1.12.2's {@code Section#fill(FluidStack, boolean)} -- see the 26.x copy's {@code insert}. */
        @Override
        public int fill(FluidStack resource, FluidAction action) {
            if (resource == null || resource.isEmpty() || part.face == null) {
                return 0;
            }
            if (!getCurrentDirection().canInput() || !pipe.isConnected(part.face)) {
                return 0;
            }
            resource = resource.copy();
            PipeEventFluid.TryInsert tryInsert = new PipeEventFluid.TryInsert(
                pipe.getHolder(), PipeFlowFluids.this, part.face, resource
            );
            pipe.getHolder().fireEvent(tryInsert);
            if (tryInsert.isCanceled()) {
                return 0;
            }

            if (currentFluid.isEmpty() || currentFluid.isFluidEqual(resource)) {
                boolean doFill = action.execute();
                if (doFill && currentFluid.isEmpty()) {
                    setFluid(resource);
                }
                int filled = fill(resource.getAmount(), doFill);
                if (filled > 0 && doFill) {
                    ticksInDirection = COOLDOWN_INPUT;
                }
                return filled;
            }
            return 0;
        }

        @Override
        public @NotNull FluidStack drain(FluidStack resource, FluidAction action) {
            return FluidStack.EMPTY;
        }

        @Override
        public @NotNull FluidStack drain(int maxDrain, FluidAction action) {
            return FluidStack.EMPTY;
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
}
