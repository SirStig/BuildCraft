/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * Copyright (c) 2026 Joshua Kac -- NeoForge port (BuildCraft 10)
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL
 * was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.builders.tile;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.TreeSet;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

import buildcraft.api.mj.MjAPI;
import buildcraft.api.mj.MjBattery;
import buildcraft.api.properties.BuildCraftProperties;
import buildcraft.api.tiles.IDebuggable;
import buildcraft.api.tiles.ITileAreaProvider;

import buildcraft.lib.misc.BlockUtil;
import buildcraft.lib.misc.InventoryUtil;
import buildcraft.lib.misc.LocaleUtil;
import buildcraft.lib.misc.data.AxisOrder;
import buildcraft.lib.misc.data.Box;
import buildcraft.lib.misc.data.BoxIterator;
import buildcraft.lib.misc.data.EnumAxisOrder;
import buildcraft.lib.mj.MjBatteryReceiver;
import buildcraft.lib.tile.TileBC;

import buildcraft.core.marker.VolumeCache;
import buildcraft.core.marker.VolumeConnection;
import buildcraft.core.tile.TileMarkerVolume;

import buildcraft.BCBuildersRegistries;

/**
 * The port of 1.12.2's {@code TileQuarry}: claims a rectangular area (from an adjacent {@link ITileAreaProvider},
 * typically a {@link TileMarkerVolume} volume-marker box), builds a frame around it, then sweeps the enclosed
 * volume top-to-bottom, one Y layer at a time, breaking every mineable block along a fixed serpentine path and
 * pushing what it digs up into the best adjacent inventory (or dropping it, if there is none).
 *
 * <p><b>What ported unchanged:</b> the area-claiming logic in {@link #onPlacedBy} (an {@link ITileAreaProvider}
 * touching the quarry directly, or any {@link VolumeConnection} box whose expanded edge touches it -- 1.12.2's own
 * two-branch search, reproduced verbatim against this port's already-committed marker system), the frame-membership
 * bookkeeping ({@link #check}, {@link #frameBreakBlockPoses}/{@link #framePlaceFramePoses}, the incremental
 * {@link #toCheck} round-robin that avoids re-scanning the whole frame perimeter every tick), the ordered frame
 * placement walk ({@link #getFramePositions}), the boustrophedon dig order (a random per-position
 * {@link EnumAxisOrder}/{@link AxisOrder.Inversion} seed feeding a {@link BoxIterator}, exactly 1.12.2's
 * {@link #createBoxIterator}), and {@link BlockUtil#computeBlockBreakPower}-based MJ costing for every block broken.
 *
 * <p><b>What changed, and why:</b>
 * <ul>
 * <li><b>No {@code drillPos}/{@code Task} state machine.</b> 1.12.2's {@code TaskBreakBlock}/{@code TaskAddFrame}/
 *     {@code TaskMoveDrill} triplet existed to drive {@code RenderQuarry}'s animated drill head: a smoothly
 *     interpolated {@code Vec3d} position, synced to the client every tick, that the renderer read to draw the
 *     extending arms and lowering head. That renderer is not ported in this pass (see the class javadoc's own
 *     scope note below), so there is nothing left to feed a drill position to. This tile instead tracks one
 *     pending {@link Action} (break a frame obstruction, place a frame block, or mine the next dig position) and
 *     accumulates MJ toward it exactly the way {@code buildcraft.factory.tile.TileMiningWell} already does for its
 *     own single-block-at-a-time dig loop -- see that class for the established precedent this follows.</li>
 * <li><b>No separate "move the drill" MJ cost.</b> 1.12.2's {@code TaskMoveDrill} charged
 *     {@code distance * 20 MJ} to slide the (rendered) drill head between dig positions, rate-limited by
 *     {@code BCBuildersConfig.quarryMaxFrameMoveSpeed} -- which defaults to {@code 0} (no limit) in 1.12.2's own
 *     unconfigured settings, i.e. the throttle this drops was already a no-op under default configuration. No
 *     config system is ported yet (see {@link BlockUtil}'s own javadoc for the same call already made for
 *     {@code miningMultiplier}), so this keeps the unconfigured *outcome* (only the per-block break cost matters)
 *     without the dead throttle or the render-only travel-time cost it paced.</li>
 * <li><b>No {@code IWorldEventListener}.</b> 1.12.2 additionally re-checked a frame or mining position the instant
 *     any block in the world changed. {@code Level} exposes no such hook on this target (confirmed via
 *     {@code javap} against the merged jar -- there is no {@code addListener}/{@code EventListener} of any kind
 *     left, the same gap {@code TileMiningWell}'s own javadoc already documents). What is dropped is purely the
 *     "react within the same tick" optimisation: the round-robin {@link #toCheck} poll this class already runs
 *     every tick re-examines every frame position independently of any listener, so a frame block manually broken
 *     or a hole in the mining floor gets rebuilt/re-detected within a few seconds regardless -- just not
 *     instantly. The one behaviour this genuinely loses is {@code boxIterator.moveTo} snapping the dig cursor back
 *     to a spot that reopened after being visited; not reproduced.</li>
 * <li><b>No chunkloading.</b> {@code buildcraft.lib.chunkload.IChunkLoadingTile} exists on this port, but its
 *     backing {@code ChunkLoaderManager} does not -- its own javadoc already says this "follows once a machine
 *     (the quarry, the pump) actually needs it", i.e. this port already anticipated the quarry as the reason to
 *     eventually port it. That is real, separate infrastructure work (a NeoForge {@code TicketController}), out
 *     of scope for this pass; verified instead with a manual {@code /forceload} the way every other multi-chunk
 *     machine in this port's test rig already is.</li>
 * <li><b>No advancement unlock.</b> 1.12.2 fired {@code buildcraftbuilders:diggy_diggy_hole} on finishing a
 *     64x64 dig. No advancement pack exists for this port's single {@code buildcraft} mod id yet, so this is
 *     simply left out rather than wired to a placeholder id.</li>
 * <li><b>{@code IAreaProvider}/{@code VolumeCache}/{@code VolumeConnection}/{@code TileMarkerVolume}</b> keep their
 *     1.12.2 names and shapes on this port (see each class's own javadoc), so {@link #onPlacedBy} below is close
 *     to a direct port rather than a redesign -- the one substantive change is that {@code IAreaProvider} is
 *     {@link ITileAreaProvider} here (an {@code instanceof} check against block *entities*, unchanged in spirit).</li>
 * </ul>
 *
 * <p><b>Rendering scope cut:</b> {@code buildcraft.builders.client.render.RenderQuarry} (the animated frame/arm/head
 * renderer) is not ported in this pass -- it is a genuinely separate, large piece of client-only geometry, and this
 * pass prioritises a working, MJ-metered, save-safe digging machine over a matching visual. The quarry block itself
 * renders as a plain textured cube (see {@code BCBuildersRegistries}), and the frame border blocks it places while
 * working render as plain cubes too (see {@code buildcraft.builders.block.BlockFrame}'s own javadoc for that same
 * cut applied to the connected-strut geometry). Both are real, solid blocks in the world regardless -- only the
 * cosmetic animation is missing.
 */
