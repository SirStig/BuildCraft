/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * Copyright (c) 2026 Joshua Kac -- NeoForge port (BuildCraft 10)
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL
 * was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.factory.tile;

import java.util.List;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

import net.neoforged.neoforge.fluids.FluidType;
import net.neoforged.neoforge.transfer.CombinedResourceHandler;
import net.neoforged.neoforge.transfer.fluid.FluidResource;

import buildcraft.api.tiles.IDebuggable;

import buildcraft.lib.fluid.Tank;

import buildcraft.factory.container.ContainerAutoCraftFluids;

import buildcraft.BCFactoryRegistries;

/**
 * The fluids half of the auto-workbench, ported from a genuinely unfinished corner of 1.12.2: confirmed by reading
 * {@code common/buildcraft/factory/BCFactoryBlocks.java}, the original's own registration call for this block is
 * commented out ({@code // public static BlockAutoWorkbenchFluids autoWorkbenchFluids;}), and no
 * {@code buildcraft_resources} asset tree entry (block texture, GUI texture, blockstate, model, loot table,
 * recipe) exists anywhere for it either -- only {@code TileAutoWorkbenchFluids}/{@code BlockAutoWorkbenchFluids}'
 * source files themselves ever shipped. This port finishes what 1.12.2 left half-built: a real, working,
 * registered block, with placeholder assets documented as such (see {@code BCFactoryRegistries}' own javadoc for
 * this block, and this port's resource files under {@code assets/buildcraft/models/block/auto_workbench_fluid*}).
 *
 * <p>Otherwise a direct, small port: {@code super(2, 2)} (confirmed against {@link TileAutoWorkbenchBase}'s own
 * constructor -- the two ints are the phantom blueprint grid's width/height, exactly as {@link TileAutoWorkbenchItems}'s
 * own {@code super(..., 3, 3)} already established, not an input/output slot count) means a 2x2 blueprint instead
 * of the items variant's 3x3 -- everything else (item handling, MJ accumulation, crafting) is inherited unchanged
 * from {@link TileAutoWorkbenchBase}. {@link #createMenu} is overridden (that method is not {@code final} on the
 * base) since the inherited implementation is hardcoded to {@code ContainerAutoCraftItems}.
 *
 * <p><b>Per-side fluid capability exposure needs no logic in this class at all.</b> {@code BCFactoryRegistries}'
 * {@code registerBlockEntity} lookup function already receives the queried {@link Direction} directly, so the
 * {@code DOWN}/{@code NORTH}/{@code WEST} -> {@link #tank1}, {@code UP}/{@code SOUTH}/{@code EAST} -> {@link #tank2}
 * split from 1.12.2's {@code CapUtil.addCapabilityInstance(..., EnumPipePart...)} wiring is just a branch inside
 * that registration lambda -- see {@code BCFactoryRegistries}' own javadoc. {@link #combinedTanks} (a
 * {@link CombinedResourceHandler}, this target's ready-made multi-handler combinator -- the same class
 * {@code ItemHandlerManager} already uses for its own combined-face item view) stands in for 1.12.2's
 * {@code TankManager}-typed {@code tankManager} field for the {@code side == null} ("CENTER") case; no
 * {@code TankManager} port exists in this codebase (nothing else has needed one yet), so this is the minimal
 * substitute rather than a full port of that class.
 */
public class TileAutoWorkbenchFluids extends TileAutoWorkbenchBase implements IDebuggable {
    public final Tank tank1 = new Tank(FluidType.BUCKET_VOLUME * 6, this::markDirtyAndSync);
    public final Tank tank2 = new Tank(FluidType.BUCKET_VOLUME * 6, this::markDirtyAndSync);
    public final CombinedResourceHandler<FluidResource> combinedTanks = new CombinedResourceHandler<>(tank1, tank2);

    public TileAutoWorkbenchFluids(BlockPos pos, BlockState state) {
        super(BCFactoryRegistries.AUTO_WORKBENCH_FLUIDS_TYPE.get(), pos, state, 2, 2);
    }

    @Override
    public AbstractContainerMenu createMenu(int windowId, Inventory playerInv, Player player) {
        return new ContainerAutoCraftFluids(BCFactoryRegistries.AUTO_WORKBENCH_FLUIDS_MENU.get(), windowId, playerInv, this);
    }

    @Override
    public void getDebugInfo(List<String> left, List<String> right, @Nullable Direction side) {
        left.add("Tanks:");
        left.add("  tank1: " + tank1.getContentsString());
        left.add("  tank2: " + tank2.getContentsString());
    }

    // TileBC

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        input.child("tank1").ifPresent(tank1::deserialize);
        input.child("tank2").ifPresent(tank2::deserialize);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        tank1.serialize(output.child("tank1"));
        tank2.serialize(output.child("tank2"));
    }
}
