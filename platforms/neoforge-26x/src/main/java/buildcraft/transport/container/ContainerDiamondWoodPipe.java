/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * Copyright (c) 2026 Joshua Kac -- NeoForge port (BuildCraft 10)
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL
 * was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.transport.container;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.MenuType;

import buildcraft.api.transport.pipe.IPipeHolder.PipeMessageReceiver;

import buildcraft.lib.gui.ContainerBCTile;
import buildcraft.lib.gui.slot.SlotPhantom;
import buildcraft.lib.tile.item.ItemHandlerSimple;

import buildcraft.transport.pipe.behaviour.PipeBehaviourWoodDiamond;
import buildcraft.transport.pipe.behaviour.PipeBehaviourWoodDiamond.FilterMode;
import buildcraft.transport.tile.TilePipeHolder;

import buildcraft.BCTransportRegistries;

/**
 * The wood/diamond combo pipe's own filter-configuration menu -- 9 phantom filter slots, the full player
 * inventory at y=79, and a filter-mode radio choice (white/black/round-robin). A port of 1.12.2's own
 * {@code ContainerDiamondWoodPipe}, with its {@code sendNewFilterMode}/custom {@code readMessage} pair replaced by
 * {@link #clickMenuButton}: this port has no BuildCraft-specific client-to-server GUI packet layer at all (see
 * {@code ContainerBCTile}'s own javadoc for why {@code ContainerBC_Neptune}'s network layer was not ported), and
 * {@link AbstractContainerMenu#clickMenuButton}/{@code ServerboundContainerButtonClickPacket} is the vanilla
 * mechanism for exactly this "a GUI button changed server-visible state" shape (confirmed via the real decompiled
 * {@code MultiPlayerGameMode#handleInventoryButtonClick}, the same route vanilla's own loom/stonecutter screens
 * use) -- the screen calls {@code Minecraft.getInstance().gameMode.handleInventoryButtonClick(containerId, id)}
 * on a button press, and this menu's own {@link #clickMenuButton} applies it server-side.
 */
public class ContainerDiamondWoodPipe extends ContainerBCTile<TilePipeHolder> {
    private final PipeBehaviourWoodDiamond behaviour;

    public ContainerDiamondWoodPipe(MenuType<?> type, int windowId, Inventory playerInv, PipeBehaviourWoodDiamond behaviour) {
        super(type, windowId, (TilePipeHolder) behaviour.pipe.getHolder().getPipeTile());
        this.behaviour = behaviour;

        ItemHandlerSimple filterInv = behaviour.filters;
        for (int i = 0; i < 9; i++) {
            addSlot(new SlotPhantom(filterInv, i, 8 + i * 18, 18));
        }

        addFullPlayerInventory(playerInv, 79);
    }

    /** Client-side factory constructor, matching {@code IContainerFactory<T>}'s shape. */
    public ContainerDiamondWoodPipe(int windowId, Inventory playerInv, RegistryFriendlyByteBuf extraData) {
        this(BCTransportRegistries.PIPE_DIAMOND_WOOD_MENU.get(), windowId, playerInv,
            lookupBehaviour(playerInv, extraData.readBlockPos()));
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