public class TileQuarry extends TileBC implements IDebuggable {

    private static final long MAX_POWER_PER_TICK = 512 * MjAPI.MJ;
    /** {@code BCBuildersConfig.quarryMaxTasksPerTick}'s unconfigured default; see the class javadoc for why no
     * config system is stood up for this one field. */
    private static final int MAX_TASKS_PER_TICK = 4;
    /** {@code TaskAddFrame#getTarget()}'s constant MJ cost to place one frame block. */
    private static final long FRAME_PLACE_COST = 24 * MjAPI.MJ;

    private final MjBattery battery = new MjBattery(24_000 * MjAPI.MJ);
    public final MjBatteryReceiver mjReceiver = new MjBatteryReceiver(battery);

    public final Box frameBox = new Box();
    private final Box miningBox = new Box();
    @Nullable
    private BoxIterator boxIterator;
    public final List<BlockPos> framePoses = new ArrayList<>();
    private int frameBoxPosesCount = 0;
    private final LinkedList<BlockPos> toCheck = new LinkedList<>();
    private final Set<BlockPos> firstCheckedPoses = new HashSet<>();
    private boolean firstChecked = false;
    /** Ordered nearest-first, like 1.12.2's own {@code BlockUtil.uniqueBlockPosComparator}-wrapped comparator --
     * a plain distance comparator alone would make every pair of equidistant positions compare as "equal" and
     * silently vanish from the {@link TreeSet}, so ties are broken by raw coordinates. */
    private final Set<BlockPos> frameBreakBlockPoses = new TreeSet<>(
        Comparator.<BlockPos>comparingDouble(p -> worldPosition.distSqr(p))
            .thenComparingInt(Vec3i::getX).thenComparingInt(Vec3i::getY).thenComparingInt(Vec3i::getZ)
    );
    private final Set<BlockPos> framePlaceFramePoses = new HashSet<>();

