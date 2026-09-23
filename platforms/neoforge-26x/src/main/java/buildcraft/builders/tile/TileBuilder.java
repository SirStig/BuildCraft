/*
 * Copyright (c) 2016 SpaceToad and the BuildCraft team
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
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

import net.neoforged.neoforge.transfer.item.ItemResource;

import buildcraft.api.core.EnumPipePart;
import buildcraft.api.mj.MjAPI;
import buildcraft.api.mj.MjBattery;
import buildcraft.api.properties.BuildCraftProperties;
import buildcraft.api.tiles.IDebuggable;

import buildcraft.lib.mj.MjBatteryReceiver;
import buildcraft.lib.tile.TileBC;
import buildcraft.lib.tile.item.ItemHandlerManager;
import buildcraft.lib.tile.item.ItemHandlerManager.EnumAccess;
import buildcraft.lib.tile.item.ItemHandlerSimple;

import buildcraft.builders.item.ItemBlueprint;
import buildcraft.builders.snapshot.Blueprint;
import buildcraft.builders.snapshot.BlueprintBuilder;

import buildcraft.BCBuildersRegistries;

/**
 * The port of 1.12.2's {@code TileBuilder}, rebuilding a captured {@link Blueprint} block by block using MJ power
 * and items pulled from {@link #invResources}. See {@link BlueprintBuilder}'s own javadoc for exactly what the
 * check/break/place algorithm itself dropped relative to the original.
 *
 * <p><b>Dropped entirely this round, beyond what {@link Blueprint}/{@link BlueprintBuilder} already cover:</b>
 * the {@code IPathProvider}/stripes-pipe path system (a builder could originally follow a laid path and rebuild
 * the same structure at every stop along it -- this port only ever builds once, directly in front of itself) and
 * the {@code Template}/filler-pattern build mode entirely (only {@code Blueprint} mode exists here). The claimed
 * base corner itself is still always {@code worldPosition.relative(facing.getOpposite())} regardless of which
 * way this block faces (there is no original {@code offset}/marker-relative base-corner tracking here -- see
 * {@code TileArchitectTable}'s own javadoc on the same simplification), but the structure built from that corner
 * <b>is now rotated</b> -- see {@link #loadBlueprint} and {@link Blueprint}'s own javadoc for the
 * {@code Rotation} lookup this ports from the original {@code common} {@code TileBuilder#updateSnapshot}. No
 * GUI/container exists yet for this tile (matching {@code TileQuarry}'s own current scope), so the resources/
 * blueprint inventories are only reachable through a hopper or a pipe against the block's outer faces, not a
 * player-facing screen.
 */
public class TileBuilder extends TileBC implements IDebuggable {
    private static final long BATTERY_CAPACITY = 16_000 * MjAPI.MJ;

    public final ItemHandlerManager itemManager = new ItemHandlerManager(this::onSlotChange);
    public final ItemHandlerSimple invBlueprint;
    public final ItemHandlerSimple invResources;

    private final MjBattery battery = new MjBattery(BATTERY_CAPACITY);
    public final MjBatteryReceiver mjReceiver = new MjBatteryReceiver(battery);

    private final BlueprintBuilder builder = new BlueprintBuilder(this);

    public TileBuilder(BlockPos pos, BlockState state) {
        super(BCBuildersRegistries.BUILDER_TYPE.get(), pos, state);
        invBlueprint = itemManager.addInvHandler(
            "blueprint", 1, this::isValidBlueprint, EnumAccess.BOTH, EnumPipePart.VALUES);
        invResources = itemManager.addInvHandler("resources", 27, EnumAccess.BOTH, EnumPipePart.VALUES);
    }

    private boolean isValidBlueprint(int slot, ItemResource resource) {
        return resource.getItem() instanceof ItemBlueprint;
    }

    private void onSlotChange(ItemHandlerSimple handler, int slot, ItemStack before, ItemStack after) {
        if (handler == invBlueprint) {
            loadBlueprint();
        }
    }

    private void loadBlueprint() {
        ItemStack stack = invBlueprint.getStackInSlot(0);
        Blueprint newBlueprint = level == null || stack.isEmpty() ? null : Blueprint.readFromStack(stack, level);
        Direction facing = getBlockState().getValue(BuildCraftProperties.BLOCK_FACING);
        BlockPos basePos = worldPosition.relative(facing.getOpposite());
        Rotation rotation = newBlueprint == null ? Rotation.NONE : rotationFor(newBlueprint.facing, facing);
        builder.setBlueprint(newBlueprint, basePos, rotation);
        markDirtyAndSync();
    }

    /** Exactly the original {@code common} {@code TileBuilder#updateSnapshot}'s own {@code Rotation.values()}
     * lookup: the rotation that turns the blueprint's own captured facing into this builder's current facing, so
     * a builder rebuilds a structure oriented the way *it* faces rather than the way the table that captured it
     * happened to. */
    private static Rotation rotationFor(Direction capturedFacing, Direction builderFacing) {
        for (Rotation candidate : Rotation.values()) {
            if (candidate.rotate(capturedFacing) == builderFacing) {
                return candidate;
            }
        }
        return Rotation.NONE;
    }

    /** Driven by {@code BlockBuilder}'s {@code getTicker}. */
    public void serverTick() {
        if (level instanceof ServerLevel serverLevel && builder.getBlueprint() != null) {
            builder.tick(serverLevel);
            setChanged();
        }
    }

    // NBT

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        itemManager.serialize(output);
        output.putLong("battery", battery.getStored());
        output.store("builder", CompoundTag.CODEC, builder.serializeNBT());
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        itemManager.deserialize(input);
        battery.setStored(input.getLongOr("battery", 0));
        if (level != null) {
            input.read("builder", CompoundTag.CODEC)
                .ifPresent(tag -> builder.deserializeNBT(tag, Blueprint.blockLookup(level)));
        }
    }

    // Accessors used by BlueprintBuilder

    public MjBattery getBattery() {
        return battery;
    }

    public ItemHandlerSimple getInvResources() {
        return invResources;
    }

    public BlockPos getBuilderPos() {
        return worldPosition;
    }

    @Nullable
    public Blueprint getBlueprint() {
        return builder.getBlueprint();
    }

    @Override
    public void getDebugInfo(List<String> left, List<String> right, Direction side) {
        left.add("battery = " + battery.getDebugString());
        left.add("leftToBreak = " + builder.leftToBreak);
        left.add("leftToPlace = " + builder.leftToPlace);
    }
}
