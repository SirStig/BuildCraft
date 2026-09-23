/*
 * Copyright (c) 2016 SpaceToad and the BuildCraft team
 * Copyright (c) 2026 Joshua Kac -- NeoForge port (BuildCraft 10)
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL
 * was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package buildcraft.builders.tile;

import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
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
 * A deliberately narrow scope-cut port of 1.12.2's {@code TileElectronicLibrary}. The original was a
 * network-mediated duplicator: a filled {@code ItemSnapshot} inserted into {@code invDownIn} taught the world's
 * {@code GlobalSavedDataSnapshots} registry (and every connected client's own local snapshot database, browsable
 * through {@code GuiElectronicLibrary}) about that snapshot's contents, keyed by a hash; a blank snapshot inserted
 * into {@code invUpIn}, together with a client-side GUI selection of one previously-"downloaded" entry, produced a
 * fresh "used" copy of that chosen snapshot in {@code invUpOut} -- all driven by a bespoke chunked
 * {@code NET_DOWN}/{@code NET_UP} network protocol standing in for what was really a client-server database sync.
 *
 * <p>None of that indirection exists in this port. There is no {@code GlobalSavedDataSnapshots} hash-keyed
 * registry, no per-client snapshot database, no {@code Template} type, and {@link Blueprint} lives directly on a
 * filled {@link ItemBlueprint} stack's own NBT (see that class's javadoc) rather than behind a lookup key -- so
 * "teach the network about this snapshot, then request a copy of one by key" has nothing left to plug into. What
 * <b>is</b> kept, honestly scoped to what this round's blueprint slice actually supports: the core duplicator
 * behaviour those two pipelines existed to serve. A filled blueprint placed in {@link #invMaster} acts as the
 * library's standing "master copy"; feeding any other {@link ItemBlueprint} stack into {@link #invIn} stamps out an
 * independent duplicate of the master's data into {@link #invOut}, consuming the input stack -- the master itself
 * is never consumed, so it can be used to print any number of copies. This is a direct in-block copy, not a
 * network transfer, and there is no GUI (no client-side catalog exists to browse) -- matching {@code TileBuilder}/
 * {@code TileArchitectTable}'s own no-GUI precedent for this round; all three slots are hopper/pipe reachable only.
 *
 * <p>Runs unconditionally every tick once a master and an input are present and the output is clear, with no delay
 * or progress bar (1.12.2's own down/up progress bars were purely a GUI-only visual, {@code DeltaManager.
 * EnumNetworkVisibility.GUI_ONLY} -- meaningless with no GUI to show them in).
 */
public class TileElectronicLibrary extends TileBC implements IDebuggable {

    public final ItemHandlerManager itemManager = new ItemHandlerManager((handler, slot, before, after) -> {});
    /** The library's standing master copy -- read from, never consumed by {@link #serverTick()}. */
    public final ItemHandlerSimple invMaster = itemManager.addInvHandler(
        "master", 1, this::isBlueprint, EnumAccess.BOTH, EnumPipePart.VALUES);
    /** Any blueprint stack fed in here is overwritten with a copy of {@link #invMaster}'s data. */
    public final ItemHandlerSimple invIn = itemManager.addInvHandler(
        "in", 1, this::isBlueprint, EnumAccess.INSERT, EnumPipePart.VALUES);
    public final ItemHandlerSimple invOut = itemManager.addInvHandler(
        "out", 1, EnumAccess.EXTRACT, EnumPipePart.VALUES);

    public TileElectronicLibrary(BlockPos pos, BlockState state) {
        super(BCBuildersRegistries.ELECTRONIC_LIBRARY_TYPE.get(), pos, state);
    }

    private boolean isBlueprint(int slot, ItemResource resource) {
        return resource.getItem() instanceof ItemBlueprint;
    }

    /** Driven by {@code BlockElectronicLibrary}'s {@code getTicker}. */
    public void serverTick() {
        if (level == null || level.isClientSide()) {
            return;
        }
        ItemStack masterStack = invMaster.getStackInSlot(0);
        ItemStack inStack = invIn.getStackInSlot(0);
        if (masterStack.isEmpty() || inStack.isEmpty() || !invOut.getStackInSlot(0).isEmpty()) {
            return;
        }
        Blueprint master = Blueprint.readFromStack(masterStack, level);
        if (master == null) {
            return;
        }
        ItemStack duplicate = inStack.copy();
        duplicate.setCount(1);
        Blueprint.writeToStack(duplicate, master);
        invOut.setStackInSlot(0, duplicate);
        invIn.setStackInSlot(0, ItemStack.EMPTY);
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
        left.add("master = " + !invMaster.getStackInSlot(0).isEmpty());
        left.add("in = " + !invIn.getStackInSlot(0).isEmpty());
        left.add("out = " + !invOut.getStackInSlot(0).isEmpty());
    }
}
