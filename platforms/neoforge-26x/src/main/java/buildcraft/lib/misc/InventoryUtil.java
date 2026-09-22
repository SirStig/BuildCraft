/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * Copyright (c) 2026 Joshua Kac -- NeoForge port (BuildCraft 10)
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL
 * was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.lib.misc;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
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

import buildcraft.api.inventory.IItemTransactor;

import buildcraft.lib.inventory.ItemTransactorHelper;

/**
 * Ported subset of 1.12.2's {@code InventoryUtil}: the drop/spawn helpers, which have no dependency beyond
 * vanilla and the transfer API, plus {@link #addToBestAcceptor}, landed alongside {@code TileMiningWell} (this
 * method's first caller) once {@code ItemTransactorHelper}/{@code ItemTransactorCapabilities} existed to build it
 * on.
 *
 * <p>{@code addToRandomInjectable} is not ported, and {@link #addToBestAcceptor} only does the
 * {@code addToRandomInventory} half of what 1.12.2's version did -- see that method's own javadoc for why.
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

    /** Attempts to add {@code stack} to a random neighbouring inventory, falling back to dropping it on the
     * ground at {@code pos} if nothing around it will take it (or all of it).
     *
     * <p>1.12.2's version tried {@code IInjectable} (pipes) first, then {@code IItemHandler}. The
     * {@code IInjectable} half is not ported -- {@code buildcraft.transport} (pipes) is not ported at all yet,
     * the same gap {@code ItemTransactorHelper}'s own javadoc already notes for its dropped
     * {@code getInjectable}/{@code wrapInjectable} -- so this only does the {@code IItemHandler}-equivalent half,
     * through {@link ItemTransactorHelper#getTransactor(Level, BlockPos, Direction)}. {@code ignore} is kept for
     * call-site parity with 1.12.2 (which used it only to steer the dropped {@code IInjectable} half away from
     * the direction an item arrived from) but is currently unused, since there is no injectable half left for it
     * to steer.
     */
    public static void addToBestAcceptor(Level level, BlockPos pos, @Nullable Direction ignore, ItemStack stack) {
        if (stack.isEmpty()) {
            return;
        }
        ItemStack remaining = stack.copy();
        List<Direction> toTry = new ArrayList<>(List.of(Direction.values()));
        Collections.shuffle(toTry);
        for (Direction face : toTry) {
            if (remaining.isEmpty()) {
                break;
            }
            IItemTransactor transactor = ItemTransactorHelper.getTransactor(level, pos.relative(face), face.getOpposite());
            int inserted;
            try (Transaction transaction = Transaction.openRoot()) {
                inserted = transactor.insert(ItemResource.of(remaining), remaining.getCount(), false, transaction);
                if (inserted > 0) {
                    transaction.commit();
                }
            }
            if (inserted > 0) {
                remaining.shrink(inserted);
            }
        }
        if (!remaining.isEmpty()) {
            drop(level, pos, remaining);
        }
    }

    // NBT migration
}
