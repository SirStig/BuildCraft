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
import net.minecraft.nbt.CompoundTag;
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

/**
 * Port of 1.12.2's {@code buildcraft.silicon.item.ItemPluggableGate}: one physical item, an NBT-carried
 * {@link GateVariant} distinguishing every material/logic/modifier combination -- 1.12.2's own NBT-variant
 * item shape ({@code NBTUtilBC.getItemData}), not a separate {@link Item} per variant.
 *
 * <p><b>Scope cut:</b> {@code addSubItems}/{@code addModelVariants} (the full creative-tab cartesian product of
 * every material x logic x modifier combination, plus per-variant model registration) are not ported -- this
 * batch's creative tab only carries the plain, untagged stack (which reads back as {@code GateVariant}'s own
 * "empty NBT" default: AND/clay-brick/no-modifier, 1.12.2's own "basic gate" special case -- see
 * {@link GateVariant#getLocalizedName}). Every other variant is still fully real and constructible
 * ({@link #getStack}, or a `/give` with the right NBT), just not pre-populated into the creative menu this
 * round. Tooltip/display-name customisation is dropped for the same "polish, not core function" reason.
 */
public class ItemPluggableGate extends Item implements IItemPluggable {
    public ItemPluggableGate(Properties properties) {
        super(properties);
    }

    public static GateVariant getVariant(@NotNull ItemStack stack) {
        return new GateVariant(NBTUtilBC.getItemData(stack).getCompoundOrEmpty("gate"));
    }

    @NotNull
    public ItemStack getStack(GateVariant variant) {
        ItemStack stack = new ItemStack(this);
        CompoundTag data = new CompoundTag();
        data.put("gate", variant.writeToNBT());
        NBTUtilBC.setItemData(stack, data);
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
