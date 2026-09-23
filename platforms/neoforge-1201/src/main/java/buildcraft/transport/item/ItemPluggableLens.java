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
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import buildcraft.api.transport.IItemPluggable;
import buildcraft.api.transport.pipe.IFlowItems;
import buildcraft.api.transport.pipe.IPipeHolder;
import buildcraft.api.transport.pluggable.PipePluggable;
import buildcraft.api.transport.pluggable.PluggableDefinition;

import buildcraft.lib.misc.NBTUtilBC;

import buildcraft.transport.plug.PluggableLens;

import buildcraft.BCTransportRegistries;

/**
 * Port of 1.12.2's {@code buildcraft.silicon.item.ItemPluggableLens}: one physical item, an NBT-carried
 * colour/filter pair distinguishing every variant -- the same NBT-variant shape {@code ItemPluggableGate} already
 * established on this port for {@code GateVariant}, used here in place of 1.12.2's own item-damage-as-variant
 * encoding (16 colours x 2 (lens/filter) + 2 "clear" states = 34 damage values there).
 *
 * <p><b>Scope cut:</b> {@code addSubItems}/{@code addModelVariants} (pre-populating all 34 combinations into the
 * creative tab, plus their model variants) and the special-colour tooltip font renderer are not ported -- same
 * "polish, not core function" reasoning as {@code ItemPluggableGate}'s own scope-cut javadoc; every variant is
 * still fully real and constructible via {@link #getStack}.
 */
public class ItemPluggableLens extends Item implements IItemPluggable {
    public ItemPluggableLens(Properties properties) {
        super(properties);
    }

    @Nullable
    public static DyeColor getColour(@NotNull ItemStack stack) {
        return NBTUtilBC.readEnum(NBTUtilBC.getItemData(stack).get("colour"), DyeColor.class);
    }

    public static boolean isFilter(@NotNull ItemStack stack) {
        return NBTUtilBC.getItemData(stack).getBoolean("filter");
    }

    @NotNull
    public static ItemStack getStack(@Nullable DyeColor colour, boolean isFilter) {
        ItemStack stack = new ItemStack(BCTransportRegistries.ITEM_PLUGGABLE_LENS.get());
        CompoundTag data = NBTUtilBC.getItemData(stack);
        data.put("colour", NBTUtilBC.writeEnum(colour));
        data.putBoolean("filter", isFilter);
        return stack;
    }

    @Override
    @Nullable
    public PipePluggable onPlace(
        @NotNull ItemStack stack, IPipeHolder holder, Direction side, Player player, InteractionHand hand
    ) {
        if (holder.getPipe() == null || !(holder.getPipe().getFlow() instanceof IFlowItems)) {
            return null;
        }
        PluggableDefinition def = BCTransportRegistries.PLUGGABLE_DEF_LENS;
        return new PluggableLens(def, holder, side, getColour(stack), isFilter(stack));
    }
}
