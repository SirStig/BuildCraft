/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * Copyright (c) 2026 Joshua Kac -- NeoForge port (BuildCraft 10)
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL
 * was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.lib.inventory;

import org.jetbrains.annotations.NotNull;

import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.AbstractArrow.Pickup;
import net.minecraft.world.entity.projectile.SpectralArrow;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ItemStack;

import buildcraft.api.core.IStackFilter;
import buildcraft.api.inventory.IItemTransactor.IItemExtractable;

import buildcraft.lib.misc.StackUtil;

public class TransactorEntityArrow implements IItemExtractable {

    private final AbstractArrow entity;

    public TransactorEntityArrow(AbstractArrow entity) {
        this.entity = entity;
    }

    @NotNull
    @Override
    public ItemStack extract(IStackFilter filter, int min, int max, boolean simulate) {
        if (entity.isRemoved() || entity.pickup != Pickup.ALLOWED || min > 1 || max < 1 || max < min) {
            return StackUtil.EMPTY;
        }

        // 1.12.2's EntityArrow#getArrowStack was protected, so this could only guess an arrow's item form from
        // its Java type rather than call it (tipped/potion arrows were never handled -- see the original FIXME).
        // getPickupItem() is still protected on this target too, so the same guess is kept rather than invented
        // anew; see the 26.x copy of this file for why that target no longer needs it.
        ItemStack stack = entity instanceof SpectralArrow ? new ItemStack(Items.SPECTRAL_ARROW) : new ItemStack(Items.ARROW);
        if (!simulate) {
            entity.discard();
        }
        return stack;
    }
}
