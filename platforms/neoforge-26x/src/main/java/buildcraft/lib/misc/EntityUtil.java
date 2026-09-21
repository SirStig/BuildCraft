/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * Copyright (c) 2026 Joshua Kac -- NeoForge port (BuildCraft 10)
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL
 * was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.lib.misc;

import org.jetbrains.annotations.NotNull;

import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.entity.projectile.arrow.SpectralArrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import buildcraft.api.tools.IToolWrench;

/** {@link AbstractArrow} and {@link SpectralArrow} moved into a new {@code projectile.arrow} sub-package on this
 * target; the 1.20.1 copy of this class still finds them directly under {@code projectile}. */
public class EntityUtil {
    public static NonNullList<ItemStack> collectItems(Level world, BlockPos around, double radius) {
        return collectItems(world, new Vec3(around).add(0.5, 0.5, 0.5), radius);
    }

    public static NonNullList<ItemStack> collectItems(Level world, Vec3 around, double radius) {
        NonNullList<ItemStack> stacks = NonNullList.create();

        AABB aabb = BoundingBoxUtil.makeAround(around, radius);
        for (ItemEntity ent : world.getEntitiesOfClass(ItemEntity.class, aabb)) {
            if (ent.isAlive()) {
                ent.discard();
                stacks.add(ent.getItem());
            }
        }
        return stacks;
    }

    public static Vec3 getVec(Entity entity) {
        return entity.position();
    }

    public static void setVec(Entity entity, Vec3 vec) {
        entity.setPos(vec);
    }

    public static InteractionHand getWrenchHand(LivingEntity entity) {
        ItemStack stack = entity.getMainHandItem();
        if (!stack.isEmpty() && stack.getItem() instanceof IToolWrench) {
            return InteractionHand.MAIN_HAND;
        }
        stack = entity.getOffhandItem();
        if (!stack.isEmpty() && stack.getItem() instanceof IToolWrench) {
            return InteractionHand.OFF_HAND;
        }
        return null;
    }

    public static void activateWrench(Player player, HitResult trace) {
        ItemStack stack = player.getMainHandItem();
        if (!stack.isEmpty() && stack.getItem() instanceof IToolWrench) {
            IToolWrench wrench = (IToolWrench) stack.getItem();
            wrench.wrenchUsed(player, InteractionHand.MAIN_HAND, stack, trace);
            return;
        }
        stack = player.getOffhandItem();
        if (!stack.isEmpty() && stack.getItem() instanceof IToolWrench) {
            IToolWrench wrench = (IToolWrench) stack.getItem();
            wrench.wrenchUsed(player, InteractionHand.OFF_HAND, stack, trace);
        }
    }

    @NotNull
    public static ItemStack getArrowStack(AbstractArrow arrow) {
        // FIXME: Replace this with an invocation of arrow.getPickupItem()
        // (but its protected so we can't)
        if (arrow instanceof SpectralArrow) {
            return new ItemStack(Items.SPECTRAL_ARROW);
        }
        return new ItemStack(Items.ARROW);
    }
}
