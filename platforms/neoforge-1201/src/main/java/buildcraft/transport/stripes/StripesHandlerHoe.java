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
import net.minecraft.tags.ItemTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import buildcraft.api.transport.IStripesActivator;
import buildcraft.api.transport.IStripesHandlerItem;

/**
 * A port of 1.12.2's own {@code StripesHandlerHoe}, adapted to this target's item-use API:
 * {@code EntityPlayer/ItemStack#onItemUse} is {@link ItemStack#useOn(UseOnContext)} here. Uses
 * {@link ItemTags#HOES} rather than an {@code instanceof HoeItem} check -- this port's 26.x copy of this file has
 * no dedicated hoe item class to check against at all (hoe tilling there is a data-driven
 * {@code minecraft:block_transformer} component, not a Java override), so the tag check is used on both platforms
 * for one identical implementation. Tries the block directly in front of the pipe's open face first, then the
 * block below that (in case the front position
 * is open air above farmable ground) -- 1.12.2's own two-try shape, verbatim.
 */
public enum StripesHandlerHoe implements IStripesHandlerItem {
    INSTANCE;

    @Override
    public boolean handle(
        Level level, BlockPos pos, Direction direction, ItemStack stack, Player player, IStripesActivator activator
    ) {
        if (!stack.is(ItemTags.HOES)) {
            return false;
        }

        BlockPos target = pos.relative(direction);
        if (till(level, player, stack, target)) {
            return true;
        }
        return direction != Direction.UP && till(level, player, stack, target.below());
    }

    private boolean till(Level level, Player player, ItemStack stack, BlockPos pos) {
        BlockHitResult trace = new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false);
        UseOnContext ctx = new UseOnContext(level, player, InteractionHand.MAIN_HAND, stack, trace);
        return stack.useOn(ctx) != InteractionResult.PASS;
    }
}
