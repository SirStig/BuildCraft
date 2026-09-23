/*
 * Copyright (c) 2011-2015, SpaceToad and the BuildCraft Team http://www.mod-buildcraft.com
 * Copyright (c) 2026 Joshua Kac -- NeoForge port (BuildCraft 10)
 *
 * The BuildCraft API is distributed under the terms of the MIT License. Please check the contents of the
 * license, which should be located as "LICENSE.API" in the BuildCraft source code distribution.
 */
package buildcraft.transport.stripes;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import buildcraft.api.transport.IStripesActivator;
import buildcraft.api.transport.IStripesHandlerItem;

/** A port of 1.12.2's own {@code StripesHandlerPlaceBlock}: places a {@link BlockItem} directly in front of the
 * pipe's open face, provided that position is empty. {@code ItemStack#onItemUse(...)} is
 * {@link ItemStack#useOn(UseOnContext)} here. */
public enum StripesHandlerPlaceBlock implements IStripesHandlerItem {
    INSTANCE;

    @Override
    public boolean handle(
        Level level, BlockPos pos, Direction direction, ItemStack stack, Player player, IStripesActivator activator
    ) {
        if (!(stack.getItem() instanceof BlockItem)) {
            return false;
        }
        BlockPos target = pos.relative(direction);
        if (!level.isEmptyBlock(target)) {
            return false;
        }
        BlockHitResult trace = new BlockHitResult(Vec3.atCenterOf(target), direction, target, false);
        UseOnContext ctx = new UseOnContext(level, player, InteractionHand.MAIN_HAND, stack, trace);
        stack.useOn(ctx);
        return true;
    }
}
