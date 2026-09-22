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
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.UUIDUtil;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

import net.neoforged.neoforge.fluids.FluidType;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;

import buildcraft.api.tiles.IDebuggable;

import buildcraft.lib.fluid.Tank;
import buildcraft.lib.misc.AdvancementUtil;
import buildcraft.lib.misc.BlockUtil;
import buildcraft.lib.misc.FluidUtilBC;
import buildcraft.lib.tile.TileBC;

import buildcraft.BCFactoryRegistries;

/**
 * Given a source fluid piped into {@link #tank}, searches outward through open (air, or already-same-fluid) space
 * along up to 4 of its 5 non-top sides and spreads that fluid into the world, one source block at a time, on a
 * slow, self-throttling cadence -- BuildCraft's water/lava spreader. {@link #buildQueue()} is the real algorithm
 * here, and is genuinely novel for this pass, not boilerplate; the rest of the class is the same
 * "own a {@link Tank}, tick, persist, debug" shape every other {@code factory} machine this session already
 * established.
 *
 * <p><b>The search ({@link #buildQueue()}) is a breadth-first walk, structurally close to {@link TilePump}'s own
 * search but inverted</b> -- a pump walks outward through a body of fluid looking for source blocks to drain, a
 * flood gate walks outward through open space (and through its own previously-placed fluid) looking for positions
 * to fill. Starting from the block's open sides ({@link #openSides}), it explores outward (capped at distance
 * squared {@code 64 * 64} via {@link #SEARCH_MAX_DISTANCE}, and at a hard {@link #QUEUE_MAX_SIZE} positions
 * queued) through every position that is either air or already holds the flood gate's own fluid: {@link #canFill}
 * decides whether a position is an actual fill target (air, or an existing <em>flowing</em> -- not source --
 * stretch of the same fluid, which placing a source there upgrades), and {@link #canSearch} decides whether the
 * walk may continue past a position at all (anything {@link #canFill} accepts, plus an existing <em>source</em>
 * block of the same fluid, which the search may pass through without trying to refill). Every reachable fill
 * target is pushed onto {@link #queue}; every position visited also gets its route back to the flood gate itself
 * recorded in {@link #paths}, so {@link #serverTick()} can later re-walk that specific route and refuse to place
 * fluid through a path that has since been cut off (a neighbour block placed mid-stream, say) -- exactly what
 * 1.12.2's own comment above {@code buildQueue}/{@code update} describes this mechanism for.
 *
 * <p><b>A faithfully-preserved 1.12.2 bug, found while verifying this method against the original: the path
 * re-validation in {@link #serverTick()} does not actually validate the path.</b> 1.12.2's {@code update()} loops
 * over every intermediate position {@code p} on {@code paths.get(currentPos)} but calls
 * {@code canFillThrough(currentPos)} inside that loop -- the loop variable {@code p} is never passed to
 * {@code canFillThrough} at all, only the (loop-invariant) final position. The net effect is that the "revalidate
 * the whole path" the surrounding comment and this class's own javadoc above describe collapses, in the actual
 * 1.12.2 binary, into "revalidate {@code currentPos} itself, once per path element" -- every intermediate step on
 * the route is never independently checked. This looks like a genuine typo in the upstream source (looping over
 * {@code p} and never reading it), not an intentional shortcut. Per this port's established precedent for
 * preserved-not-fixed upstream quirks (see {@code BlockMarkerVolume}'s own {@code neighborChanged} javadoc), this
 * is ported <em>as written</em>, bug included, rather than silently corrected -- flagged here for whoever next
 * touches this method.
 *
 * <p><b>{@code isGaseous}</b> follows the exact density-based convention {@link TilePump} already established for
 * this target (no {@code isGaseous}-shaped method exists on {@code FluidType} here at all; see that class's own
 * javadoc) -- water/lava both sink, so {@link #SEARCH_NORMAL} (down-biased) is what actually runs in practice, the
 * same way {@link TilePump#SEARCH_GASEOUS} is currently dead weight there too.
 *
 * <p><b>Fluid placement needs no {@code FakePlayer}, confirmed rather than assumed.</b> 1.12.2's
 * {@code FluidUtil.tryPlaceFluid(fakePlayer, world, pos, tank, fluid)} only needed a player because that Forge API
 * is player-shaped; the actual work it does is drain-on-success-only placement. {@link #canFill} only ever
 * accepts two kinds of target: plain air, or an existing <em>flowing</em> (non-source) block of the tank's own
 * fluid -- confirmed by reading {@link FluidUtilBC#getFluidSource(BlockState)}'s "must not already be a source"
 * guard inside {@link #canFill} itself. Neither case is ever a {@code LiquidBlockContainer} (a cauldron and
 * friends): a container block is neither air nor a fluid block in the sense {@link BlockUtil#getFluidWithFlowing}
 * reads, so it can never satisfy {@link #canFill}'s non-air branch. That means the
 * {@code LiquidBlockContainer#placeLiquid} half of the modern placement API this class's task brief flagged as
 * worth checking turns out to have no real target here at all -- every position {@link #placeFluid} is ever asked
 * to fill is directly, unconditionally placeable via {@link net.minecraft.world.level.Level#setBlock}, the same
 * primitive {@code FluidState#createLegacyBlock()} exists for. No player object of any kind is needed, matching
 * the precedent {@code BlockUtil#breakBlockAndGetDrops} already set for a different machine's own dropped
 * {@code FakePlayer} dependency (see that class's own javadoc) -- and, independently, {@code BuildCraftAPI
 * .fakePlayerProvider} is never assigned anywhere in this port yet, so routing through it here would have been a
 * guaranteed {@code NullPointerException} regardless.
 *
 * <p><b>{@link #openSides} is real, persistent gameplay state</b> (unlike the purely-cosmetic {@code CONNECTED_MAP}
 * blockstate 1.12.2 synthesised from it in {@code BlockFloodGate#getActualState}, which has no modern hook to be
 * synthesised from any more and is dropped, matching every other block-metadata-visualisation drop already made
 * this session) -- it genuinely gates which directions {@link #buildQueue()} is allowed to explore, is toggled by
 * a real wrench interaction ({@link buildcraft.factory.block.BlockFloodGate#useItemOn}), and is persisted as a
 * plain bitmask int (one bit per {@link Direction#ordinal()}) rather than 1.12.2's {@code NBTTagByteArray}/
 * {@code NBTPrimitive} dual-format reader -- there are no old BuildCraft saves for this fresh port to stay
 * compatible with, so the "7.99.7 and before" legacy-array branch is dropped outright, matching how
 * {@code TileMiner}'s own {@code migrateOldNBT} was already dropped for the same reason.
 *
 * <p><b>No client sync exists for {@link #openSides} beyond {@link #markDirtyAndSync()}'s ordinary full-state
 * push</b> -- 1.12.2's id-tagged {@code writePayload}/{@code readPayload}/{@code MessageUtil.writeEnumSet}/
 * {@code readEnumSet} pair only ever existed to feed the also-dropped {@code CONNECTED_MAP} renderer hint; there
 * is no renderer in this port to consume a faster-than-full-save sync of this field at all, so
 * {@link #toggleOpenSide} simply calls {@link #markDirtyAndSync()} (which already saves and syncs every persisted
 * field, {@link #openSides} included) rather than standing up a bespoke payload for a value nothing client-side
 * reads yet -- the same "{@code TileBC}'s full-state sync already covers this" reasoning
 * {@code TileMarkerVolume#showSignals} already used for its own dropped id-tagged payload pair.
 */
