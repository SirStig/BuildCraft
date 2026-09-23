/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * Copyright (c) 2026 Joshua Kac -- NeoForge port (BuildCraft 10)
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL
 * was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.transport.container;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.inventory.MenuType;

import buildcraft.lib.statement.ActionWrapper;
import buildcraft.lib.statement.TriggerWrapper;

import buildcraft.transport.gate.GateLogic;
import buildcraft.transport.plug.PluggableGate;
import buildcraft.transport.tile.TilePipeHolder;

/**
 * Port of 1.12.2's {@code buildcraft.silicon.container.ContainerGate}, rebuilt against this port's own container
 * conventions rather than 1.12.2's {@code ContainerBC_Neptune}/bespoke network layer (see
 * {@link GateLogic}'s own javadoc for the full account of what changed and why). Trigger/action assignment goes
 * through two fully-vanilla mechanisms, so no bespoke payload class exists for gates at all:
 *
 * <ul>
 * <li><b>Reading current state</b>: one {@link DataSlot} pair per gate slot (trigger index, action index) into
 *     {@link #validTriggers}/{@link #validActions} -- lists computed identically on both sides from
 *     {@link GateLogic#getAllValidTriggers()}/{@code getAllValidActions()}, since every input those methods read
 *     ({@link buildcraft.api.statements.StatementManager}'s registered providers, the gate's own wire manager)
 *     is already real, local, and already synced to the client through the pipe holder's ordinary NBT sync.
 *     {@link DataSlot} is vanilla's own auto-broadcast int channel (the same one a furnace's burn time uses),
 *     so no custom packet is needed for this direction either.</li>
 * <li><b>Writing a new assignment</b>: {@link #clickMenuButton}, vanilla's own
 * {@code ServerboundContainerButtonClickPacket} dispatch (the mechanism a loom/stonecutter/enchanting table
 *     already uses for "pick one of a fixed list of options") -- a click cycles the slot's trigger/action forward
 *     through {@link #validTriggers}/{@link #validActions}, wrapping through "none".</li>
 * </ul>
 *
 * <p><b>Scope cut:</b> statement <em>parameters</em> (the extra item-filter slots {@code EnumGateModifier}'s
 * quartz/diamond tiers allow) are not exposed by this GUI -- see {@code TriggerPipeSignal}/{@code ActionPipeSignal}'s
 * own javadoc for why no parameterised statement exists in this batch yet to need it.
 */
public class ContainerGate extends AbstractContainerMenu {

    public final PluggableGate pluggable;
    public final List<TriggerWrapper> validTriggers;
    public final List<ActionWrapper> validActions;

    public ContainerGate(MenuType<?> type, int windowId, Inventory playerInv, PluggableGate pluggable) {
        super(type, windowId);
        this.pluggable = pluggable;
        GateLogic logic = pluggable.logic;
        this.validTriggers = new ArrayList<>(logic.getAllValidTriggers());
        this.validActions = new ArrayList<>(logic.getAllValidActions());

        for (int i = 0; i < logic.statements.length; i++) {
            int slot = i;
            addDataSlot(new DataSlot() {
                @Override
                public int get() {
                    return validTriggers.indexOf(logic.statements[slot].trigger.get());
                }

                @Override
                public void set(int value) {
                    logic.statements[slot].trigger
                        .set(value < 0 || value >= validTriggers.size() ? null : validTriggers.get(value));
                }
            });
            addDataSlot(new DataSlot() {
                @Override
                public int get() {
                    return validActions.indexOf(logic.statements[slot].action.get());
                }

                @Override
                public void set(int value) {
                    logic.statements[slot].action
                        .set(value < 0 || value >= validActions.size() ? null : validActions.get(value));
                }
            });
        }
        addDataSlot(new DataSlot() {
            @Override
            public int get() {
                int bits = 0;
                for (int i = 0; i < logic.connections.length; i++) {
                    if (logic.connections[i]) {
                        bits |= 1 << i;
                    }
                }
                return bits;
            }

            @Override
            public void set(int value) {
                for (int i = 0; i < logic.connections.length; i++) {
                    logic.connections[i] = ((value >>> i) & 1) == 1;
                }
            }
        });
    }

    /** Client-side factory constructor, matching {@code IContainerFactory<T>}'s shape -- see
     * {@link PluggableGate#writeClientSideData} for the position/side pair this reads back. */
    public ContainerGate(int windowId, Inventory playerInv, RegistryFriendlyByteBuf extraData) {
        this(
            buildcraft.BCTransportRegistries.GATE_MENU.get(), windowId, playerInv,
            lookupGate(playerInv, extraData.readBlockPos(), Direction.from3DDataValue(extraData.readByte()))
        );
    }

    private static PluggableGate lookupGate(Inventory playerInv, BlockPos pos, Direction side) {
        if (playerInv.player.level().getBlockEntity(pos) instanceof TilePipeHolder holder
            && holder.getPluggable(side) instanceof PluggableGate gate) {
            return gate;
        }
        throw new IllegalStateException("No gate at " + pos + " side " + side);
    }

    /** One connection toggle, plus two "cycle forward" buttons per gate slot -- see this class's own javadoc. */
    @Override
    public boolean clickMenuButton(Player player, int id) {
        GateLogic logic = pluggable.logic;
        if (id >= ButtonId.CONNECTION_BASE) {
            int slot = id - ButtonId.CONNECTION_BASE;
            if (slot < 0 || slot >= logic.connections.length) {
                return false;
            }
            logic.connections[slot] = !logic.connections[slot];
            return true;
        }
        if (id >= ButtonId.ACTION_BASE) {
            int slot = id - ButtonId.ACTION_BASE;
            if (slot < 0 || slot >= logic.statements.length) {
                return false;
            }
            cycle(logic.statements[slot].action, validActions);
            return true;
        }
        int slot = id - ButtonId.TRIGGER_BASE;
        if (slot < 0 || slot >= logic.statements.length) {
            return false;
        }
        cycle(logic.statements[slot].trigger, validTriggers);
        return true;
    }

    private static <S> void cycle(buildcraft.lib.misc.data.IReference<S> ref, List<S> possible) {
        int index = possible.indexOf(ref.get());
        int next = index + 1;
        ref.set(next >= possible.size() ? null : possible.get(next));
    }

    public static int triggerButtonId(int slot) {
        return ButtonId.TRIGGER_BASE + slot;
    }

    public static int actionButtonId(int slot) {
        return ButtonId.ACTION_BASE + slot;
    }

    public static int connectionButtonId(int slot) {
        return ButtonId.CONNECTION_BASE + slot;
    }

    private interface ButtonId {
        /** Gate materials top out at {@link buildcraft.transport.gate.EnumGateMaterial#GOLD}'s 8 slots, so 64 is
         * a generous per-band stride. */
        int TRIGGER_BASE = 0;
        int ACTION_BASE = 64;
        int CONNECTION_BASE = 128;
    }

    @Override
    public boolean stillValid(Player player) {
        return pluggable.holder.canPlayerInteract(player) && pluggable.holder.getPluggable(pluggable.side) == pluggable;
    }

    @Override
    public net.minecraft.world.item.ItemStack quickMoveStack(Player player, int index) {
        return net.minecraft.world.item.ItemStack.EMPTY;
    }
}
