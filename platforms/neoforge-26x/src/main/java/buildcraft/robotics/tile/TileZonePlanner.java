/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * Copyright (c) 2026 Joshua Kac -- NeoForge port (BuildCraft 10)
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL
 * was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.robotics.tile;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

import net.neoforged.neoforge.transfer.item.ItemResource;

import buildcraft.lib.tile.TileBC;
import buildcraft.lib.tile.item.ItemHandlerManager;
import buildcraft.lib.tile.item.ItemHandlerManager.EnumAccess;
import buildcraft.lib.tile.item.ItemHandlerSimple;

import buildcraft.core.item.ItemPaintbrush;
import buildcraft.robotics.container.ContainerZonePlanner;
import buildcraft.robotics.zone.ZonePlan;

import buildcraft.BCRoboticsRegistries;

/**
 * The Zone Planner's tile: a 16-layer (one per {@link net.minecraft.world.item.DyeColor}) chunk-grid claim map
 * ({@link ZonePlan}), plus a 16-slot storage grid for spare paintbrushes.
 *
 * <p><b>Two real 1.12.2 features are cut here, both for the same reason: this port has no
 * {@code buildcraft.core.item.ItemMapLocation}</b> (confirmed via a repo-wide search -- {@code buildcraft.api.items
 * .IMapLocation} exists as an interface, but no concrete implementing item or {@code BCCoreItems} field exists
 * anywhere in this port yet, on either platform). 1.12.2's tile used exactly that item, via a paintbrush + a
 * "chunkMapping"-tagged map location stack in a pair of input/output slot groups with a 200-tick "processing"
 * delay, to import a claimed zone from a portable map item into a layer, or export a layer back onto a blank map.
 * That whole exchange is dropped from this pass -- it is a real, deliberate scope cut caused by an upstream item
 * this port hasn't reached yet, not an oversight, and it is cheap to add back once that item exists (the exact
 * NBT shape {@code ZonePlan#writeToNBT}/{@code #readFromNBT} produces is unchanged from 1.12.2's own
 * "chunkMapping" tag). <b>The second cut, for the same root cause plus the 1.12.2 GUI's raw-GL 3D minimap and
 * mouse-drag painting having no home in this port's own rendering pipeline on either target (see
 * {@code GuiZonePlanner}'s own javadoc), is the entire client-editable-map path</b> -- {@code sendLayerToServer}
 * and the {@code NET_PLAN_CHANGE} id-tagged payload it used are not ported either: with no in-GUI way to paint a
 * layer, there is nothing left to push from client to server. What remains, and is fully real: the sixteen
 * {@link ZonePlan} layers themselves persist and sync (via {@link #markDirtyAndSync()}, this port's whole-state
 * replacement for 1.12.2's {@code NET_RENDER_DATA} payload) exactly as before, so a layer set by any future
 * caller (a re-added map-location exchange, a command, a different tool) is visible to every tracking client
 * immediately, and the paintbrush storage grid is a real, usable 16-slot inventory.
 */
public class TileZonePlanner extends TileBC implements MenuProvider {
    public final ItemHandlerManager itemManager = new ItemHandlerManager(null);
    public final ItemHandlerSimple invPaintbrushes;
    public final ZonePlan[] layers = new ZonePlan[16];

    public TileZonePlanner(BlockPos pos, BlockState state) {
        super(BCRoboticsRegistries.ZONE_PLANNER_TYPE.get(), pos, state);
        for (int i = 0; i < layers.length; i++) {
            layers[i] = new ZonePlan();
        }
        invPaintbrushes = itemManager.addInvHandler("paintbrushes", 16, this::isValidPaintbrush, EnumAccess.NONE);
    }

    private boolean isValidPaintbrush(int slot, ItemResource resource) {
        return resource.getItem() instanceof ItemPaintbrush;
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        itemManager.serialize(output.child("inv_manager"));
        for (int i = 0; i < layers.length; i++) {
            CompoundTag layerTag = new CompoundTag();
            layers[i].writeToNBT(layerTag);
            output.store("layer_" + i, CompoundTag.CODEC, layerTag);
        }
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        input.child("inv_manager").ifPresent(itemManager::deserialize);
        for (int i = 0; i < layers.length; i++) {
            input.read("layer_" + i, CompoundTag.CODEC).ifPresent(layers[i]::readFromNBT);
        }
    }

    // MenuProvider

    @Override
    public Component getDisplayName() {
        return Component.translatable(getBlockState().getBlock().getDescriptionId());
    }

    @Override
    public AbstractContainerMenu createMenu(int windowId, Inventory playerInv, Player player) {
        return new ContainerZonePlanner(BCRoboticsRegistries.ZONE_PLANNER_MENU.get(), windowId, playerInv, this);
    }

    /** Called server-side (once, when the menu is first opened) so the client can look up this same tile again --
     * see {@link ContainerZonePlanner}'s client-side factory constructor. */
    @Override
    public void writeClientSideData(AbstractContainerMenu menu, RegistryFriendlyByteBuf buffer) {
        buffer.writeBlockPos(getBlockPos());
    }
}
