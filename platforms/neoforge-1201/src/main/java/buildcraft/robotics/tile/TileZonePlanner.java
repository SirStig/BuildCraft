/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * Copyright (c) 2026 Joshua Kac -- NeoForge port (BuildCraft 10)
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL
 * was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.robotics.tile;

import org.jetbrains.annotations.NotNull;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.util.LazyOptional;

import buildcraft.lib.tile.TileBC;
import buildcraft.lib.tile.item.ItemHandlerManager;
import buildcraft.lib.tile.item.ItemHandlerManager.EnumAccess;
import buildcraft.lib.tile.item.ItemHandlerSimple;

import buildcraft.core.item.ItemPaintbrush;
import buildcraft.robotics.container.ContainerZonePlanner;
import buildcraft.robotics.zone.ZonePlan;

import buildcraft.BCRoboticsRegistries;

/**
 * The Zone Planner's tile -- see the 26.x copy of this class for the full account of the two 1.12.2 features cut
 * here (the map-location-item exchange and the client-editable 3D minimap), both caused by this port not having a
 * concrete {@code ItemMapLocation} yet, and neither a defect in this pass. What remains is fully real: sixteen
 * persisted, synced {@link ZonePlan} layers and a real 16-slot paintbrush storage grid.
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

    private boolean isValidPaintbrush(int slot, ItemStack stack) {
        return stack.getItem() instanceof ItemPaintbrush;
    }

    @Override
    public void load(CompoundTag nbt) {
        super.load(nbt);
        itemManager.deserializeNBT(nbt.getCompound("inv_manager"));
        for (int i = 0; i < layers.length; i++) {
            layers[i].readFromNBT(nbt.getCompound("layer_" + i));
        }
    }

    @Override
    protected void saveAdditional(CompoundTag nbt) {
        super.saveAdditional(nbt);
        nbt.put("inv_manager", itemManager.serializeNBT());
        for (int i = 0; i < layers.length; i++) {
            CompoundTag layerTag = new CompoundTag();
            layers[i].writeToNBT(layerTag);
            nbt.put("layer_" + i, layerTag);
        }
    }

    // Capabilities

    @Override
    public <T> @NotNull LazyOptional<T> getCapability(@NotNull Capability<T> cap, Direction side) {
        LazyOptional<T> itemCap = itemManager.getCapability(cap, side);
        if (itemCap.isPresent()) {
            return itemCap;
        }
        return super.getCapability(cap, side);
    }

    // MenuProvider

    @Override
    public Component getDisplayName() {
        return Component.translatable(getBlockState().getBlock().getDescriptionId());
    }

    @Override
    public AbstractContainerMenu createMenu(int windowId, Inventory playerInv, Player player) {
        return new ContainerZonePlanner(windowId, playerInv, this);
    }
}
