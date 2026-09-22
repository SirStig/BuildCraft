/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * Copyright (c) 2026 Joshua Kac -- NeoForge port (BuildCraft 10)
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL
 * was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.factory.tile;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;

import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidType;
import net.minecraftforge.fluids.capability.IFluidHandler;

import buildcraft.api.core.BCDebugging;
import buildcraft.api.core.BCLog;
import buildcraft.api.core.SafeTimeTracker;
import buildcraft.api.mj.IMjReadable;
import buildcraft.api.mj.IMjReceiver;
import buildcraft.api.mj.MjAPI;
import buildcraft.api.mj.MjCapabilities;

import buildcraft.lib.fluid.Tank;
import buildcraft.lib.misc.AdvancementUtil;
import buildcraft.lib.misc.BlockUtil;
import buildcraft.lib.misc.FluidUtilBC;
import buildcraft.lib.mj.MjRedstoneBatteryReceiver;

import buildcraft.BCFactoryRegistries;

/**
 * Extends {@link TileMiner}'s vertical-shaft digging into a fluid-draining search. Mirrors the 26.x class of the
 * same name -- see that one's javadoc for the full account of what changed from 1.12.2: the dropped oil-spring
 * branch, the dead {@code fluidConnection} field, the dropped debug-profiler instrumentation, the
 * {@code Fluid#isGaseous()} density-based approximation, and why {@link #mjRedstoneReceiver} exists as a second
 * receiver alongside the inherited (unused, for capability purposes) {@code TileMiner#mjReceiver}. This file
 * differs only in the usual 1.20.1 places: NBT is {@code CompoundTag}, capabilities are exposed through
 * {@code getCapability} rather than registered against the block entity type, and {@link Tank} is a real
 * {@code IFluidHandler} itself (no wrapping needed for the {@link ForgeCapabilities#FLUID_HANDLER} capability).
 */
public class TilePump extends TileMiner {
    public static final boolean DEBUG_PUMP = BCDebugging.shouldDebugComplex("factory.pump");

    private static final Direction[] SEARCH_NORMAL = { //
        Direction.UP, Direction.NORTH, Direction.SOUTH, //
        Direction.WEST, Direction.EAST //
    };

    private static final Direction[] SEARCH_GASEOUS = { //
        Direction.DOWN, Direction.NORTH, Direction.SOUTH, //
        Direction.WEST, Direction.EAST //
    };

    private static final boolean PUMPS_CONSUME_WATER = false;
    private static final int PUMP_MAX_DISTANCE = 64;

    private static final ResourceLocation ADVANCEMENT_DRAIN_ANY =
        new ResourceLocation("buildcraftfactory", "draining_the_world");

    static final class FluidPath {
        final BlockPos thisPos;
        @Nullable
        final FluidPath parent;

        FluidPath(BlockPos thisPos, @Nullable FluidPath parent) {
            this.thisPos = thisPos;
            this.parent = parent;
        }
    }

    public final Tank tank = new Tank(16 * FluidType.BUCKET_VOLUME, this::markDirtyAndSync);
    public final MjRedstoneBatteryReceiver mjRedstoneReceiver = new MjRedstoneBatteryReceiver(battery);
    private final LazyOptional<IMjReceiver> redstoneReceiverCap = LazyOptional.of(() -> mjRedstoneReceiver);
    private final LazyOptional<IMjReadable> redstoneReadableCap = LazyOptional.of(() -> mjRedstoneReceiver);
    private final LazyOptional<IFluidHandler> fluidCap = LazyOptional.of(() -> tank);

    private boolean queueBuilt = false;
    private final Map<BlockPos, FluidPath> paths = new HashMap<>();
    private final Deque<BlockPos> queue = new ArrayDeque<>();
    private boolean isInfiniteWaterSource;
    private final SafeTimeTracker rebuildDelay = new SafeTimeTracker(30);

    /** The position just below the bottom of the pump tube. */
    private BlockPos targetPos;

    @Nullable
    private UUID owner;

    public TilePump(BlockPos pos, BlockState state) {
        super(BCFactoryRegistries.PUMP_TYPE.get(), pos, state);
        tank.setCanFill(false);
    }

    public void onPlacedBy(@Nullable LivingEntity placer) {
        owner = placer == null ? null : placer.getUUID();
    }

