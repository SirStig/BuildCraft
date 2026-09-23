/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * Copyright (c) 2026 Joshua Kac -- NeoForge port (BuildCraft 10)
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL
 * was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.builders.tile;

import java.util.List;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.RegistryFriendlyByteBuf;
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
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

import buildcraft.api.core.EnumPipePart;
import buildcraft.api.mj.MjAPI;
import buildcraft.api.mj.MjBattery;
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
 * The port of 1.12.2's {@code TileFiller}: claims a rectangular area exactly the way {@link TileQuarry} does (see
 * that class's own javadoc for the area-claiming algorithm this reuses almost verbatim, minus the frame -- the
 * Filler never builds one), then works through every position in the claimed box, placing a block from its own
 * 27-slot resource inventory wherever the chosen {@link FillerPattern} wants one and none is there, and (if
 * {@link #canExcavate}) breaking whatever is in the way of a position the pattern wants left empty.
 *
 * <p><b>Not ported: the {@code Template}/{@code TemplateBuilder}/{@code SnapshotBuilder} snapshot system, and the
 * gate statement/action system.</b> 1.12.2's {@code TileFiller} was built entirely on top of both: a chosen
 * {@code IFillerPattern} produced a {@code Template.FilledTemplate} (via {@code FillerUtil.createBuildingInfo}),
 * which a {@code TemplateBuilder} then diffed against the world exactly like a Blueprint would; the pattern
 * itself was picked by dragging a {@code BCStatement} icon onto the filler from a gate GUI
 * ({@code IStatementContainer}/{@code IActionExternal}/{@code FullStatement<IFillerPattern>}). Neither subsystem
 * exists on this port yet -- both are large, separate bodies of work (the snapshot system alone also backs the
 * Blueprint builder, out of scope here; the gate system backs every Action/Trigger in the game, likewise out of
 * scope). Porting the Filler with either dependency in place first was not a realistic scope for this pass, so
 * this instead ports {@code Pattern}'s own shape-generation math directly onto a plain local grid
 * ({@link FilledArea}, this port's stand-in for {@code IFilledTemplate}) and drives it with the same
 * accumulate-MJ-then-act loop {@link TileQuarry} already established for its own dig loop. The GUI cycles a
 * pattern (and its 0-3 parameters) with plain buttons instead of dragging a gate icon -- see
 * {@link ContainerFiller}'s own javadoc.
 *
 * <p><b>Other deviations:</b>
 * <ul>
 * <li>No {@code IControllable}/gate-driven on/off/loop {@code Mode}. {@link #enabled} is a single GUI-toggled
 *     boolean instead -- nothing in this port yet drives a tile through {@code IControllable} (confirmed: only
 *     the API interface itself exists), so wiring up the full tri-state mode for one machine was not worth it
 *     this pass.</li>
 * <li>No addon/{@code VolumeBox} "filler planner" mode. 1.12.2's {@code TileFiller} could also be locked onto a
 *     marker volume's {@code AddonFillerPlanner} slot, building from a saved blueprint snapshot instead of a
 *     pattern. That is a different machine in spirit (schematic-driven, not shape-driven) built on the same
 *     unported snapshot system -- out of scope here; only the plain marker-volume-box path is ported.</li>
 * <li>Placed blocks always use the resource item's {@link BlockItem#getBlock()} default state -- 1.12.2's
 *     schematic-driven placement could reproduce a block's exact recorded state (rotation, waterlogged, etc.);
 *     without the snapshot system there is no recorded state to reproduce, so this places the plain default
 *     state, matching how a player would place that item with no extra context.</li>
 * <li>Breaking uses a fixed diamond-pickaxe stand-in tool, exactly {@link TileQuarry}'s own precedent (see that
 *     class's {@code completeAction}) -- there is no real tool in a filler's inventory to break with.</li>
 * </ul>
 */
public class TileFiller extends TileBC implements IDebuggable, MenuProvider {

    private static final long MAX_POWER_PER_TICK = 128 * MjAPI.MJ;
    private static final int MAX_ACTIONS_PER_TICK = 4;
    /** Fixed MJ cost to place one resource block -- no original per-block "place" cost exists to port (1.12.2's
     * placement cost lived entirely inside the unported {@code TemplateBuilder}), so this borrows
     * {@link TileQuarry#FRAME_PLACE_COST}'s order of magnitude. */
    private static final long PLACE_COST = 8 * MjAPI.MJ;

    private final MjBattery battery = new MjBattery(16_000 * MjAPI.MJ);
    public final MjBatteryReceiver mjReceiver = new MjBatteryReceiver(battery);

    public final ItemHandlerManager itemManager = new ItemHandlerManager((handler, slot, before, after) -> markDirtyAndSync());
    public final ItemHandlerSimple invResources = itemManager.addInvHandler(
        "resources", 27, (slot, resource) -> resource.getItem() instanceof BlockItem,
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

    /** Claims a box from an adjacent {@link ITileAreaProvider} or marker-volume connection -- the same two
     * sources {@link TileQuarry#onPlacedBy} reads, minus the frame/mining-box split (the Filler works the whole
     * claimed box directly) and minus the quarry's own no-marker-found directional fallback (1.12.2's Filler had
     * none either: with nothing claimed, {@link #hasBox()} just stays {@code false}). */
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

    /** Advances {@link #cursor} past every position that already matches the pattern, then stops on the next one
     * that needs a place or a break -- or leaves {@link #currentAction} {@code null} without advancing if a
     * needed place has nothing to place with yet (retried again next tick, once resources arrive). */
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
        for (int i = 0; i < invResources.size(); i++) {
            if (!invResources.getStackInSlot(i).isEmpty()) {
                return i;
            }
        }
        return -1;
    }

    /** Only ever called while {@link #currentAction} is non-null -- see {@link #serverTick}. */
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

    @Override
    public void writeClientSideData(AbstractContainerMenu menu, RegistryFriendlyByteBuf buffer) {
        buffer.writeBlockPos(getBlockPos());
    }

    // NBT

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        itemManager.serialize(output);
        output.store("box", net.minecraft.nbt.CompoundTag.CODEC, box.writeToNBT());
        output.putString("pattern", pattern.id);
        output.putInt("param0", params[0]);
        output.putInt("param1", params[1]);
        output.putInt("param2", params[2]);
        output.putBoolean("inverted", inverted);
        output.putBoolean("canExcavate", canExcavate);
        output.putBoolean("enabled", enabled);
        output.putLong("battery", battery.getStored());
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        itemManager.deserialize(input);
        box.initialize(input.read("box", net.minecraft.nbt.CompoundTag.CODEC).orElseGet(net.minecraft.nbt.CompoundTag::new));
        pattern = FillerPatterns.byId(input.getStringOr("pattern", FillerPatterns.NONE.id));
        params[0] = input.getIntOr("param0", 0);
        params[1] = input.getIntOr("param1", 0);
        params[2] = input.getIntOr("param2", 0);
        inverted = input.getBooleanOr("inverted", false);
        canExcavate = input.getBooleanOr("canExcavate", true);
        enabled = input.getBooleanOr("enabled", true);
        battery.setStored(input.getLongOr("battery", 0));
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
