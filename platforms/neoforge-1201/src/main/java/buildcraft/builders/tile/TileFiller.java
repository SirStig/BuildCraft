/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * Copyright (c) 2026 Joshua Kac -- NeoForge port (BuildCraft 10)
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL
 * was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.builders.tile;

import java.util.List;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.state.BlockState;

import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.util.LazyOptional;

import buildcraft.api.core.EnumPipePart;
import buildcraft.api.mj.IMjReadable;
import buildcraft.api.mj.IMjReceiver;
import buildcraft.api.mj.MjAPI;
import buildcraft.api.mj.MjBattery;
import buildcraft.api.mj.MjCapabilities;
import buildcraft.api.properties.BuildCraftProperties;
import buildcraft.api.tiles.IDebuggable;
import buildcraft.api.tiles.ITileAreaProvider;

import buildcraft.lib.misc.BlockUtil;
import buildcraft.lib.misc.InventoryUtil;
import buildcraft.lib.misc.LocaleUtil;
import buildcraft.lib.misc.data.Box;
import buildcraft.lib.mj.MjBatteryReceiver;
import buildcraft.lib.tile.TileBC;
import buildcraft.lib.tile.item.ItemHandlerManager;
import buildcraft.lib.tile.item.ItemHandlerManager.EnumAccess;
import buildcraft.lib.tile.item.ItemHandlerSimple;

import buildcraft.core.marker.VolumeCache;
import buildcraft.core.marker.VolumeConnection;

import buildcraft.builders.container.ContainerFiller;
import buildcraft.builders.filler.FilledArea;
import buildcraft.builders.filler.FillerPattern;
import buildcraft.builders.filler.FillerPatterns;

import buildcraft.BCBuildersRegistries;

/**
 * Mirrors the 26.x class of the same name -- see that one's own javadoc for the full account of what 1.12.2's
 * {@code TileFiller} looked like and what is (and is not) ported. This file differs only in the usual 1.20.1
 * places: {@code CompoundTag}-based {@code saveAdditional}/{@code load} instead of {@code ValueOutput}/
 * {@code ValueInput}, and {@link #getCapability} exposing MJ directly (this target has no
 * {@code RegisterCapabilitiesEvent} block-entity hook -- see {@code TileQuarry}'s own javadoc for the same split
 * already established there).
 */
public class TileFiller extends TileBC implements IDebuggable, MenuProvider {

    private static final long MAX_POWER_PER_TICK = 128 * MjAPI.MJ;
    private static final int MAX_ACTIONS_PER_TICK = 4;
    private static final long PLACE_COST = 8 * MjAPI.MJ;

    private final MjBattery battery = new MjBattery(16_000 * MjAPI.MJ);
    public final MjBatteryReceiver mjReceiver = new MjBatteryReceiver(battery);
    private final LazyOptional<IMjReceiver> receiverCap = LazyOptional.of(() -> mjReceiver);
    private final LazyOptional<IMjReadable> readableCap = LazyOptional.of(() -> mjReceiver);

    public final ItemHandlerManager itemManager = new ItemHandlerManager((handler, slot, before, after) -> markDirtyAndSync());
    public final ItemHandlerSimple invResources = itemManager.addInvHandler(
        "resources", 27, (slot, stack) -> stack.getItem() instanceof BlockItem,
        EnumAccess.INSERT, EnumPipePart.VALUES
    );

    public final Box box = new Box();
    private FillerPattern pattern = FillerPatterns.NONE;
    /** Sized for the largest {@code paramCount()} of any ported pattern -- {@code PatternShape2d} and the eighth/
     * quarter {@code PatternSpherePart} variants need all three (axis/hollow-or-facing, hollow-or-facing, and
     * rotation). */
    private final int[] params = new int[3];
    private boolean inverted = false;
    private boolean canExcavate = true;
    private boolean enabled = true;
    private boolean finished = false;

    @Nullable
    private FilledArea area;
    @Nullable
    private List<BlockPos> positions;
    private int cursor;

    @Nullable
    private Action currentAction;
    private BlockPos actionPos = BlockPos.ZERO;
    private long actionProgress = 0;

    private enum Action {
        PLACE,
        BREAK
    }

    public TileFiller(BlockPos pos, BlockState state) {
        super(BCBuildersRegistries.FILLER_TYPE.get(), pos, state);
    }