    @Nullable
    private Action currentAction;
    private BlockPos actionPos = BlockPos.ZERO;
    private long actionProgress = 0;

    public TileQuarry(BlockPos pos, BlockState state) {
        super(BCBuildersRegistries.QUARRY_TYPE.get(), pos, state);
    }

    private enum Action {
        BREAK_OBSTRUCTION,
        PLACE_FRAME,
        MINE
    }

    @Nullable
    public BlockPos getActionPos() {
        return currentAction == null ? null : actionPos;
    }

    private BoxIterator createBoxIterator() {
        long x = worldPosition.getX();
        long y = worldPosition.getY();
        long z = worldPosition.getZ();
        long seed = (x & 0xFFFF) | ((y & 0xFFFF) << 16) | ((z & 0xFFFFL) << 32);

        Random rand = new Random(seed);
        EnumAxisOrder axisOrder = rand.nextBoolean() ? EnumAxisOrder.XZY : EnumAxisOrder.ZXY;
        AxisOrder.Inversion inv = AxisOrder.Inversion.getFor(rand.nextBoolean(), rand.nextBoolean(), false);
        return new BoxIterator(miningBox, AxisOrder.getFor(axisOrder, inv), true);
    }

    /** Gets the current positions where frame blocks should be placed, in placement order. Ported unchanged from
     * 1.12.2 -- see that class's own javadoc for the algorithm (a breadth-first walk outward from this tile along
     * the frame's edge, shuffled at each step so the frame doesn't visibly build in a single sweep). */
    private List<BlockPos> getFramePositions() {
        Set<BlockPos> visitedSet = new HashSet<>();
        List<BlockPos> framePositions = new ArrayList<>();

        List<BlockPos> openSet = new ArrayList<>();
        List<BlockPos> nextOpenSet = new ArrayList<>();
        openSet.add(worldPosition);

        Direction[] order = Direction.values();

        int maxIterationCount = frameBox.getBlocksOnEdgeCount();
        int iterationCount = 0;
        do {
            for (BlockPos p : openSet) {
                Collections.shuffle(Arrays.asList(order));
                for (Direction face : order) {
                    BlockPos next = p.relative(face);
                    if (frameBox.isOnEdge(next) && visitedSet.add(next)) {
                        nextOpenSet.add(next);
                        framePositions.add(next);
                    }
                }
            }
            openSet.clear();
            List<BlockPos> t = openSet;
            openSet = nextOpenSet;
            nextOpenSet = t;
            Collections.shuffle(openSet);

            if (openSet.size() > 8 * 3) {
                throw new IllegalStateException(
                    "OpenSet got too big! pos=" + worldPosition + " frameBox=" + frameBox
                );
            }
            iterationCount++;
            if (iterationCount >= maxIterationCount) {
                throw new IllegalStateException(
                    "Failed to generate frame positions -- was the frame box wrong? pos=" + worldPosition
                        + " frameBox=" + frameBox
                );
            }
        } while (!openSet.isEmpty());

        if (framePositions.isEmpty()) {
            throw new IllegalStateException(
                "Failed to generate frame positions -- was the frame box wrong? pos=" + worldPosition
                    + " frameBox=" + frameBox
            );
        }
        return framePositions;
    }

    private boolean shouldBeFrame(BlockPos p) {
        return frameBox.isOnEdge(p);
    }