public class TileFloodGate extends TileBC implements IDebuggable {
    private static final Direction[] SEARCH_NORMAL = { //
        Direction.DOWN, Direction.NORTH, Direction.SOUTH, //
        Direction.WEST, Direction.EAST //
    };
    private static final Direction[] SEARCH_GASEOUS = { //
        Direction.UP, Direction.NORTH, Direction.SOUTH, //
        Direction.WEST, Direction.EAST //
    };

    private static final Set<Direction> DEFAULT_OPEN_SIDES = EnumSet.complementOf(EnumSet.of(Direction.UP));

    private static final int DEFAULT_OPEN_MASK;
    static {
        int mask = 0;
        for (Direction face : DEFAULT_OPEN_SIDES) {
            mask |= 1 << face.ordinal();
        }
        DEFAULT_OPEN_MASK = mask;
    }

    private static final Identifier ADVANCEMENT_FLOOD_SINGLE =
        Identifier.fromNamespaceAndPath("buildcraftfactory", "flooding_the_world");

    private static final int[] REBUILD_DELAYS = { 16, 32, 64, 128, 256 };
    private static final int SEARCH_MAX_DISTANCE = 64;
    private static final int QUEUE_MAX_SIZE = 4096;

    public final Tank tank = new Tank(2 * FluidType.BUCKET_VOLUME, this::markDirtyAndSync);
    public final Set<Direction> openSides = EnumSet.copyOf(DEFAULT_OPEN_SIDES);
    public final Deque<BlockPos> queue = new ArrayDeque<>();
    private final Map<BlockPos, List<BlockPos>> paths = new HashMap<>();
    private int delayIndex = 0;
    private int tick = 0;