    // Area claiming

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
            provider.removeFromWorld();
        } else {
            var subCache = VolumeCache.INSTANCE.getSubCache(level);
            var marker = subCache.getMarker(areaPos);
            if (marker != null) {
                VolumeConnection connection = marker.getCurrentConnection();
                if (connection != null) {
                    Box volBox = connection.getBox();
                    if (volBox.isInitialized()) {
                        min = volBox.min();
                        max = volBox.max();
                        if (marker instanceof ITileAreaProvider markerArea) {
                            markerArea.removeFromWorld();
                        }
                    }
                }
            }
        }

        if (min == null || max == null) {
            return;
        }
        box.reset();
        box.setMin(min);
        box.setMax(max);
        rebuildArea();
        markDirtyAndSync();
    }

    public boolean hasBox() {
        return box.isInitialized();
    }

    // Pattern / grid

    private void rebuildArea() {
        area = null;
        positions = null;
        cursor = 0;
        finished = false;
        currentAction = null;
        actionProgress = 0;
        if (!box.isInitialized() || level == null) {
            return;
        }
        BlockPos size = box.size();
        area = new FilledArea(size.getX(), size.getY(), size.getZ());
        pattern.fill(area, clampedParams());
        positions = box.getBlocksInArea();
    }

    private int[] clampedParams() {
        int count = pattern.paramCount();
        int[] result = new int[count];
        for (int i = 0; i < count; i++) {
            int values = pattern.paramValueCount(i);
            result[i] = values <= 0 ? 0 : Math.floorMod(params[i], values);
        }
        return result;
    }

    private void resetProgress() {
        cursor = 0;
        finished = false;
        currentAction = null;
        actionProgress = 0;
    }

    public void cyclePattern() {
        int next = (FillerPatterns.indexOf(pattern) + 1) % FillerPatterns.VALUES.length;
        pattern = FillerPatterns.VALUES[next];
        for (int i = 0; i < params.length; i++) {
            params[i] = pattern.paramCount() > i ? pattern.defaultParam(i) : 0;
        }
        rebuildArea();
        markDirtyAndSync();
    }

    public void cycleParam(int index) {
        if (index < 0 || index >= pattern.paramCount()) {
            return;
        }
        int values = pattern.paramValueCount(index);
        if (values <= 0) {
            return;
        }
        params[index] = (Math.floorMod(params[index], values) + 1) % values;
        rebuildArea();
        markDirtyAndSync();
    }

    public void toggleInverted() {
        inverted = !inverted;
        resetProgress();
        markDirtyAndSync();
    }

    public void toggleExcavate() {
        canExcavate = !canExcavate;
        resetProgress();
        markDirtyAndSync();
    }

    public void toggleEnabled() {
        enabled = !enabled;
        markDirtyAndSync();
    }

    public FillerPattern getPattern() {
        return pattern;
    }

    public int getParam(int index) {
        return index >= 0 && index < params.length ? params[index] : 0;
    }

    public boolean isInverted() {
        return inverted;
    }

    public boolean canExcavate() {
        return canExcavate;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public boolean isFinished() {
        return finished;
    }

    public MjBattery getBattery() {
        return battery;
    }

    // Work loop

    /** Driven by {@code BlockFiller}'s {@code getTicker}. */
    public void serverTick() {
        if (!enabled || !box.isInitialized()) {
            return;
        }
        if (area == null) {
            rebuildArea();
        }
        if (area == null || positions == null || finished) {
            return;
        }

        long max = MAX_POWER_PER_TICK;
        boolean changed = false;
        for (int i = 0; i < MAX_ACTIONS_PER_TICK && max > 0; i++) {
            if (currentAction == null) {
                pickNextAction();
                if (currentAction == null) {
                    if (positions != null && cursor >= positions.size()) {
                        finished = true;
                    }
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
            } else {
                break;
            }
        }
        if (changed) {
            setChanged();
        }
    }

    private void pickNextAction() {
        while (positions != null && cursor < positions.size()) {
            BlockPos worldPos = positions.get(cursor);
            BlockPos local = worldPos.subtract(box.min());
            boolean desired = area.get(local.getX(), local.getY(), local.getZ()) != inverted;
            boolean present = !level.getBlockState(worldPos).isAir();
            if (desired == present) {
                cursor++;
                continue;
            }
            if (desired) {
                if (findResourceSlot() < 0) {
                    return;
                }
                currentAction = Action.PLACE;
                actionPos = worldPos;
                return;
            } else {
                if (!canExcavate || BlockUtil.isUnbreakableBlock(level, worldPos)) {
                    cursor++;
                    continue;
                }
                currentAction = Action.BREAK;
                actionPos = worldPos;
                return;
            }
        }
    }

    private int findResourceSlot() {
        for (int i = 0; i < invResources.getSlots(); i++) {
            if (!invResources.getStackInSlot(i).isEmpty()) {
                return i;
            }
        }
        return -1;
    }

    private long actionTarget() {
        return switch (currentAction) {
            case PLACE -> PLACE_COST;
            case BREAK -> BlockUtil.computeBlockBreakPower(level, actionPos);
        };
    }

    private void completeAction() {
        switch (currentAction) {
            case PLACE -> {
                int slot = findResourceSlot();
                if (slot >= 0 && level.getBlockState(actionPos).isAir()) {
                    ItemStack stack = invResources.getStackInSlot(slot).copy();
                    if (stack.getItem() instanceof BlockItem blockItem) {
                        level.setBlockAndUpdate(actionPos, blockItem.getBlock().defaultBlockState());
                        stack.shrink(1);
                        invResources.setStackInSlot(slot, stack);
                    }
                }
                cursor++;
            }
            case BREAK -> {
                if (level instanceof ServerLevel serverLevel && !level.getBlockState(actionPos).isAir()) {
                    level.destroyBlockProgress(actionPos.hashCode(), actionPos, -1);
                    BlockUtil.breakBlockAndGetDrops(serverLevel, actionPos, new ItemStack(Items.DIAMOND_PICKAXE))
                        .ifPresent(drops -> drops.forEach(
                            stack -> InventoryUtil.addToBestAcceptor(level, worldPosition, null, stack)
                        ));
                }
                cursor++;
            }
        }
    }

    // MenuProvider

    @Override
    public Component getDisplayName() {
        return Component.translatable(getBlockState().getBlock().getDescriptionId());
    }

    @Override
    public AbstractContainerMenu createMenu(int windowId, Inventory playerInv, Player player) {
        return new ContainerFiller(BCBuildersRegistries.FILLER_MENU.get(), windowId, playerInv, this);
    }

    // Capabilities

    @Override
    public <T> @NotNull LazyOptional<T> getCapability(@NotNull Capability<T> cap, Direction side) {
        if (cap == MjCapabilities.RECEIVER) {
            return receiverCap.cast();
        }
        if (cap == MjCapabilities.READABLE) {
            return readableCap.cast();
        }
        return super.getCapability(cap, side);
    }

    @Override
    public void invalidateCaps() {
        super.invalidateCaps();
        receiverCap.invalidate();
        readableCap.invalidate();
    }

    // NBT

    @Override
    protected void saveAdditional(CompoundTag nbt) {
        super.saveAdditional(nbt);
        nbt.put("inv_manager", itemManager.serializeNBT());
        nbt.put("box", box.writeToNBT());
        nbt.putString("pattern", pattern.id);
        nbt.putInt("param0", params[0]);
        nbt.putInt("param1", params[1]);
        nbt.putInt("param2", params[2]);
        nbt.putBoolean("inverted", inverted);
        nbt.putBoolean("canExcavate", canExcavate);
        nbt.putBoolean("enabled", enabled);
        nbt.putLong("battery", battery.getStored());
    }

    @Override
    public void load(CompoundTag nbt) {
        super.load(nbt);
        itemManager.deserializeNBT(nbt.getCompound("inv_manager"));
        box.initialize(nbt.getCompound("box"));
        pattern = FillerPatterns.byId(nbt.contains("pattern") ? nbt.getString("pattern") : FillerPatterns.NONE.id);
        params[0] = nbt.getInt("param0");
        params[1] = nbt.getInt("param1");
        params[2] = nbt.getInt("param2");
        inverted = nbt.getBoolean("inverted");
        canExcavate = !nbt.contains("canExcavate") || nbt.getBoolean("canExcavate");
        enabled = !nbt.contains("enabled") || nbt.getBoolean("enabled");
        battery.setStored(nbt.getLong("battery"));
        rebuildArea();
    }

    // IDebuggable

    @Override
    public void getDebugInfo(List<String> left, List<String> right, @Nullable Direction side) {
        left.add("battery = " + battery.getDebugString());
        left.add("box = " + box.min() + " -> " + box.max());
        left.add("pattern = " + pattern.id + " " + java.util.Arrays.toString(params));
        left.add("inverted = " + inverted + ", canExcavate = " + canExcavate + ", enabled = " + enabled);
        left.add("finished = " + finished + ", cursor = " + cursor + "/" + (positions == null ? 0 : positions.size()));
        left.add("action = " + currentAction + " @ " + actionPos + " (" + LocaleUtil.localizeMj(actionProgress) + ")");
    }
}
