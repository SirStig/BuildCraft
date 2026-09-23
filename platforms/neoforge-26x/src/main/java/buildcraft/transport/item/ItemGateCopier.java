/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * Copyright (c) 2026 Joshua Kac -- NeoForge port (BuildCraft 10)
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL
 * was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.transport.item;

import java.util.function.Consumer;

import org.jetbrains.annotations.Nullable;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.Level;

import buildcraft.lib.misc.NBTUtilBC;

/**
 * Port of 1.12.2's {@code buildcraft.silicon.item.ItemGateCopier}: an empty copier stores a configured gate's
 * trigger/action/connection state when right-clicked on it; a loaded copier pastes that state onto the next
 * gate instead -- see {@code buildcraft.transport.plug.PluggableGate#interactWithCopier} for the actual
 * copy/paste logic (moved there to match this port's own {@code onPluggableActivate} dispatch, rather than
 * 1.12.2's item-side {@code interactWithCopier}). Shift-right-clicking in the air clears a loaded copier, same
 * as the original.
 *
 * <p><b>Scope cut:</b> the two-texture ("empty"/"full") metadata-driven model swap 1.12.2 used
 * ({@code addModelVariants}) is not ported -- this item uses one static model/texture regardless of whether it
 * currently holds data, matching {@code ItemPluggableGate}/{@code ItemPluggableFacade}'s own precedent of
 * dropping per-NBT-state model variation this round. The "shift-right-click to clean" tooltip
 * ({@code buildcraft.item.nonclean.usage}) is kept, since it costs nothing and is the one piece of UI feedback
 * a player has for "this copier currently holds data" without the model swap.
 */
public class ItemGateCopier extends Item {
    private static final String NBT_DATA = "gate_data";

    public ItemGateCopier(Properties properties) {
        super(properties.stacksTo(1));
    }

    @Override
    public void appendHoverText(
        ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> builder,
        TooltipFlag flag
    ) {
        super.appendHoverText(stack, context, display, builder, flag);
        if (getCopiedGateData(stack) != null) {
            builder.accept(Component.translatable("buildcraft.item.nonclean.usage"));
        }
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide()) {
            return InteractionResult.PASS;
        }
        if (player.isShiftKeyDown()) {
            return clearData(stack);
        }
        return InteractionResult.PASS;
    }

    private InteractionResult clearData(ItemStack stack) {
        if (getCopiedGateData(stack) == null) {
            return InteractionResult.PASS;
        }
        NBTUtilBC.updateItemData(stack, nbt -> nbt.remove(NBT_DATA));
        return InteractionResult.SUCCESS;
    }

    @Nullable
    public static CompoundTag getCopiedGateData(ItemStack stack) {
        CompoundTag data = NBTUtilBC.getItemData(stack);
        return data.contains(NBT_DATA) ? data.getCompoundOrEmpty(NBT_DATA) : null;
    }

    public static void setCopiedGateData(ItemStack stack, CompoundTag nbt) {
        NBTUtilBC.updateItemData(stack, data -> data.put(NBT_DATA, nbt));
    }
}