    /** Claims a mining area, either from an {@link ITileAreaProvider} touching this tile directly, from any
     * {@link VolumeConnection} volume-marker box whose (expanded) edge touches it, or -- failing both -- a
     * fixed-size default box extending away from the direction this quarry faces. Ported from 1.12.2's
     * {@code onPlacedBy}, unchanged in spirit; see the class javadoc for what {@code IAreaProvider} became. */
    public void onPlacedBy(LivingEntity placer) {
        if (level == null || level.isClientSide()) {
            return;
        }
        Direction facing = getBlockState().getValue(BuildCraftProperties.BLOCK_FACING);
        BlockPos areaPos = worldPosition.relative(facing.getOpposite());
        BlockPos min = null, max = null;

        if (level.getBlockEntity(areaPos) instanceof ITileAreaProvider provider) {
            min = provider.min();
            max = provider.max();
            int dx = max.getX() - min.getX();
            int dz = max.getZ() - min.getZ();
            if (dx < 3 || dz < 3) {
                min = null;
                max = null;
            } else {
                provider.removeFromWorld();
            }
        }

        if (min == null) {
            var subCache = VolumeCache.INSTANCE.getSubCache(level);
            for (BlockPos markerPos : subCache.getAllMarkers()) {
                var marker = subCache.getMarker(markerPos);
                if (marker == null) {
                    continue;
                }
                VolumeConnection connection = marker.getCurrentConnection();
                if (connection == null) {
                    continue;
                }
                Box volBox = connection.getBox();
                Box box2 = new Box();
                box2.initialize(volBox);
                if (!box2.isInitialized()) {
                    continue;
                }
                if (worldPosition.getY() != box2.min().getY()) {
                    continue;
                }
                if (box2.contains(worldPosition)) {
                    continue;
                }
                if (!box2.contains(areaPos)) {
                    continue;
                }
                if (box2.size().getX() < 3 || box2.size().getZ() < 3) {
                    continue;
                }
                box2.expand(1);
                box2.setMin(box2.min().above());
                if (box2.isOnEdge(worldPosition)) {
                    min = volBox.min();
                    max = volBox.max();
                    if (marker instanceof ITileAreaProvider markerArea) {
                        markerArea.removeFromWorld();
                    }
                    break;
                }
            }
        }

        if (min == null) {
            switch (facing.getOpposite()) {
                case WEST -> {
                    min = worldPosition.offset(-11, 0, -5);
                    max = worldPosition.offset(-1, 4, 5);
                }
                case SOUTH -> {
                    min = worldPosition.offset(-5, 0, 1);
                    max = worldPosition.offset(5, 4, 11);
                }
                case NORTH -> {
                    min = worldPosition.offset(-5, 0, -11);
                    max = worldPosition.offset(5, 4, -1);
                }
                default -> {
                    min = worldPosition.offset(1, 0, -5);
                    max = worldPosition.offset(11, 4, 5);
                }
            }
        }

        final int quarryFrameMinHeight = 4;
        if (max.getY() - min.getY() < quarryFrameMinHeight) {
            max = new BlockPos(max.getX(), min.getY() + quarryFrameMinHeight, max.getZ());
        }
        if (level.isOutsideBuildHeight(max)) {
            int dist = max.getY() - min.getY();
            min = min.below(dist);
            max = max.below(dist);
        }

        frameBox.reset();
        frameBox.setMin(min);
        frameBox.setMax(max);
        miningBox.reset();
        final int miningMaxDepth = 512;
        int minY = max.getY() - 1 - miningMaxDepth;
        if (level.isOutsideBuildHeight(new BlockPos(min.getX(), minY, min.getZ()))) {
            minY = level.getMinY();
        }
        miningBox.setMin(new BlockPos(min.getX() + 1, minY, min.getZ() + 1));
        miningBox.setMax(new BlockPos(max.getX() - 1, max.getY() - 1, max.getZ() - 1));
        updatePoses();
        markDirtyAndSync();
    }

    private boolean canMine(BlockPos pos) {
        if (BlockUtil.isUnbreakableBlock(level, pos)) {
            return false;
        }
        FluidState fluid = BlockUtil.getFluidWithFlowing(level, pos);
        return fluid == null || fluid.getType().getFluidType().getViscosity(fluid, level, pos) <= 1000;
    }

