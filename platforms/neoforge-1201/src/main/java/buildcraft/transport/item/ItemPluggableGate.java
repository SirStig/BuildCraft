/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * Copyright (c) 2026 Joshua Kac -- NeoForge port (BuildCraft 10)
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL
 * was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.transport.item;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import buildcraft.api.transport.IItemPluggable;
import buildcraft.api.transport.pipe.IPipeHolder;
import buildcraft.api.transport.pluggable.PipePluggable;
import buildcraft.api.transport.pluggable.PluggableDefinition;

import buildcraft.lib.misc.NBTUtilBC;

import buildcraft.transport.gate.GateVariant;
import buildcraft.transport.plug.PluggableGate;

import buildcraft.BCTransportRegistries;

/** Port of 1.12.2's {@code buildcraft.silicon.item.ItemPluggableGate} -- see the 26.x copy of this class for the
 * full account, including this batch's creative-tab scope cut. {@link NBTUtilBC#getItemData} on this target
 * returns the stack's live tag directly (1.20.1 still has real item NBT), so no separate
 * {@code setItemData} call is needed the way 26.x's copy-on-read data components require. */
public class ItemPluggableGate extends Item implements IItemPluggable {
    public ItemPluggableGate(Properties properties) {
        super(properties);
    }

    public static GateVariant getVariant(@NotNull ItemStack stack) {
        return new GateVariant(NBTUtilBC.getItemData(stack).getCompound("gate"));
    }

    @NotNull
    public ItemStack getStack(GateVariant variant) {
        ItemStack stack = new ItemStack(this);
        NBTUtilBC.getItemData(stack).put("gate", variant.writeToNBT());
        return stack;
    }

    @Override
    @Nullable
    public PipePluggable onPlace(
        @NotNull ItemStack stack, IPipeHolder holder, Direction side, Player player, InteractionHand hand
    ) {
        GateVariant variant = getVariant(stack);
        PluggableDefinition def = BCTransportRegistries.PLUGGABLE_DEF_GATE;
        return new PluggableGate(def, holder, side, variant);
    }
}