    @Nullable
    private UUID owner;

    public TileFloodGate(BlockPos pos, BlockState state) {
        super(BCFactoryRegistries.FLOOD_GATE_TYPE.get(), pos, state);
    }

    public void onPlacedBy(@Nullable LivingEntity placer) {
        owner = placer == null ? null : placer.getUUID();
    }

    private int getCurrentDelay() {
        return REBUILD_DELAYS[delayIndex];
    }

    private void buildQueue() {
        queue.clear();
        paths.clear();
        FluidResource fluid = tank.getFluidType();
        if (fluid.isEmpty() || tank.getAmountAsInt(0) <= 0) {
            return;
        }
        Set<BlockPos> checked = new HashSet<>();
        checked.add(worldPosition);
        List<BlockPos> nextPosesToCheck = new ArrayList<>();
        for (Direction face : openSides) {
            BlockPos offset = worldPosition.relative(face);
            nextPosesToCheck.add(offset);
            paths.put(offset, List.of(offset));
        }
        Direction[] directions = isGaseous(fluid) ? SEARCH_GASEOUS : SEARCH_NORMAL;
        int maxDistSq = SEARCH_MAX_DISTANCE * SEARCH_MAX_DISTANCE;
        outer: while (!nextPosesToCheck.isEmpty()) {
            List<BlockPos> nextPosesToCheckCopy = new ArrayList<>(nextPosesToCheck);
            nextPosesToCheck.clear();
            for (BlockPos toCheck : nextPosesToCheckCopy) {
                if (toCheck.distSqr(worldPosition) > maxDistSq) {
                    continue;
                }
                if (!checked.add(toCheck)) {
                    continue;
                }
                if (!canSearch(toCheck)) {
                    continue;
                }
                if (canFill(toCheck)) {
                    queue.push(toCheck);
                    if (queue.size() >= QUEUE_MAX_SIZE) {
                        break outer;
                    }
                }
                List<BlockPos> checkPath = paths.get(toCheck);
                for (Direction side : directions) {
                    BlockPos next = toCheck.relative(side);
                    if (checked.contains(next)) {
                        continue;
                    }
                    List<BlockPos> nextPath = new ArrayList<>(checkPath);
                    nextPath.add(next);
                    paths.put(next, List.copyOf(nextPath));
                    nextPosesToCheck.add(next);
                }
            }
        }
    }

    private static boolean isGaseous(FluidResource fluid) {
        return fluid.getFluidType().getDensity() < 0;
    }

    private boolean canFill(BlockPos offsetPos) {
        if (level.isEmptyBlock(offsetPos)) {
            return true;
        }
        FluidState fluidState = BlockUtil.getFluidWithFlowing(level, offsetPos);
        return fluidState != null && FluidUtilBC.areFluidsEqual(fluidState.getType(), tank.getFluidType().getFluid())
            && FluidUtilBC.getFluidSource(level.getBlockState(offsetPos)) == null;
    }

    private boolean canSearch(BlockPos offsetPos) {
        if (canFill(offsetPos)) {
            return true;
        }
        Fluid fluid = FluidUtilBC.getFluidSource(level, offsetPos);
        return FluidUtilBC.areFluidsEqual(fluid, tank.getFluidType().getFluid());
    }

    private boolean canFillThrough(BlockPos pos) {
        if (level.isEmptyBlock(pos)) {
            return false;
        }
        FluidState fluidState = BlockUtil.getFluidWithFlowing(level, pos);
        return fluidState != null && FluidUtilBC.areFluidsEqual(fluidState.getType(), tank.getFluidType().getFluid());
    }

