/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * Copyright (c) 2026 Joshua Kac -- NeoForge port (BuildCraft 10)
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL
 * was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.builders.snapshot;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import buildcraft.api.template.ITemplateHandler;

/**
 * The default (and, this round, only) {@link ITemplateHandler}, registered by {@code BCBuildersRegistries} with
 * no explicit priority -- exactly the original's own registration, which likewise never passed one. The 1.12.2
 * original called {@code ItemStack#onItemUse(player,
 * world, pos, hand, EnumFacing.UP, 0.5F, 0.0F, 0.5F)}; this is that same "simulate clicking the top-centre of
 * {@code pos}" interaction rebuilt on the modern {@link UseOnContext}/{@link BlockHitResult} API -- a
 * {@link Direction#UP}-facing hit at the world position {@code pos + (0.5, 0, 0.5)}, matching the original's
 * hit-fraction offsets exactly.
 *
 * <p>Like the original, this only behaves correctly if {@code player}'s held item in {@link InteractionHand#
 * MAIN_HAND} is already {@code stack} -- {@link ItemStack#useOn} resolves the item to use from the outer stack
 * ({@code this}), but the {@link UseOnContext} 3-arg constructor used here always re-reads the actual stack to
 * place from {@code player.getItemInHand(hand)}, exactly as the original's own {@code ItemStack#onItemUse} read
 * the held stack internally. Callers (mirroring the original's own {@code TemplateBuilder}, which set the fake
 * player's held item immediately before calling {@link ITemplateHandler#handle}) are expected to uphold that.
 */
public enum TemplateHandlerDefault implements ITemplateHandler {
    INSTANCE;

    @Override
    public boolean handle(Level level, BlockPos pos, Player player, ItemStack stack) {
        Vec3 hitLocation = new Vec3(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
        BlockHitResult hit = new BlockHitResult(hitLocation, Direction.UP, pos, false);
        UseOnContext context = new UseOnContext(player, InteractionHand.MAIN_HAND, hit);
        return stack.useOn(context).consumesAction();
    }
}
