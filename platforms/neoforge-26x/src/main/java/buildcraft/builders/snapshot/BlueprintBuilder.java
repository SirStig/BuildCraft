/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * Copyright (c) 2026 Joshua Kac -- NeoForge port (BuildCraft 10)
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL
 * was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.builders.snapshot;

import java.util.Arrays;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderGetter;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import buildcraft.api.mj.MjAPI;

import buildcraft.lib.misc.BlockUtil;
import buildcraft.lib.misc.InventoryUtil;
import buildcraft.lib.misc.NBTUtilBC;
import buildcraft.lib.tile.item.ItemHandlerSimple;

import buildcraft.builders.tile.TileBuilder;

/**
 * Drives one {@link TileBuilder}'s build: for every position in its {@link Blueprint}, works out whether the
 * world already matches (do nothing), needs the current block cleared first, or needs the target block placed --
 * then spends MJ from the tile's battery over time to actually do it, exactly like the original
 * {@code SnapshotBuilder}/{@code BlueprintBuilder} pair's check/break/place task queues.
 *
 * <p><b>Genuinely simplified relative to the original</b> (see {@link Blueprint}'s own javadoc for what the data
 * model itself dropped): every position is rechecked every tick rather than the original's incremental
 * round-robin scan ({@code CHECKS_PER_TICK}) -- fine for the modestly-sized structures this round's slice targets,
 * but a real cost for a very large blueprint, since it walks the whole volume once per tick regardless of how
 * much of it is already correct. There is also no fluid cost, no entity spawning, and no
 * {@code isReadyToPlace}/{@code requiredBlockOffsets} dependency ordering -- blocks are placed in flat index
 * order with no "build the support before the thing it holds up" awareness. Required items come only from
 * {@link TileBuilder#getInvResources()} (a plain slot scan, not the original's generic
 * {@code IItemTransactor#extract} simulate-then-commit contract).
 */
public class BlueprintBuilder {
    private static final int MAX_QUEUE_SIZE = 8;
    private static final long MAX_POWER_PER_TICK = 256 * MjAPI.MJ;
    private static final byte UNKNOWN = 0;
    private static final byte CORRECT = 1;
    private static final byte TO_BREAK = 2;
    private static final byte TO_PLACE = 3;

    private final TileBuilder tile;
    @Nullable
    private Blueprint blueprint;
    private BlockPos basePos = BlockPos.ZERO;
    private byte[] checkResults = new byte[0];
    private final Map<Integer, Long> breakPower = new LinkedHashMap<>();
    private final Map<Integer, Long> placePower = new LinkedHashMap<>();
    public int leftToBreak = 0;
    public int leftToPlace = 0;

    public BlueprintBuilder(TileBuilder tile) {
        this.tile = tile;
    }

    @Nullable
    public Blueprint getBlueprint() {
        return blueprint;
    }

    public void setBlueprint(@Nullable Blueprint blueprint, BlockPos basePos) {
        cancel();
        this.blueprint = blueprint;
        this.basePos = basePos;
        if (blueprint != null) {
            checkResults = new byte[blueprint.data.length];
            Arrays.fill(checkResults, UNKNOWN);
        }
    }

    public void cancel() {
        breakPower.clear();
        placePower.clear();
        checkResults = new byte[0];
        leftToBreak = 0;
        leftToPlace = 0;
    }

    private BlockPos worldPos(int index) {
        return basePos.offset(blueprint.posFromIndex(index));
    }

    private void check(ServerLevel level, int index) {
        BlockState target = blueprint.get(index);
        BlockPos worldPos = worldPos(index);
        BlockState actual = level.getBlockState(worldPos);
        if (target.isAir()) {
            checkResults[index] = actual.isAir() ? CORRECT : TO_BREAK;
        } else if (actual.equals(target)) {
            checkResults[index] = CORRECT;
        } else if (actual.isAir()) {
            checkResults[index] = TO_PLACE;
        } else {
            checkResults[index] = TO_BREAK;
        }
    }

    private boolean extractOneFromResources(Item item) {
        ItemHandlerSimple inv = tile.getInvResources();
        for (int slot = 0; slot < inv.size(); slot++) {
            ItemStack stack = inv.getStackInSlot(slot);
            if (!stack.isEmpty() && stack.is(item)) {
                ItemStack shrunk = stack.copy();
                shrunk.shrink(1);
                inv.setStackInSlot(slot, shrunk);
                return true;
            }
        }
        return false;
    }

    private void returnOneToResources(ServerLevel level, Item item) {
        ItemHandlerSimple inv = tile.getInvResources();
        for (int slot = 0; slot < inv.size(); slot++) {
            ItemStack stack = inv.getStackInSlot(slot);
            if (stack.isEmpty()) {
                inv.setStackInSlot(slot, new ItemStack(item, 1));
                return;
            }
            if (stack.is(item) && stack.getCount() < stack.getMaxStackSize()) {
                ItemStack grown = stack.copy();
                grown.grow(1);
                inv.setStackInSlot(slot, grown);
                return;
            }
        }
        InventoryUtil.addToBestAcceptor(level, tile.getBuilderPos(), null, new ItemStack(item, 1));
    }

