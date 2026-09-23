/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * Copyright (c) 2026 Joshua Kac -- NeoForge port (BuildCraft 10)
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL
 * was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.transport.stripes;

import java.util.Collections;
import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.vehicle.AbstractMinecart;
import net.minecraft.world.entity.vehicle.AbstractMinecartContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

import buildcraft.api.transport.IStripesActivator;
import buildcraft.api.transport.IStripesHandlerBlock;

/** A port of 1.12.2's own {@code StripesHandlerMinecartDestroy}: destroys a minecart sat on the block beyond the
 * pipe's open face, sending back its contents (if it is a chest/hopper/furnace minecart) plus its own item form.
 * {@code EntityMinecart#getCartItem()} is {@link AbstractMinecart#getPickResult()} on this target;
 * {@code setDead()} is {@link AbstractMinecart#discard()}. */
public enum StripesHandlerMinecartDestroy implements IStripesHandlerBlock {
    INSTANCE;

    @Override
    public boolean handle(Level level, BlockPos pos, Direction direction, Player player, IStripesActivator activator) {
        AABB box = new AABB(pos);
        List<AbstractMinecart> minecarts = level.getEntitiesOfClass(AbstractMinecart.class, box);

        if (!minecarts.isEmpty()) {
            Collections.shuffle(minecarts);
            AbstractMinecart cart = minecarts.get(0);
            if (cart instanceof AbstractMinecartContainer container) {
                for (int i = 0; i < container.getContainerSize(); i++) {
                    ItemStack s = container.getItem(i);
                    if (!s.isEmpty()) {
                        container.setItem(i, ItemStack.EMPTY);
                        if (container.getItem(i).isEmpty()) {
                            activator.sendItem(s, direction);
                        }
                    }
                }
            }
            cart.discard();
            activator.sendItem(cart.getPickResult(), direction);
            return true;
        }
        return false;
    }
}
