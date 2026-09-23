/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * Copyright (c) 2026 Joshua Kac -- NeoForge port (BuildCraft 10)
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL
 * was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.transport.item;

import java.util.List;

import org.jetbrains.annotations.Nullable;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import buildcraft.lib.misc.NBTUtilBC;

/**
 * Port of 1.12.2's {@code buildcraft.silicon.item.ItemGateCopier} -- see the 26.x copy of this class for the
 * full account (scope cuts, why the copy/paste logic itself lives on {@code PluggableGate} instead).
 */
public class ItemGateCopier extends Item {
    private static final String NBT_DATA = "gate_data";

    public ItemGateCopier(Properties properties) {
        super(properties.stacksTo(1));
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, level, tooltip, flag);
        if (getCopiedGateData(stack) != null) {
            tooltip.add(Component.translatable("buildcraft.item.nonclean.usage"));
        }
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide()) {
            return InteractionResultHolder.pass(stack);
        }
        if (player.isShiftKeyDown()) {
            return clearData(stack);
        }
        return InteractionResultHolder.pass(stack);
    }

    private InteractionResultHolder<ItemStack> clearData(ItemStack stack) {
        if (getCopiedGateData(stack) == null) {
            return InteractionResultHolder.pass(stack);
        }
        NBTUtilBC.getItemData(stack).remove(NBT_DATA);
        return InteractionResultHolder.success(stack);
    }

    @Nullable
    public static CompoundTag getCopiedGateData(ItemStack stack) {
        CompoundTag data = NBTUtilBC.getItemData(stack);
        return data.contains(NBT_DATA) ? data.getCompound(NBT_DATA) : null;
    }

    public static void setCopiedGateData(ItemStack stack, CompoundTag nbt) {
        NBTUtilBC.getItemData(stack).put(NBT_DATA, nbt);
    }
}