    /** Places a source block of {@code fluid} at {@code placePos} -- always air or an existing flowing block of
     * the same fluid by the time this is called (both {@link #canFill} outcomes; see the class javadoc), so this
     * is a plain, unconditional {@link net.minecraft.world.level.Level#setBlock} rather than a
     * {@code LiquidBlockContainer}-aware dance. */
    private void placeFluid(BlockPos placePos, Fluid fluid) {
        level.setBlock(placePos, fluid.defaultFluidState().createLegacyBlock(), Block.UPDATE_ALL);
    }

    // TileEntity

    /** Driven by {@link buildcraft.factory.block.BlockFloodGate#getTicker}; was {@code TileFloodGate#update()}.
     *
     * <p>Rebuilds {@link #queue} on an exponentially-backing-off delay ({@link #REBUILD_DELAYS}, one step up each
     * time the queue empties with nothing left to place, reset to the fastest delay the moment a placement
     * actually succeeds), and, once every 16 ticks, attempts exactly one placement from the queue if {@link #tank}
     * holds at least a full bucket -- unchanged 1.12.2 pacing, not an arbitrary rewrite. */
    public void serverTick() {
        if (tank.getAmountAsInt(0) < FluidType.BUCKET_VOLUME) {
            return;
        }

        tick++;
        if (tick % 16 == 0 && !tank.isEmpty() && !queue.isEmpty()) {
            BlockPos currentPos = queue.removeLast();
            List<BlockPos> path = paths.get(currentPos);
            boolean canFillPath = true;
            if (path != null) {
                for (BlockPos p : path) {
                    if (p.equals(currentPos)) {
                        continue;
                    }
                    // Faithfully reproduces a 1.12.2 bug: this should test `p`, not `currentPos` -- see the class
                    // javadoc.
                    if (!canFillThrough(currentPos)) {
                        canFillPath = false;
                        break;
                    }
                }
            }
            if (canFillPath && canFill(currentPos)) {
                FluidResource fluidResource = tank.getFluidType();
                placeFluid(currentPos, fluidResource.getFluid());
                try (Transaction transaction = Transaction.openRoot()) {
                    tank.extract(0, fluidResource, FluidType.BUCKET_VOLUME, transaction);
                    transaction.commit();
                }
                if (owner != null) {
                    AdvancementUtil.unlockAdvancement(owner, ADVANCEMENT_FLOOD_SINGLE);
                }
                delayIndex = 0;
                tick = 0;
            } else {
                buildQueue();
            }
        }

        if (queue.isEmpty() && tick >= getCurrentDelay()) {
            delayIndex = Math.min(delayIndex + 1, REBUILD_DELAYS.length - 1);
            tick = 0;
            buildQueue();
        }
    }

    /** Wrench-driven: flips whether {@link #buildQueue()}'s search may explore {@code side}, then clears
     * {@link #queue} so the very next {@link #serverTick()} rebuilds against the new configuration -- without
     * this, an already-full queue would keep placing fluid through the old configuration until it happened to
     * drain on its own. Was {@code BlockFloodGate#onBlockActivated}'s inline body; the block now calls this
     * directly instead, the same "the block calls the tile's own hook" pattern established for {@code onPlacedBy}
     * elsewhere in this pass. */
    public void toggleOpenSide(Direction side) {
        if (!openSides.remove(side)) {
            openSides.add(side);
        }
        queue.clear();
        markDirtyAndSync();
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        input.child("tank").ifPresent(tank::deserialize);
        owner = input.read("owner", UUIDUtil.CODEC).orElse(null);
        int mask = input.getIntOr("openSides", DEFAULT_OPEN_MASK);
        openSides.clear();
        for (Direction face : Direction.values()) {
            if (((mask >> face.ordinal()) & 1) == 1) {
                openSides.add(face);
            }
        }
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        tank.serialize(output.child("tank"));
        if (owner != null) {
            output.store("owner", UUIDUtil.CODEC, owner);
        }
        int mask = 0;
        for (Direction face : openSides) {
            mask |= 1 << face.ordinal();
        }
        output.putInt("openSides", mask);
    }

    // IDebuggable

    @Override
    public void getDebugInfo(List<String> left, List<String> right, @Nullable Direction side) {
        left.add("fluid = " + tank.getDebugString());
        left.add("open sides = " + openSides.stream().map(Enum::name).collect(Collectors.joining(", ")));
        left.add("delay = " + getCurrentDelay());
        left.add("tick = " + tick);
        left.add("queue size = " + queue.size());
    }
}
