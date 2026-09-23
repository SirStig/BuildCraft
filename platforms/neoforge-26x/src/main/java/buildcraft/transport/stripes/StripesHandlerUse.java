/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * Copyright (c) 2026 Joshua Kac -- NeoForge port (BuildCraft 10)
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL
 * was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.transport.stripes;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import buildcraft.api.transport.IStripesActivator;
import buildcraft.api.transport.IStripesHandlerItem;

/** A port of 1.12.2's own {@code StripesHandlerUse}: a mod-extensible list of items (empty by default, exactly as
 * upstream) that get used directly against the block beyond the pipe's open face, on its near face. 1.12.2's own
 * {@code BlockUtil.useItemOnBlock} is inlined here as {@link ItemStack#useOn(UseOnContext)} -- the trimmed
 * {@code BlockUtil} this port carries forward never needed that particular helper before now. */
public enum StripesHandlerUse implements IStripesHandlerItem {
    INSTANCE;

    public static final List<Item> ITEMS = new ArrayList<>();

    @Override
    public boolean handle(
        Level level, BlockPos pos, Direction direction, ItemStack stack, Player player, IStripesActivator activator
    ) {
        if (!ITEMS.contains(stack.getItem())) {
            return false;
        }
        BlockPos target = pos.relative(direction);
        Direction face = direction.getOpposite();
        BlockHitResult trace = new BlockHitResult(Vec3.atCenterOf(target), face, target, false);
        UseOnContext ctx = new UseOnContext(level, player, InteractionHand.MAIN_HAND, stack, trace);
        return stack.useOn(ctx) != InteractionResult.PASS;
    }
}