    private boolean hasRequiredItem(int index) {
        Item item = blueprint.get(index).getBlock().asItem();
        if (item == Items.AIR) {
            return true;
        }
        ItemHandlerSimple inv = tile.getInvResources();
        for (int slot = 0; slot < inv.size(); slot++) {
            ItemStack stack = inv.getStackInSlot(slot);
            if (!stack.isEmpty() && stack.is(item)) {
                return true;
            }
        }
        return false;
    }

    private boolean tryPlace(ServerLevel level, int index, BlockPos worldPos) {
        BlockState target = blueprint.get(index);
        Item item = target.getBlock().asItem();
        if (item != Items.AIR && !extractOneFromResources(item)) {
            return false;
        }
        if (level.setBlockAndUpdate(worldPos, target)) {
            return true;
        }
        if (item != Items.AIR) {
            returnOneToResources(level, item);
        }
        return false;
    }

    /** @return {@code true} once every position in the blueprint matches the world (or there is no blueprint at
     *     all to build). */
    public boolean tick(ServerLevel level) {
        if (blueprint == null || blueprint.isEmpty()) {
            return true;
        }
        boolean allCorrect = true;
        for (int index = 0; index < checkResults.length; index++) {
            check(level, index);
            if (checkResults[index] != CORRECT) {
                allCorrect = false;
            }
        }
        breakPower.keySet().removeIf(index -> checkResults[index] == CORRECT);
        placePower.keySet().removeIf(index -> checkResults[index] == CORRECT);

        leftToBreak = 0;
        for (int index = 0; index < checkResults.length; index++) {
            if (checkResults[index] == TO_BREAK) {
                leftToBreak++;
                if (breakPower.size() < MAX_QUEUE_SIZE && !breakPower.containsKey(index)
                    && !BlockUtil.isUnbreakableBlock(level, worldPos(index))) {
                    breakPower.put(index, 0L);
                }
            }
        }
        leftToPlace = 0;
        for (int index = 0; index < checkResults.length; index++) {
            if (checkResults[index] == TO_PLACE) {
                leftToPlace++;
                if (breakPower.isEmpty() && placePower.size() < MAX_QUEUE_SIZE && !placePower.containsKey(index)
                    && hasRequiredItem(index)) {
                    placePower.put(index, 0L);
                }
            }
        }

        long budget = Math.min(MAX_POWER_PER_TICK, tile.getBattery().getStored());
        if (!breakPower.isEmpty()) {
            long share = Math.max(1, budget / breakPower.size());
            for (Iterator<Map.Entry<Integer, Long>> it = breakPower.entrySet().iterator(); it.hasNext(); ) {
                Map.Entry<Integer, Long> entry = it.next();
                int index = entry.getKey();
                BlockPos worldPos = worldPos(index);
                long target = BlockUtil.computeBlockBreakPower(level, worldPos);
                long power = entry.getValue() + tile.getBattery().extractPower(0, Math.min(share, target - entry.getValue()));
                if (power >= target) {
                    level.destroyBlockProgress(worldPos.hashCode(), worldPos, -1);
                    BlockUtil.breakBlockAndGetDrops(level, worldPos, new ItemStack(Items.DIAMOND_PICKAXE))
                        .ifPresent(drops -> drops.forEach(
                            drop -> InventoryUtil.addToBestAcceptor(level, tile.getBuilderPos(), null, drop)
                        ));
                    it.remove();
                    check(level, index);
                } else {
                    entry.setValue(power);
                    level.destroyBlockProgress(worldPos.hashCode(), worldPos, (int) ((power * 9) / target));
                }
            }
        } else if (!placePower.isEmpty()) {
            long share = Math.max(1, budget / placePower.size());
            for (Iterator<Map.Entry<Integer, Long>> it = placePower.entrySet().iterator(); it.hasNext(); ) {
                Map.Entry<Integer, Long> entry = it.next();
                int index = entry.getKey();
                BlockPos worldPos = worldPos(index);
                long target = (long) (Math.sqrt(worldPos.distSqr(tile.getBuilderPos())) * 10 * MjAPI.MJ) + MjAPI.MJ;
                long power = entry.getValue() + tile.getBattery().extractPower(0, Math.min(share, target - entry.getValue()));
                if (power >= target) {
                    tryPlace(level, index, worldPos);
                    it.remove();
                    check(level, index);
                } else {
                    entry.setValue(power);
                }
            }
        }

        return allCorrect;
    }

    public CompoundTag serializeNBT() {
        CompoundTag nbt = new CompoundTag();
        if (blueprint != null) {
            nbt.put("blueprint", blueprint.serializeNBT());
            nbt.put("basePos", NBTUtilBC.writeBlockPos(basePos));
        }
        return nbt;
    }

    public void deserializeNBT(CompoundTag nbt, HolderGetter<Block> blocks) {
        cancel();
        CompoundTag blueprintTag = nbt.getCompound("blueprint").orElse(null);
        if (blueprintTag != null) {
            blueprint = Blueprint.deserializeNBT(blueprintTag, blocks);
            BlockPos loadedBasePos = NBTUtilBC.readBlockPos(nbt.get("basePos"));
            basePos = loadedBasePos == null ? BlockPos.ZERO : loadedBasePos;
            checkResults = new byte[blueprint.data.length];
            Arrays.fill(checkResults, UNKNOWN);
        } else {
            blueprint = null;
        }
    }
}
