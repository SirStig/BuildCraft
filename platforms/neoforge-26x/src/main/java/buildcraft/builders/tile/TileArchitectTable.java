/*
 * Copyright (c) 2016 SpaceToad and the BuildCraft team
 * Copyright (c) 2026 Joshua Kac -- NeoForge port (BuildCraft 10)
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL
 * was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.builders.tile;

import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

import buildcraft.api.core.EnumPipePart;
import buildcraft.api.properties.BuildCraftProperties;
import buildcraft.api.tiles.IDebuggable;
import buildcraft.api.tiles.ITileAreaProvider;

import buildcraft.lib.misc.InventoryUtil;
import buildcraft.lib.misc.data.Box;
import buildcraft.lib.tile.TileBC;
import buildcraft.lib.tile.item.ItemHandlerManager;
import buildcraft.lib.tile.item.ItemHandlerManager.EnumAccess;
import buildcraft.lib.tile.item.ItemHandlerSimple;

import buildcraft.builders.snapshot.Blueprint;

import buildcraft.BCBuildersRegistries;

/**
 * The port of 1.12.2's {@code TileArchitectTable}: claims a rectangular area the same way {@link TileQuarry} does
 * (an adjacent {@link ITileAreaProvider}, typically a {@code TileMarkerVolume} volume-marker box touching the
 * table directly opposite the way it faces), captures every block in it into a {@link Blueprint}, and drops the
 * result as a filled {@code buildcraft.builders.item.ItemBlueprint} stack.
 *
 * <p><b>Genuinely simplified relative to 1.12.2</b> (see {@link Blueprint}'s own javadoc for what the data model
 * drops): the whole area is captured in one go on the tick after a valid area is claimed, rather than the
 * original's block-by-block scan spread over many ticks with a client-visible progress delta and per-block scan
 * particles ({@code ClientArchitectTables}) -- there is no per-block progress to show for a structure this round's
 * slice is sized for, and no snapshot-type choice (this only ever produces a {@code Blueprint}, never a
 * {@code Template}). There is also no manual "insert a blank snapshot item to start scanning" step: this table
 * scans automatically, exactly once, as soon as it has a valid claimed area -- see {@link #onPlacedBy}. Only the
 * direct-adjacent {@link ITileAreaProvider} case is ported from {@code TileQuarry#onPlacedBy}'s two-branch search;
 * the {@code VolumeConnection}-edge fallback search is not, to keep this class small -- place the table so one face
 * touches a corner marker directly. If no marker is found at all, a small fixed-size default box is claimed
 * directly in front of the table (mirroring {@code TileQuarry}'s own unconditional fallback), so the table is
 * useful without a marker set up first.
 */
public class TileArchitectTable extends TileBC implements IDebuggable {

    private static final int DEFAULT_RADIUS_H = 2;
    private static final int DEFAULT_HEIGHT = 4;

    public final ItemHandlerManager itemManager = new ItemHandlerManager((handler, slot, before, after) -> {});
    /** {@code EnumPipePart.VALUES} is required here, not optional -- {@link EnumAccess#EXTRACT} with no listed
     * parts registers no {@link buildcraft.lib.tile.item.WrappedItemHandlerExtract} wrapper against any face at
     * all (see {@link ItemHandlerManager#addInvHandler}), which would make this slot's {@code Capabilities.Item.
     * BLOCK} registration in {@code BCBuildersRegistries} expose an always-empty handler on every side -- a
     * hopper or pipe would never be able to pull the finished blueprint back out. */
    public final ItemHandlerSimple invOut = itemManager.addInvHandler("out", 1, EnumAccess.EXTRACT, EnumPipePart.VALUES);

    public final Box box = new Box();
    private boolean scanned = false;

    public TileArchitectTable(BlockPos pos, BlockState state) {
        super(BCBuildersRegistries.ARCHITECT_TABLE_TYPE.get(), pos, state);
    }

    /** Claims the area to capture, either from a directly-adjacent {@link ITileAreaProvider} or a fixed-size
     * default box -- see the class javadoc. Scans immediately once the area is known. */
    public void onPlacedBy(LivingEntity placer) {
        if (level == null || level.isClientSide()) {
            return;
        }
        Direction facing = getBlockState().getValue(BuildCraftProperties.BLOCK_FACING);
        BlockPos areaPos = worldPosition.relative(facing.getOpposite());
        BlockEntity adjacent = level.getBlockEntity(areaPos);

        if (adjacent instanceof ITileAreaProvider provider) {
            box.reset();
            box.setMin(provider.min());
            box.setMax(provider.max());
            provider.removeFromWorld();
        } else {
            switch (facing.getOpposite()) {
                case WEST -> {
                    box.setMin(worldPosition.offset(-DEFAULT_RADIUS_H - 1, 0, -DEFAULT_RADIUS_H));
                    box.setMax(worldPosition.offset(-1, DEFAULT_HEIGHT, DEFAULT_RADIUS_H));
                }
                case EAST -> {
                    box.setMin(worldPosition.offset(1, 0, -DEFAULT_RADIUS_H));
                    box.setMax(worldPosition.offset(DEFAULT_RADIUS_H + 1, DEFAULT_HEIGHT, DEFAULT_RADIUS_H));
                }
                case NORTH -> {
                    box.setMin(worldPosition.offset(-DEFAULT_RADIUS_H, 0, -DEFAULT_RADIUS_H - 1));
                    box.setMax(worldPosition.offset(DEFAULT_RADIUS_H, DEFAULT_HEIGHT, -1));
                }
                default -> {
                    box.setMin(worldPosition.offset(-DEFAULT_RADIUS_H, 0, 1));
                    box.setMax(worldPosition.offset(DEFAULT_RADIUS_H, DEFAULT_HEIGHT, DEFAULT_RADIUS_H + 1));
                }
            }
        }
        scanAndProduce();
    }

    private void scanAndProduce() {
        if (scanned || level == null || level.isClientSide() || !box.isInitialized()) {
            return;
        }
        scanned = true;
        Direction facing = getBlockState().getValue(BuildCraftProperties.BLOCK_FACING);
        Blueprint blueprint = Blueprint.capture(level, box.min(), box.size(), facing);
        ItemStack stack = new ItemStack(BCBuildersRegistries.BLUEPRINT.get());
        Blueprint.writeToStack(stack, blueprint);
        if (invOut.getStackInSlot(0).isEmpty()) {
            invOut.setStackInSlot(0, stack);
        } else {
            InventoryUtil.addToBestAcceptor(level, worldPosition, facing.getOpposite(), stack);
        }
        markDirtyAndSync();
    }

    // NBT

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        itemManager.serialize(output);
        output.store("box", net.minecraft.nbt.CompoundTag.CODEC, box.writeToNBT());
        output.putBoolean("scanned", scanned);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        itemManager.deserialize(input);
        input.read("box", net.minecraft.nbt.CompoundTag.CODEC).ifPresent(box::initialize);
        scanned = input.getBooleanOr("scanned", false);
    }

    @Override
    public void getDebugInfo(List<String> left, List<String> right, Direction side) {
        left.add("box = " + box.min() + " -> " + box.max());
        left.add("scanned = " + scanned);
    }
}