    private boolean canMoveThrough(BlockPos pos) {
        if (level.getBlockState(pos).isAir()) {
            return true;
        }
        FluidState fluid = BlockUtil.getFluidWithFlowing(level, pos);
        return fluid != null && fluid.getType().getFluidType().getViscosity(fluid, level, pos) <= 1000;
    }

    private boolean canMoveDownTo(BlockPos pos) {
        for (int y = miningBox.max().getY(); y > pos.getY(); y--) {
            if (!canMoveThrough(new BlockPos(pos.getX(), y, pos.getZ()))) {
                return false;
            }
        }
        return true;
    }

    /** {@code true} if the position holds a genuine obstruction (neither air nor a passable fluid) that has to be
     * broken before the frame/dig sweep can treat that position as clear. */
    private boolean canIgnoreInFrameBox(BlockPos pos) {
        return !level.getBlockState(pos).isAir() && BlockUtil.getFluidWithFlowing(level, pos) == null;
    }

    private void check(BlockPos pos) {
        frameBreakBlockPoses.remove(pos);
        framePlaceFramePoses.remove(pos);
        if (shouldBeFrame(pos)) {
            if (!level.getBlockState(pos).is(BCBuildersRegistries.FRAME.get())) {
                if (canIgnoreInFrameBox(pos)) {
                    frameBreakBlockPoses.add(pos);
                } else {
                    framePlaceFramePoses.add(pos);
                }
            }
        } else if (canIgnoreInFrameBox(pos)) {
            frameBreakBlockPoses.add(pos);
        }
        if (!firstChecked) {
            firstCheckedPoses.add(pos);
            if (firstCheckedPoses.size() >= frameBoxPosesCount) {
                firstChecked = true;
            }
        }
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (level != null && !level.isClientSide()) {
            updatePoses();
        }
    }

