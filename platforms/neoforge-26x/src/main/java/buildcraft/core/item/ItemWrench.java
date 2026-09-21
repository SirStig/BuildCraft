/*
 * Copyright (c) 2016 SpaceToad and the BuildCraft team
 * Copyright (c) 2026 Joshua Kac -- NeoForge port (BuildCraft 10)
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL
 * was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.core.item;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.SwingAnimation;
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
 * 1.12.2's {@code ItemBC_Neptune} base (unlocalised name, registry name, creative tab, per-damage model
 * variants) is superseded here by {@link buildcraft.lib.registry.BCRegistry} plus the lang/model JSON -- see
 * PORTING.md's "Deliberately not ported" note on {@code IItemBuildCraft}, which explains the same supersession
 * for the equivalent 1.12.2 interface. This extends plain {@link Item} directly.
 *
 * <p>{@code onItemUseFirst}/{@code onItemUse} merged into the single {@link #useOn(UseOnContext)}.
 * {@code doesSneakBypassUse} no longer exists -- there is nothing left needing it, since the item has no other
 * sneak-sensitive behaviour to bypass. {@code IBlockState#getActualState} is gone along with the rest of the
 * extended-blockstate system it belonged to (see {@code buildcraft.lib.prop.UnlistedNonNullProperty}'s
 * deferral note): a {@link BlockState} already *is* the real state now, so the call is simply dropped.
 * {@code EntityPlayer#swingArm(hand)} is {@code LivingEntity#swing(hand, animation, sendToSwingingEntity)};
 * passing {@code true} for the last argument matches the old method's unconditional "just play it" behaviour,
 * since this item has no client-side prediction of its own to avoid double-animating.
 */
public class ItemWrench extends Item implements IToolWrench {
    private static final Identifier ADVANCEMENT = Identifier.fromNamespaceAndPath("buildcraftcore", "wrenched");

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
        player.swing(hand, SwingAnimation.DEFAULT, true);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level world = context.getLevel();
        BlockPos pos = context.getClickedPos();
        Player player = context.getPlayer();
        InteractionHand hand = context.getHand();

        BlockState state = world.getBlockState(pos);
        InteractionResult result = CustomRotationHelper.INSTANCE.attemptRotateBlock(world, pos, state, context.getClickedFace());
        if (result instanceof InteractionResult.Success && player != null) {
            wrenchUsed(player, hand, context.getItemInHand(), new BlockHitResult(context.getClickLocation(), context.getClickedFace(), pos, false));
        }
        SoundUtil.playSlideSound(world, pos, state, result);
        return result;
    }
}
