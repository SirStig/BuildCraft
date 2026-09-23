/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * Copyright (c) 2026 Joshua Kac -- NeoForge port (BuildCraft 10)
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL
 * was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package buildcraft.builders.tile;

import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

import net.neoforged.neoforge.transfer.item.ItemResource;

import buildcraft.api.core.EnumPipePart;
import buildcraft.api.tiles.IDebuggable;

import buildcraft.lib.tile.TileBC;
import buildcraft.lib.tile.item.ItemHandlerManager;
import buildcraft.lib.tile.item.ItemHandlerManager.EnumAccess;
import buildcraft.lib.tile.item.ItemHandlerSimple;

import buildcraft.builders.item.ItemBlueprint;
import buildcraft.builders.snapshot.Blueprint;

import buildcraft.BCBuildersRegistries;

/**
 * The port of 1.12.2's {@code TileReplacer}. The original swapped one {@code ISchematicBlock} for another inside a
 * captured snapshot's palette, driven by two {@code ItemSchematicSingle} "single block schematic" carrier items
 * (a from/to pair) plus a filled {@code ItemSnapshot}; the rebuilt snapshot came back out as a new "used" snapshot
 * item, keyed into the world's {@code GlobalSavedDataSnapshots} registry, with the two carriers consumed.
 *
 * <p>This round's {@link Blueprint} palette is a plain {@link BlockState} list (see that class's own javadoc), and
 * this port never carried over {@code ItemSchematicSingle}/{@code ISchematicBlock} at all -- there is no
 * single-block-schematic carrier item in this slice. The from/to pair is ported instead as two plain block-item
 * slots: any {@link BlockItem} stack (a stack of {@code minecraft:stone}, say) stands in directly for "this block",
 * matched by its {@link net.minecraft.world.level.block.Block#defaultBlockState()}. That is a real behavioural
 * narrowing versus the original's rule-driven {@code ISchematicBlock} (no NBT/metadata-variant targeting -- just
 * the default state of whichever block the item places), but it keeps the tile's actual job -- "replace every
 * instance of block A in this blueprint with block B" -- genuinely working with what this port's blueprint system
 * has today. The blueprint slot's stack is rewritten in place with the new palette (not swapped for a fresh "used"
 * item pointing at a registry entry -- this port's {@link Blueprint} lives directly on the item's own NBT, see
 * {@link ItemBlueprint}'s javadoc), and both selector slots are consumed on every run, exactly like the original
 * (which never checked whether the palette actually contained {@code from} either).
 *
 * <p>Runs unconditionally every tick once all three slots are filled, with no delay or progress bar -- 1.12.2's own
 * {@code TileReplacer#update} had none either. No GUI exists for this tile, matching {@code TileBuilder}/
 * {@code TileArchitectTable}'s own no-GUI precedent for this round's blueprint slice -- all three slots are only
 * reachable through a hopper or a pipe against the block's outer faces.
 */
public class TileReplacer extends TileBC implements IDebuggable {

    public final ItemHandlerManager itemManager = new ItemHandlerManager((handler, slot, before, after) -> {});
    public final ItemHandlerSimple invBlueprint = itemManager.addInvHandler(
        "blueprint", 1, this::isBlueprint, EnumAccess.BOTH, EnumPipePart.VALUES);
    public final ItemHandlerSimple invFrom = itemManager.addInvHandler(
        "from", 1, this::isBlockItem, EnumAccess.BOTH, EnumPipePart.VALUES);
    public final ItemHandlerSimple invTo = itemManager.addInvHandler(
        "to", 1, this::isBlockItem, EnumAccess.BOTH, EnumPipePart.VALUES);

    public TileReplacer(BlockPos pos, BlockState state) {
        super(BCBuildersRegistries.REPLACER_TYPE.get(), pos, state);
    }

    private boolean isBlueprint(int slot, ItemResource resource) {
        return resource.getItem() instanceof ItemBlueprint;
    }

    private boolean isBlockItem(int slot, ItemResource resource) {
        return resource.getItem() instanceof BlockItem;
    }

    /** Driven by {@code BlockReplacer}'s {@code getTicker}. */
    public void serverTick() {
        if (level == null || level.isClientSide()) {
            return;
        }
        ItemStack blueprintStack = invBlueprint.getStackInSlot(0);
        ItemStack fromStack = invFrom.getStackInSlot(0);
        ItemStack toStack = invTo.getStackInSlot(0);
        if (blueprintStack.isEmpty() || fromStack.isEmpty() || toStack.isEmpty()) {
            return;
        }
        if (!(fromStack.getItem() instanceof BlockItem fromItem) || !(toStack.getItem() instanceof BlockItem toItem)) {
            return;
        }
        Blueprint blueprint = Blueprint.readFromStack(blueprintStack, level);
        if (blueprint == null) {
            return;
        }
        BlockState fromState = fromItem.getBlock().defaultBlockState();
        BlockState toState = toItem.getBlock().defaultBlockState();
        for (int i = 0; i < blueprint.palette.size(); i++) {
            if (blueprint.palette.get(i).equals(fromState)) {
                blueprint.palette.set(i, toState);
            }
        }
        ItemStack updatedBlueprint = blueprintStack.copy();
        Blueprint.writeToStack(updatedBlueprint, blueprint);
        invBlueprint.setStackInSlot(0, updatedBlueprint);
        invFrom.setStackInSlot(0, ItemStack.EMPTY);
        invTo.setStackInSlot(0, ItemStack.EMPTY);
        markDirtyAndSync();
    }

    // NBT

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        itemManager.serialize(output);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        itemManager.deserialize(input);
    }

    @Override
    public void getDebugInfo(List<String> left, List<String> right, Direction side) {
        left.add("blueprint = " + !invBlueprint.getStackInSlot(0).isEmpty());
        left.add("from = " + invFrom.getStackInSlot(0));
        left.add("to = " + invTo.getStackInSlot(0));
    }
}
