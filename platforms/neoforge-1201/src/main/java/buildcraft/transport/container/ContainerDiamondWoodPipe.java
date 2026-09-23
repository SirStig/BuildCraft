/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * Copyright (c) 2026 Joshua Kac -- NeoForge port (BuildCraft 10)
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL
 * was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.transport.container;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;

import buildcraft.api.transport.pipe.IPipeHolder.PipeMessageReceiver;

import buildcraft.lib.gui.ContainerBCTile;
import buildcraft.lib.gui.slot.SlotPhantom;
import buildcraft.lib.tile.item.ItemHandlerSimple;

import buildcraft.transport.pipe.behaviour.PipeBehaviourWoodDiamond;
import buildcraft.transport.pipe.behaviour.PipeBehaviourWoodDiamond.FilterMode;
import buildcraft.transport.tile.TilePipeHolder;

import buildcraft.BCTransportRegistries;

/** The wood/diamond combo pipe's own filter-configuration menu -- see the 26.x copy of this class for the full
 * account, including why the filter-mode change goes through {@link #clickMenuButton} rather than a bespoke
 * packet: this target has no BuildCraft-specific client-to-server GUI packet layer either. */
public class ContainerDiamondWoodPipe extends ContainerBCTile<TilePipeHolder> {
    private final PipeBehaviourWoodDiamond behaviour;

    public ContainerDiamondWoodPipe(int windowId, Inventory playerInv, PipeBehaviourWoodDiamond behaviour) {
        super(BCTransportRegistries.PIPE_DIAMOND_WOOD_MENU.get(), windowId,
            (TilePipeHolder) behaviour.pipe.getHolder().getPipeTile());
        this.behaviour = behaviour;

        ItemHandlerSimple filterInv = behaviour.filters;
        for (int i = 0; i < 9; i++) {
            addSlot(new SlotPhantom(filterInv, i, 8 + i * 18, 18));
        }

        addFullPlayerInventory(playerInv, 79);
    }

    /** Client-side factory constructor, matching {@code IContainerFactory<T>}'s shape. */
    public ContainerDiamondWoodPipe(int windowId, Inventory playerInv, FriendlyByteBuf extraData) {
        this(windowId, playerInv, lookupBehaviour(playerInv, extraData.readBlockPos()));
    }

    private static PipeBehaviourWoodDiamond lookupBehaviour(Inventory playerInv, BlockPos pos) {
        if (playerInv.player.level().getBlockEntity(pos) instanceof TilePipeHolder holder && holder.getPipe() != null
            && holder.getPipe().getBehaviour() instanceof PipeBehaviourWoodDiamond woodDiamond) {
            return woodDiamond;
        }
        throw new IllegalStateException("No diamond/wood pipe at " + pos);
    }

    /** {@code id} is a {@link FilterMode} ordinal -- see {@code GuiDiamondWoodPipe}'s own button wiring. */
    @Override
    public boolean clickMenuButton(Player player, int id) {
        FilterMode newMode = FilterMode.get(id);
        behaviour.filterMode = newMode;
        behaviour.pipe.getHolder().scheduleNetworkUpdate(PipeMessageReceiver.BEHAVIOUR);
        return true;
    }
}
