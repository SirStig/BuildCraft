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
import net.minecraft.util.Prediction;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.Vec3;

import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;

/**
 * Ported subset of 1.12.2's {@code InventoryUtil}: the drop/spawn helpers, which have no dependency beyond
 * vanilla and the transfer API.
 *
 * <p>{@code addToRandomInventory}, {@code addToRandomInjectable} and {@code addToBestAcceptor} are not ported
 * yet -- they route through {@code ItemTransactorHelper} (capability lookup against an arbitrary neighbouring
 * block entity), which itself needs {@code buildcraft.lib.misc.CapUtil}, not yet ported on this target either
 * (the same gap noted on {@code buildcraft.lib.inventory}'s own skipped {@code ItemTransactorHelper.java}, and
 * on {@code buildcraft.lib.tile.craft.WorkbenchCrafting}, which needs {@code addToBestAcceptor} specifically).
 *
 * <p>{@code Block.spawnAsEntity} is {@link Block#popResource(Level, BlockPos, ItemStack)} now.
 * {@code World#spawnEntity} is {@link Level#addFreshEntity(net.minecraft.world.entity.Entity)} -- a default
 * method on {@code Level} itself (via {@code LevelWriter}), so this keeps 1.12.2's "works with any world"
 * signature rather than narrowing to {@code ServerLevel}.
 */
public class InventoryUtil {

    public static void dropAll(Level world, Vec3 vec, ResourceHandler<ItemResource> handler) {
        dropAll(world, vec.x, vec.y, vec.z, handler);
    }

    public static void dropAll(Level world, BlockPos pos, ResourceHandler<ItemResource> handler) {
        dropAll(world, pos.getX(), pos.getY(), pos.getZ(), handler);
    }

    public static void dropAll(Level world, double x, double y, double z, ResourceHandler<ItemResource> handler) {
        for (int i = 0; i < handler.size(); i++) {
            ItemResource resource = handler.getResource(i);
            if (resource.isEmpty()) continue;
            int amount;
            try (Transaction transaction = Transaction.openRoot()) {
                amount = handler.extract(i, resource, handler.getAmountAsInt(i), transaction);
                transaction.commit();
            }
            if (amount > 0) {
                drop(world, x, y, z, resource.toStack(amount));
            }
        }
    }

    public static void dropAll(Level world, BlockPos pos, NonNullList<ItemStack> toDrop) {
        for (ItemStack stack : toDrop) {
            if (stack == null) {
                throw new NullPointerException("Null stack!");
            }
            drop(world, pos, stack);
        }
    }

    public static void drop(Level world, BlockPos pos, @NotNull ItemStack stack) {
        Block.popResource(world, pos, stack);
    }

    public static void drop(Level world, Vec3 vec, @NotNull ItemStack stack) {
        drop(world, vec.x, vec.y, vec.z, stack);
    }

    public static void drop(Level world, double x, double y, double z, @NotNull ItemStack stack) {
        if (stack.isEmpty()) {
            return;
        }
        ItemEntity entity = new ItemEntity(world, x, y, z, stack);
        world.addFreshEntity(entity);
    }

    /** Adds every stack from src to dst. Doesn't add empty stacks. */
    public static void addAll(ResourceHandler<ItemResource> src, NonNullList<ItemStack> dst) {
        for (int i = 0; i < src.size(); i++) {
            ItemResource resource = src.getResource(i);
            if (!resource.isEmpty()) {
                dst.add(resource.toStack(src.getAmountAsInt(i)));
            }
        }
    }

    /** Adds the given {@link ItemStack} to the player's inventory, or drops it in front of them if there was not
     * enough room. {@code EntityPlayer#dropItem(stack, false, false)}'s modern equivalent takes a
     * {@link Prediction} instead of a second boolean; {@link Prediction#SERVER_ONLY} matches 1.12.2's
     * pre-prediction behaviour, where nothing was ever predicted client-side. */
    public static void addToPlayer(Player player, ItemStack stack) {
        if (player.getInventory().add(stack)) {
            player.inventoryMenu.broadcastChanges();
        } else {
            player.drop(stack, false, Prediction.SERVER_ONLY);
        }
    }

    // NBT migration
}