    private void buildQueue() {
        queue.clear();
        paths.clear();
        Fluid queueFluid = null;
        isInfiniteWaterSource = false;
        Set<BlockPos> checked = new HashSet<>();
        List<BlockPos> nextPosesToCheck = new ArrayList<>();
        for (targetPos = worldPosition.below(); !level.isOutsideBuildHeight(targetPos); targetPos = targetPos.below()) {
            if (worldPosition.getY() - targetPos.getY() > MINING_MAX_DEPTH) {
                break;
            }
            FluidState hereFluid = BlockUtil.getFluidWithFlowing(level, targetPos);
            if (hereFluid != null) {
                queueFluid = hereFluid.getType();
                nextPosesToCheck.add(targetPos);
                paths.put(targetPos, new FluidPath(targetPos, null));
                checked.add(targetPos);
                if (FluidUtilBC.getFluidSource(level, targetPos) != null) {
                    queue.add(targetPos);
                }
                break;
            }
            if (!level.getBlockState(targetPos).isAir() && !level.getBlockState(targetPos).is(BCFactoryRegistries.TUBE.get())) {
                break;
            }
        }
        if (nextPosesToCheck.isEmpty() || queueFluid == null) {
            return;
        }
        buildQueue0(queueFluid, nextPosesToCheck, checked);
    }

    /** See the 26.x copy of this class's javadoc for the full account of the search and the infinite-water-source
     * rule this reproduces. */
    private void buildQueue0(Fluid queueFluid, List<BlockPos> nextPosesToCheck, Set<BlockPos> checked) {
        Direction[] directions = isGaseous(queueFluid) ? SEARCH_GASEOUS : SEARCH_NORMAL;
        boolean isWater = !PUMPS_CONSUME_WATER && FluidUtilBC.areFluidsEqual(queueFluid, Fluids.WATER);
        int maxLengthSquared = PUMP_MAX_DISTANCE * PUMP_MAX_DISTANCE;
        outer: while (!nextPosesToCheck.isEmpty()) {
            List<BlockPos> nextPosesToCheckCopy = new ArrayList<>(nextPosesToCheck);
            nextPosesToCheck.clear();
            for (BlockPos posToCheck : nextPosesToCheckCopy) {
                int count = 0;
                for (Direction side : directions) {
                    BlockPos offsetPos = posToCheck.relative(side);
                    if (offsetPos.distSqr(targetPos) > maxLengthSquared) {
                        continue;
                    }
                    boolean isNew = checked.add(offsetPos);
                    if (isNew) {
                        FluidState fluidAt = BlockUtil.getFluidWithFlowing(level, offsetPos);
                        boolean eq = fluidAt != null && FluidUtilBC.areFluidsEqual(fluidAt.getType(), queueFluid);
                        if (eq) {
                            FluidPath oldPath = paths.get(posToCheck);
                            FluidPath path = new FluidPath(offsetPos, oldPath);
                            paths.put(offsetPos, path);
                            if (FluidUtilBC.getFluidSource(level, offsetPos) != null) {
                                queue.add(offsetPos);
                            }
                            nextPosesToCheck.add(offsetPos);
                            count++;
                        }
                    } else {
                        // We've already tested this block: it *must* be a valid same-fluid neighbour.
                        count++;
                    }
                }
                if (isWater && count >= 2) {
                    BlockState below = level.getBlockState(posToCheck.below());
                    Fluid fluidBelow = FluidUtilBC.getFluidSource(below);
                    if (FluidUtilBC.areFluidsEqual(fluidBelow, Fluids.WATER) || below.isSolid()) {
                        isInfiniteWaterSource = true;
                        break outer;
                    }
                }
            }
        }
    }

    private static boolean isGaseous(Fluid fluid) {
        return fluid.getFluidType().getDensity() < 0;
    }

    private boolean canDrain(BlockPos blockPos) {
        Fluid fluid = FluidUtilBC.getFluidSource(level, blockPos);
        return tank.isEmpty() ? fluid != null : FluidUtilBC.areFluidsEqual(fluid, tank.getFluidType());
    }

    private void nextPos() {
        while (!queue.isEmpty()) {
            currentPos = queue.removeLast();
            if (canDrain(currentPos)) {
                updateLength();
                return;
            }
        }
        currentPos = null;
        updateLength();
    }

    @Override
    protected BlockPos getTargetPos() {
        if (queue.isEmpty()) {
            return null;
        }
        return targetPos;
    }

    @Override
    public void serverTick() {
        if (!queueBuilt) {
            buildQueue();
            queueBuilt = true;
        }
        super.serverTick();
        FluidUtilBC.pushFluidAround(level, worldPosition, tank);
    }

