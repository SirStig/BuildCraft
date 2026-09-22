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

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.UUIDUtil;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.FluidType;

import buildcraft.api.core.BCDebugging;
import buildcraft.api.core.BCLog;
import buildcraft.api.core.SafeTimeTracker;
import buildcraft.api.mj.MjAPI;

import buildcraft.lib.fluid.Tank;
import buildcraft.lib.misc.AdvancementUtil;
import buildcraft.lib.misc.BlockUtil;
import buildcraft.lib.misc.FluidUtilBC;
import buildcraft.lib.mj.MjRedstoneBatteryReceiver;

import buildcraft.BCFactoryRegistries;

/**
 * Extends {@link TileMiner}'s vertical-shaft digging into a fluid-draining search: instead of breaking one block
 * straight down, a pump follows every connected block of the <em>same</em> fluid outward from the position below
 * it (a breadth-first search across up to {@link #PUMP_MAX_DISTANCE} blocks), draining whichever reachable source
 * block is cheapest to reach into its own {@link #tank}, then {@linkplain FluidUtilBC#pushFluidAround pushes that
 * tank's contents} out to any neighbour that will take them.
 *
 * <p><b>The oil-spring branch is dropped entirely, not ported.</b> 1.12.2's {@code isOil}/{@code oilSpringPos}/
 * {@code ADVANCEMENT_DRAIN_OIL}/{@code BCEnergyFluids.crudeOil}/{@code ITileOilSpring#onPumpOil} all depend on
 * {@code buildcraft.energy}, which is not ported at all in this port. This is not a functional cut: {@code isOil}
 * itself already opened with {@code if (BCModules.ENERGY.isLoaded()) { ... } return false;}, and since
 * {@code buildcraft.energy} never registers here, that condition is permanently {@code false} in this port
 * regardless of whether the branch exists in source or not -- dropping it outright reproduces the exact same
 * real-world behaviour this port is already in, matching the precedent already set for {@code BlockSpringWater}'s
 * dropped oil half and the un-ported {@code ITileOilSpring} itself.
 *
 * <p><b>{@code fluidConnection} is not ported</b> -- a dead field even in 1.12.2's own source (assigned in
 * {@code buildQueue} but never read anywhere in the whole 1.12.2 tree).
 *
 * <p><b>The debug-profiler instrumentation ({@code Profiler debugProf}/{@code Stopwatch watch}/
 * {@code ProfilerEntry}) is dropped, not reproduced.</b> It only ever ran behind {@link #DEBUG_PUMP} (a system
 * property nobody sets), had zero effect on behaviour, and would have needed merging with a live per-tick world
 * profiler this target has no simple handle on. {@link #DEBUG_PUMP}'s real diagnostic value -- the
 * {@code BCLog.logger.info} calls scattered through {@link #mine()} explaining <em>why</em> a drain attempt
 * failed -- is kept in full.
 *
 * <p><b>{@code Fluid#isGaseous()} has no modern equivalent</b> -- confirmed via {@code javap} against
 * {@link FluidType}: no {@code isGaseous}-shaped method exists there at all, getter or builder flag. The
 * established Forge/NeoForge convention (a negative {@link FluidType#getDensity()} marks a gas) stands in for it
 * in {@link #isGaseous}. Nothing this port registers is actually gaseous yet (only vanilla water and lava, both
 * liquids), so {@link #SEARCH_GASEOUS} never actually gets used in practice -- kept anyway, faithfully, since the
 * original explicitly special-cased it and the direction-array swap costs nothing.
 *
 * <p><b>{@code getOwner().getId()}</b> (used only to grant {@link #ADVANCEMENT_DRAIN_ANY}) follows the same
 * {@link #owner} UUID-field pattern {@code TileChute}/{@code TileEngineWood} already established -- unlike
 * {@code TileMiningWell} (which needed no owner at all, see that class's own javadoc), a pump's advancement grant
 * is the one place this tile still cares who placed it.
 *
 * <p><b>{@code createMjReceiver()}</b> no longer exists as an overridable hook on {@link TileMiner} (see that
 * class's own javadoc for why) -- its {@code mjReceiver} field is fixed to a plain {@code MjBatteryReceiver}.
 * Since a pump genuinely still wants {@link MjRedstoneBatteryReceiver} (unlike the mining well, which was happy
 * with the plain receiver 1.12.2 gave it by default too), and {@code TileMiner} is out of scope for this pass,
 * {@link #mjRedstoneReceiver} is a second receiver here, wrapping the very same {@link #battery} -- inherited
 * {@code mjReceiver} is simply not registered for this tile; {@link BCFactoryRegistries} registers
 * {@link #mjRedstoneReceiver} in its place.
 *
 * <p>{@code BlockUtil}'s own 1.12.2 {@code getFluid}/{@code getFluidWithoutFlowing}/{@code drainBlock} (this
 * class's other fluid-block-inspection dependencies, beyond the already-ported {@code getFluidWithFlowing}) move
 * to {@link FluidUtilBC#getFluidSource}/{@link FluidUtilBC#drainBlock} instead -- {@code BlockUtil} itself is out
 * of scope for this pass; see that class's own javadoc for the full account of the modern shape.
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

    /** {@code BCCoreConfig.pumpsConsumeWater}'s unconfigured default; see {@link TileMiner}'s own javadoc for why
     * no config system stands behind this. */
    private static final boolean PUMPS_CONSUME_WATER = false;

    /** {@code BCCoreConfig.pumpMaxDistance}'s unconfigured default. */
    private static final int PUMP_MAX_DISTANCE = 64;

    private static final Identifier ADVANCEMENT_DRAIN_ANY =
        Identifier.fromNamespaceAndPath("buildcraftfactory", "draining_the_world");

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

    /** The breadth-first search proper. Walks outward from {@code nextPosesToCheck} through every connected block
     * of {@code queueFluid}, recording a {@link FluidPath} parent chain (so {@link #getFirstInvalidPointOnPath}
     * can later notice a path drying up partway along) and queueing every source block found for
     * {@link #nextPos()} to drain.
     *
     * <p>The infinite-water-source check mirrors vanilla's own rule for exactly the same trick a player can pull
     * off by hand (two adjacent water buckets over a solid floor regenerating a source block forever) --
     * confirmed by decompiling the real modern {@code FlowingFluid#getNewLiquid}, the direct descendant of
     * 1.12.2's {@code BlockDynamicLiquid#updateTick} this class's own comment already pointed at: a flowing block
     * becomes a new source once it has {@code neighbourSources >= 2} horizontally-adjacent source neighbours of
     * the same fluid <em>and</em> the block below is either solid or another source of that fluid. Same shape,
     * same threshold (2), just re-expressed against {@link FluidState}/{@code BlockState#isSolid()} instead of
     * {@code Material#isSolid()}. A pump that finds this pattern treats the whole body as inexhaustible and never
     * actually removes the source block it drains from (see {@link #mine()}).
     */
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
        return tank.isEmpty() ? fluid != null : FluidUtilBC.areFluidsEqual(fluid, tank.getFluidType().getFluid());
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

    /** <b>Deviates from 1.12.2, which checked {@code queue.isEmpty()} here instead of {@link #paths}.</b> {@code
     * queue} is only the not-yet-visited-this-round worklist of source blocks left to drain, so it empties out the
     * instant {@link #nextPos()} dequeues the last (or only) candidate -- including the moment it becomes {@code
     * currentPos} and is actively mid-drain, still perfectly valid. Checking it here meant the tube shaft never
     * extended at all toward an isolated single-block source (confirmed live: an unpowered pump above one lone
     * water source resolved a correct {@code currentPos} but kept reporting {@code wantedLength: 0} forever), and
     * would retract one position early on any body's very last source block. {@link #paths} instead holds every
     * position {@link #buildQueue0} confirmed reachable and is only trimmed via {@link #mine()}'s own {@code
     * paths.remove(currentPos)} on a truly completed drain -- the right signal for "nothing left to reach at all".
     */
    @Override
    protected BlockPos getTargetPos() {
        if (paths.isEmpty()) {
            return null;
        }
        return targetPos;
    }

    /** Driven by the owning block's {@code getTicker}; was {@code TilePump#update()}. */
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
        if (tank.getAmountAsInt(0) > tank.getCapacity() / 2) {
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
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        input.child("tank").ifPresent(tank::deserialize);
        owner = input.read("owner", UUIDUtil.CODEC).orElse(null);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        tank.serialize(output.child("tank"));
        if (owner != null) {
            output.store("owner", UUIDUtil.CODEC, owner);
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
}
