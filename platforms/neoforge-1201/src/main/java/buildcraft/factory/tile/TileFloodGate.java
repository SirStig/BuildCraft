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

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;

import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidType;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.fluids.capability.IFluidHandler.FluidAction;

import buildcraft.api.tiles.IDebuggable;

import buildcraft.lib.fluid.Tank;
import buildcraft.lib.misc.AdvancementUtil;
import buildcraft.lib.misc.BlockUtil;
import buildcraft.lib.misc.FluidUtilBC;
import buildcraft.lib.tile.TileBC;

import buildcraft.BCFactoryRegistries;

/**
 * Given a source fluid piped into {@link #tank}, searches outward through open (air, or already-same-fluid) space
 * along up to 4 of its 5 non-top sides and spreads that fluid into the world. Mirrors the 26.x class of the same
 * name -- see that one's javadoc for the full account of the search/placement algorithm, the faithfully-preserved
 * 1.12.2 path-revalidation bug in {@link #serverTick()}, why no {@code FakePlayer} is needed for placement, why
 * {@link #openSides} is real persisted state with no client sync of its own, and why the legacy
 * {@code NBTTagByteArray} save format is dropped rather than ported. This file differs only in the usual 1.20.1
 * places: NBT is {@link CompoundTag}, {@link #tank} is a real {@link IFluidHandler} itself (exposed through
 * {@link #getCapability} rather than registered against the block entity type), and {@link Fluid}/{@link FluidStack}
 * stand in for {@code FluidResource}.
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

    private static final ResourceLocation ADVANCEMENT_FLOOD_SINGLE =
        new ResourceLocation("buildcraftfactory", "flooding_the_world");

    private static final int[] REBUILD_DELAYS = { 16, 32, 64, 128, 256 };
    private static final int SEARCH_MAX_DISTANCE = 64;
    private static final int QUEUE_MAX_SIZE = 4096;

    public final Tank tank = new Tank(2 * FluidType.BUCKET_VOLUME, this::markDirtyAndSync);
    public final Set<Direction> openSides = EnumSet.copyOf(DEFAULT_OPEN_SIDES);
    public final Deque<BlockPos> queue = new ArrayDeque<>();
    private final Map<BlockPos, List<BlockPos>> paths = new HashMap<>();
    private int delayIndex = 0;
    private int tick = 0;

    private final LazyOptional<IFluidHandler> fluidCap = LazyOptional.of(() -> tank);

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
        Fluid fluid = tank.getFluidType();
        if (fluid == null || tank.getFluidAmount() <= 0) {
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

    private static boolean isGaseous(Fluid fluid) {
        return fluid.getFluidType().getDensity() < 0;
    }

    private boolean canFill(BlockPos offsetPos) {
        if (level.isEmptyBlock(offsetPos)) {
            return true;
        }
        FluidState fluidState = BlockUtil.getFluidWithFlowing(level, offsetPos);
        return fluidState != null && FluidUtilBC.areFluidsEqual(fluidState.getType(), tank.getFluidType())
            && FluidUtilBC.getFluidSource(level.getBlockState(offsetPos)) == null;
    }

    private boolean canSearch(BlockPos offsetPos) {
        if (canFill(offsetPos)) {
            return true;
        }
        Fluid fluid = FluidUtilBC.getFluidSource(level, offsetPos);
        return FluidUtilBC.areFluidsEqual(fluid, tank.getFluidType());
    }

    private boolean canFillThrough(BlockPos pos) {
        if (level.isEmptyBlock(pos)) {
            return false;
        }
        FluidState fluidState = BlockUtil.getFluidWithFlowing(level, pos);
        return fluidState != null && FluidUtilBC.areFluidsEqual(fluidState.getType(), tank.getFluidType());
    }

    /** Places a source block of {@code fluid} at {@code placePos}. See the 26.x copy of this class for why this
     * needs no {@code LiquidBlockContainer} handling: {@link #canFill}'s two outcomes (air, or an existing
     * flowing block of the same fluid) are both directly placeable. */
    private void placeFluid(BlockPos placePos, Fluid fluid) {
        level.setBlock(placePos, fluid.defaultFluidState().createLegacyBlock(), Block.UPDATE_ALL);
    }

    // TileEntity

    /** Driven by {@link buildcraft.factory.block.BlockFloodGate#getTicker}; was {@code TileFloodGate#update()}.
     * See the 26.x copy of this class for the rebuild/placement cadence this reproduces unchanged. */
    public void serverTick() {
        if (tank.getFluidAmount() < FluidType.BUCKET_VOLUME) {
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
                    // Faithfully reproduces a 1.12.2 bug: this should test `p`, not `currentPos` -- see the 26.x
                    // copy of this class's javadoc.
                    if (!canFillThrough(currentPos)) {
                        canFillPath = false;
                        break;
                    }
                }
            }
            if (canFillPath && canFill(currentPos)) {
                FluidStack toDrain = tank.drain(FluidType.BUCKET_VOLUME, FluidAction.SIMULATE);
                Fluid fluid = toDrain.getFluid();
                placeFluid(currentPos, fluid);
                tank.drain(FluidType.BUCKET_VOLUME, FluidAction.EXECUTE);
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
     * {@link #queue} so the next {@link #serverTick()} rebuilds against the new configuration. See the 26.x copy
     * of this class for the full rationale. */
    public void toggleOpenSide(Direction side) {
        if (!openSides.remove(side)) {
            openSides.add(side);
        }
        queue.clear();
        markDirtyAndSync();
    }

    @Override
    public void load(CompoundTag nbt) {
        super.load(nbt);
        tank.readFromNBT(nbt.getCompound("tank"));
        owner = nbt.hasUUID("owner") ? nbt.getUUID("owner") : null;
        int mask = nbt.contains("openSides") ? nbt.getInt("openSides") : DEFAULT_OPEN_MASK;
        openSides.clear();
        for (Direction face : Direction.values()) {
            if (((mask >> face.ordinal()) & 1) == 1) {
                openSides.add(face);
            }
        }
    }

    @Override
    protected void saveAdditional(CompoundTag nbt) {
        super.saveAdditional(nbt);
        nbt.put("tank", tank.writeToNBT(new CompoundTag()));
        if (owner != null) {
            nbt.putUUID("owner", owner);
        }
        int mask = 0;
        for (Direction face : openSides) {
            mask |= 1 << face.ordinal();
        }
        nbt.putInt("openSides", mask);
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

    // Capabilities

    @Override
    public <T> @NotNull LazyOptional<T> getCapability(@NotNull Capability<T> cap, Direction side) {
        if (cap == ForgeCapabilities.FLUID_HANDLER) {
            return fluidCap.cast();
        }
        return super.getCapability(cap, side);
    }

    @Override
    public void invalidateCaps() {
        super.invalidateCaps();
        fluidCap.invalidate();
    }
}