    @Override
    protected void mine() {
        if (tank.getFluidAmount() > tank.getCapacity() / 2) {
            return;
        }
        long target = 10 * MjAPI.MJ;
        if (currentPos != null && paths.containsKey(currentPos)) {
            progress += battery.extractPower(0, target - progress);
            if (progress < target) {
                return;
            }

            FluidStack drain = FluidUtilBC.drainBlock(level, currentPos, false);

            drain_attempt: {
                if (drain == null) {
                    if (DEBUG_PUMP) {
                        BCLog.logger.info(
                            "Pump @ " + worldPosition + " tried to drain " + currentPos
                                + " but couldn't because no fluid was drained!");
                    }
                    break drain_attempt;
                }

                BlockPos invalid = getFirstInvalidPointOnPath(currentPos);
                if (invalid != null) {
                    if (DEBUG_PUMP) {
                        BCLog.logger.info(
                            "Pump @ " + worldPosition + " tried to drain " + currentPos
                                + " but couldn't because the path stopped at " + invalid + "!");
                    }
                    break drain_attempt;
                } else if (!canDrain(currentPos)) {
                    if (DEBUG_PUMP) {
                        BCLog.logger.info(
                            "Pump @ " + worldPosition + " tried to drain " + currentPos
                                + " but couldn't because it couldn't be drained!");
                    }
                    break drain_attempt;
                }

                tank.fillInternal(drain);
                progress = 0;
                isInfiniteWaterSource &= !PUMPS_CONSUME_WATER;
                if (isInfiniteWaterSource) {
                    isInfiniteWaterSource = FluidUtilBC.areFluidsEqual(drain.getFluid(), Fluids.WATER);
                }
                if (owner != null) {
                    AdvancementUtil.unlockAdvancement(owner, ADVANCEMENT_DRAIN_ANY);
                }
                if (!isInfiniteWaterSource) {
                    FluidUtilBC.drainBlock(level, currentPos, true);
                    paths.remove(currentPos);
                    nextPos();
                }
                return;
            }
            if (!rebuildDelay.markTimeIfDelay(level)) {
                return;
            }
        } else {
            if (currentPos == null && !rebuildDelay.markTimeIfDelay(level)) {
                return;
            }
            if (DEBUG_PUMP) {
                if (currentPos == null) {
                    BCLog.logger.info("Pump @ " + worldPosition + " is rebuilding it's queue...");
                } else {
                    BCLog.logger.info(
                        "Pump @ " + worldPosition + " is rebuilding it's queue because we don't have a path for "
                            + currentPos);
                }
            }
        }
        buildQueue();
        nextPos();
    }

    @Nullable
    private BlockPos getFirstInvalidPointOnPath(BlockPos from) {
        FluidPath path = paths.get(from);
        if (path == null) {
            return from;
        }
        do {
            if (BlockUtil.getFluidWithFlowing(level, path.thisPos) == null) {
                return path.thisPos;
            }
        } while ((path = path.parent) != null);
        return null;
    }

    @Override
    public void load(CompoundTag nbt) {
        super.load(nbt);
        tank.readFromNBT(nbt.getCompound("tank"));
        owner = nbt.hasUUID("owner") ? nbt.getUUID("owner") : null;
    }

    @Override
    protected void saveAdditional(CompoundTag nbt) {
        super.saveAdditional(nbt);
        nbt.put("tank", tank.writeToNBT(new CompoundTag()));
        if (owner != null) {
            nbt.putUUID("owner", owner);
        }
    }

    @Override
    public void getDebugInfo(List<String> left, List<String> right, @Nullable Direction side) {
        super.getDebugInfo(left, right, side);
        left.add("fluid = " + tank.getDebugString());
        left.add("queue size = " + queue.size());
        left.add("infinite = " + isInfiniteWaterSource);
    }

    @Override
    protected long getBatteryCapacity() {
        return 50 * MjAPI.MJ;
    }

    /** Overrides {@code MjCapabilities.RECEIVER}/{@code READABLE} to return {@link #mjRedstoneReceiver} rather
     * than falling through to {@code TileMiner}'s own (plain, non-redstone) receiver capability -- see the class
     * javadoc for why. */
    @Override
    public <T> @NotNull LazyOptional<T> getCapability(@NotNull Capability<T> cap, Direction side) {
        if (cap == MjCapabilities.RECEIVER) {
            return redstoneReceiverCap.cast();
        }
        if (cap == MjCapabilities.READABLE) {
            return redstoneReadableCap.cast();
        }
        if (cap == ForgeCapabilities.FLUID_HANDLER) {
            return fluidCap.cast();
        }
        return super.getCapability(cap, side);
    }

    @Override
    public void invalidateCaps() {
        super.invalidateCaps();
        redstoneReceiverCap.invalidate();
        redstoneReadableCap.invalidate();
        fluidCap.invalidate();
    }
}