    /** Clears every frame block this quarry ever placed when the quarry itself is removed -- 1.12.2's
     * {@code BlockQuarry#breakBlock}. Reached from {@link net.minecraft.world.level.block.entity.BlockEntity
     * #preRemoveSideEffects}, this target's replacement for {@code TileBC_Neptune#onRemove()} (see
     * {@code buildcraft.factory.tile.TileMiner}'s own javadoc for the same hook already established there). */
    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        super.preRemoveSideEffects(pos, state);
        if (level != null && !level.isClientSide()) {
            for (BlockPos framePos : framePoses) {
                if (level.getBlockState(framePos).is(BCBuildersRegistries.FRAME.get())) {
                    level.removeBlock(framePos, false);
                }
            }
        }
    }

    private void updatePoses() {
        framePoses.clear();
        frameBoxPosesCount = 0;
        toCheck.clear();
        firstCheckedPoses.clear();
        firstChecked = false;
        frameBreakBlockPoses.clear();
        framePlaceFramePoses.clear();
        if (level == null) {
            return;
        }
        if (getBlockState().is(BCBuildersRegistries.QUARRY.get()) && frameBox.isInitialized()) {
            List<BlockPos> blocksInArea = frameBox.getBlocksInArea();
            blocksInArea.sort(Comparator.comparingDouble(worldPosition::distSqr));
            frameBoxPosesCount = blocksInArea.size();
            toCheck.addAll(blocksInArea);
            framePoses.addAll(getFramePositions());
        }
    }

    /** Driven by {@code BlockQuarry}'s {@code getTicker}. */
    public void serverTick() {
        if (!frameBox.isInitialized() || !miningBox.isInitialized()) {
            return;
        }

        if (!toCheck.isEmpty()) {
            int n = firstChecked ? 10 : 500;
            for (int i = 0; i < n && !toCheck.isEmpty(); i++) {
                BlockPos p = toCheck.pollFirst();
                check(p);
                toCheck.addLast(p);
            }
        }
        if (!firstChecked) {
            return;
        }

        long max;
        if (battery.getStored() > battery.getCapacity() / 2) {
            max = MAX_POWER_PER_TICK;
        } else {
            long roundedUp = battery.getStored() + MAX_POWER_PER_TICK / 2;
            max = MAX_POWER_PER_TICK * roundedUp / (battery.getCapacity() / 2);
            max = Math.max(0, Math.min(max, MAX_POWER_PER_TICK));
        }

        boolean changed = false;
        for (int i = 0; i < MAX_TASKS_PER_TICK && max > 0; i++) {
            if (currentAction == null) {
                pickNextAction();
                if (currentAction == null) {
                    break;
                }
            }
            long target = actionTarget();
            long need = Math.max(0, target - actionProgress);
            long drawn = battery.extractPower(0, Math.min(max, need));
            max -= drawn;
            actionProgress += drawn;
            if (actionProgress >= target) {
                completeAction();
                actionProgress = 0;
                currentAction = null;
                changed = true;
            } else if (currentAction == Action.MINE || currentAction == Action.BREAK_OBSTRUCTION) {
                if (!level.getBlockState(actionPos).isAir()) {
                    level.destroyBlockProgress(actionPos.hashCode(), actionPos, (int) (actionProgress * 9 / target));
                }
                break;
            } else {
                break;
            }
        }
        if (changed) {
            setChanged();
        }
    }

    /** Only ever called while {@link #currentAction} is non-null -- see {@link #serverTick}. */
    private long actionTarget() {
        return switch (currentAction) {
            case PLACE_FRAME -> FRAME_PLACE_COST;
            case BREAK_OBSTRUCTION, MINE -> BlockUtil.computeBlockBreakPower(level, actionPos);
        };
    }

    private void pickNextAction() {
        if (!frameBreakBlockPoses.isEmpty()) {
            BlockPos p = frameBreakBlockPoses.iterator().next();
            check(p);
            if (frameBreakBlockPoses.contains(p) && canMine(p)) {
                currentAction = Action.BREAK_OBSTRUCTION;
                actionPos = p;
            }
            return;
        }
        if (!framePlaceFramePoses.isEmpty()) {
            for (BlockPos p : framePoses) {
                if (!framePlaceFramePoses.contains(p)) {
                    continue;
                }
                check(p);
                if (framePlaceFramePoses.contains(p)) {
                    currentAction = Action.PLACE_FRAME;
                    actionPos = p;
                    return;
                }
            }
            return;
        }

        if (boxIterator == null) {
            boxIterator = createBoxIterator();
        }
        while (boxIterator.hasNext() && !canDig(boxIterator.getCurrent())) {
            boxIterator.advance();
        }
        if (boxIterator.hasNext() && canDig(boxIterator.getCurrent())) {
            currentAction = Action.MINE;
            actionPos = boxIterator.getCurrent();
        }
        // If the iterator has run out, the whole claimed area is dug out -- nothing left to do until either the
        // frame or a manually-placed obstruction gives it new work via check().
    }

    private boolean canDig(BlockPos pos) {
        return !canMoveThrough(pos) && canMine(pos) && canMoveDownTo(pos);
    }

    private void completeAction() {
        switch (currentAction) {
            case BREAK_OBSTRUCTION -> {
                if (canMine(actionPos) && level instanceof ServerLevel serverLevel) {
                    level.destroyBlockProgress(actionPos.hashCode(), actionPos, -1);
                    // Frame-clearing breaks discard their drops -- these are just terrain in the way, not blocks
                    // the player asked the quarry to mine. Matches 1.12.2's own drillPos == null branch.
                    BlockUtil.breakBlockAndGetDrops(serverLevel, actionPos, new ItemStack(Items.DIAMOND_PICKAXE));
                }
                check(actionPos);
            }
            case PLACE_FRAME -> {
                if (canIgnoreInFrameBox(actionPos)) {
                    // Something reoccupied this position while we were charging up to place the frame block --
                    // re-check it instead of placing into it.
                    check(actionPos);
                } else {
                    level.setBlockAndUpdate(actionPos, BCBuildersRegistries.FRAME.get().defaultBlockState());
                    check(actionPos);
                }
            }
            case MINE -> {
                if (canMine(actionPos) && level instanceof ServerLevel serverLevel) {
                    level.destroyBlockProgress(actionPos.hashCode(), actionPos, -1);
                    BlockUtil.breakBlockAndGetDrops(serverLevel, actionPos, new ItemStack(Items.DIAMOND_PICKAXE))
                        .ifPresent(drops -> drops.forEach(
                            stack -> InventoryUtil.addToBestAcceptor(level, worldPosition, null, stack)
                        ));
                }
                if (boxIterator != null) {
                    boxIterator.advance();
                }
            }
        }
    }

    // NBT

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.store("box", CompoundTag.CODEC, miningBox.writeToNBT());
        output.store("frame", CompoundTag.CODEC, frameBox.writeToNBT());
        if (boxIterator != null) {
            output.store("boxIterator", CompoundTag.CODEC, boxIterator.writeToNbt());
        }
        output.putLong("battery", battery.getStored());
        if (currentAction != null) {
            output.putString("currentAction", currentAction.name());
            output.store("actionPos", CompoundTag.CODEC, writeBlockPosNbt(actionPos));
            output.putLong("actionProgress", actionProgress);
        }
        output.putBoolean("firstChecked", firstChecked);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        miningBox.initialize(input.read("box", CompoundTag.CODEC).orElseGet(CompoundTag::new));
        frameBox.initialize(input.read("frame", CompoundTag.CODEC).orElseGet(CompoundTag::new));
        boxIterator = input.read("boxIterator", CompoundTag.CODEC).map(BoxIterator::readFromNbt).orElse(null);
        battery.setStored(input.getLongOr("battery", 0));
        String actionName = input.getStringOr("currentAction", "");
        if (!actionName.isEmpty()) {
            currentAction = Action.valueOf(actionName);
            actionPos = input.read("actionPos", CompoundTag.CODEC).map(TileQuarry::readBlockPosNbt).orElse(BlockPos.ZERO);
            actionProgress = input.getLongOr("actionProgress", 0);
        } else {
            currentAction = null;
            actionProgress = 0;
        }
        firstChecked = input.getBooleanOr("firstChecked", false);

        // Validation -- was this actually a matching frame/mining box pair, or did loading fail partway?
        boolean isValid = frameBox.isInitialized() && miningBox.isInitialized();
        if (isValid) {
            int fx0 = frameBox.min().getX(), fy0 = frameBox.min().getY(), fz0 = frameBox.min().getZ();
            int fx1 = frameBox.max().getX(), fy1 = frameBox.max().getY(), fz1 = frameBox.max().getZ();
            int mx0 = miningBox.min().getX(), my0 = miningBox.min().getY(), mz0 = miningBox.min().getZ();
            int mx1 = miningBox.max().getX(), my1 = miningBox.max().getY(), mz1 = miningBox.max().getZ();
            isValid = fx0 + 1 == mx0 && fx1 - 1 == mx1 && fz0 + 1 == mz0 && fz1 - 1 == mz1
                && fy0 >= my0 && fy1 - 1 == my1;
        }
        if (!isValid) {
            frameBox.reset();
            miningBox.reset();
        }
    }

    private static CompoundTag writeBlockPosNbt(BlockPos pos) {
        CompoundTag nbt = new CompoundTag();
        nbt.putInt("x", pos.getX());
        nbt.putInt("y", pos.getY());
        nbt.putInt("z", pos.getZ());
        return nbt;
    }

    private static BlockPos readBlockPosNbt(CompoundTag nbt) {
        return new BlockPos(nbt.getIntOr("x", 0), nbt.getIntOr("y", 0), nbt.getIntOr("z", 0));
    }

    // IDebuggable

    @Override
    public void getDebugInfo(List<String> left, List<String> right, @Nullable Direction side) {
        left.add("battery = " + battery.getDebugString());
        left.add("frameBox = " + frameBox.min() + " -> " + frameBox.max());
        left.add("miningBox = " + miningBox.min() + " -> " + miningBox.max());
        left.add("firstChecked = " + firstChecked + " (" + firstCheckedPoses.size() + "/" + frameBoxPosesCount + ")");
        left.add("frameBreakBlockPoses = " + frameBreakBlockPoses.size());
        left.add("framePlaceFramePoses = " + framePlaceFramePoses.size());
        left.add("current = " + (boxIterator == null ? "null" : boxIterator.getCurrent()));
        left.add("action = " + currentAction + " @ " + actionPos + " (" + LocaleUtil.localizeMj(actionProgress) + ")");
    }
}
