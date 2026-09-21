/*
 * Copyright (c) 2016 SpaceToad and the BuildCraft team
 * Copyright (c) 2026 Joshua Kac -- NeoForge port (BuildCraft 10)
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL
 * was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.core.item;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

import buildcraft.api.blocks.CustomRotationHelper;
import buildcraft.api.tools.IToolWrench;

import buildcraft.lib.misc.AdvancementUtil;
import buildcraft.lib.misc.SoundUtil;

/**
 * 1.12.2's {@code ItemBC_Neptune} base is superseded here by {@link buildcraft.lib.registry.BCRegistry} plus
 * the lang/model JSON -- see the 26.x copy of this class for the full explanation, which applies identically on
 * this target. {@code onItemUseFirst}/{@code onItemUse} merged into {@link #useOn(UseOnContext)};
 * {@code doesSneakBypassUse} and {@code IBlockState#getActualState} are both already gone on this target too
 * (both simplifications predate 1.20.1).
 */
public class ItemWrench extends Item implements IToolWrench {
    private static final ResourceLocation ADVANCEMENT = new ResourceLocation("buildcraftcore", "wrenched");

    public ItemWrench(Properties properties) {
        super(properties.stacksTo(1));
    }

    @Override
    public boolean canWrench(Player player, InteractionHand hand, ItemStack wrench, HitResult hit) {
        return true;
    }

    @Override
    public void wrenchUsed(Player player, InteractionHand hand, ItemStack wrench, HitResult hit) {
        AdvancementUtil.unlockAdvancement(player, ADVANCEMENT);
        player.swing(hand);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level world = context.getLevel();
        BlockPos pos = context.getClickedPos();
        Player player = context.getPlayer();
        InteractionHand hand = context.getHand();

        BlockState state = world.getBlockState(pos);
        InteractionResult result = CustomRotationHelper.INSTANCE.attemptRotateBlock(world, pos, state, context.getClickedFace());
        if (result == InteractionResult.SUCCESS && player != null) {
            wrenchUsed(player, hand, context.getItemInHand(), new BlockHitResult(context.getClickLocation(), context.getClickedFace(), pos, false));
        }
        SoundUtil.playSlideSound(world, pos, state, result);
        return result;
    }
}
