/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * Copyright (c) 2026 Joshua Kac -- NeoForge port (BuildCraft 10)
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL
 * was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.transport.plug;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;

import net.minecraftforge.network.NetworkHooks;

import buildcraft.api.transport.IWireEmitter;
import buildcraft.api.transport.pipe.IPipeHolder;
import buildcraft.api.transport.pluggable.PipePluggable;
import buildcraft.api.transport.pluggable.PluggableDefinition;

import buildcraft.transport.container.ContainerGate;
import buildcraft.transport.gate.GateVariant;

import buildcraft.BCTransportRegistries;

/** Port of 1.12.2's {@code buildcraft.silicon.plug.PluggableGate} -- see the 26.x copy of this class for the
 * full account (scope cuts, GUI opening). */
public class PluggableGate extends PipePluggable implements IWireEmitter, MenuProvider {

    public final buildcraft.transport.gate.GateLogic logic;

    public PluggableGate(PluggableDefinition def, IPipeHolder holder, Direction side, GateVariant variant) {
        super(def, holder, side);
        logic = new buildcraft.transport.gate.GateLogic(this, variant);
    }

    public PluggableGate(
        PluggableDefinition def, IPipeHolder holder, Direction side, CompoundTag nbt, HolderLookup.Provider registries
    ) {
        super(def, holder, side);
        logic = new buildcraft.transport.gate.GateLogic(this, nbt.getCompound("data"), registries);
    }

    @Override
    public CompoundTag writeToNbt(HolderLookup.Provider registries) {
        CompoundTag nbt = super.writeToNbt(registries);
        nbt.put("data", logic.writeToNbt(registries));
        return nbt;
    }

    // PipePluggable

    @Override
    public AABB getBoundingBox() {
        double min = 5 / 16.0, max = 11 / 16.0;
        return switch (side) {
            case DOWN -> new AABB(min, 2 / 16.0, min, max, 4 / 16.0, max);
            case UP -> new AABB(min, 12 / 16.0, min, max, 14 / 16.0, max);
            case NORTH -> new AABB(min, min, 2 / 16.0, max, max, 4 / 16.0);
            case SOUTH -> new AABB(min, min, 12 / 16.0, max, max, 14 / 16.0);
            case WEST -> new AABB(2 / 16.0, min, min, 4 / 16.0, max, max);
            case EAST -> new AABB(12 / 16.0, min, min, 14 / 16.0, max, max);
        };
    }

    @Override
    public boolean isBlocking() {
        return true;
    }

    @Override
    public ItemStack getPickStack() {
        return BCTransportRegistries.ITEM_PLUGGABLE_GATE.get().getStack(logic.variant);
    }

    @Override
    public boolean onPluggableActivate(Player player, BlockHitResult trace) {
        if (!player.level().isClientSide() && player instanceof ServerPlayer serverPlayer) {
            // The 4-arg extra-data overload: pos alone is not enough to find this specific PluggableGate again
            // (a pipe has up to six), so side has to ride along too -- see ContainerGate's own client factory.
            NetworkHooks.openScreen(
                serverPlayer, this,
                buffer -> {
                    buffer.writeBlockPos(holder.getPipePos());
                    buffer.writeByte(side.get3DDataValue());
                }
            );
        }
        return true;
    }

    @Override
    public boolean isEmitting(DyeColor colour) {
        return logic.isEmitting(colour);
    }

    @Override
    public void emitWire(DyeColor colour) {
        logic.emitWire(colour);
    }

    @Override
    public void onTick() {
        logic.onTick();
    }

    @Override
    public boolean canConnectToRedstone(@Nullable Direction to) {
        return true;
    }

    // MenuProvider

    @Override
    public Component getDisplayName() {
        return Component.translatable("buildcraft.gui.gate.title");
    }

    @Override
    public AbstractContainerMenu createMenu(int windowId, Inventory playerInv, Player player) {
        return new ContainerGate(BCTransportRegistries.GATE_MENU.get(), windowId, playerInv, this);
    }
}
