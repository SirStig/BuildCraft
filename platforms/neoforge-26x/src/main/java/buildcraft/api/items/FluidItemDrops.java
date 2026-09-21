/*
 * Copyright (c) 2026 Joshua Kac -- NeoForge port (BuildCraft 10)
 *
 * The BuildCraft API is distributed under the terms of the MIT License. Please check the contents of the
 * license, which should be located as "LICENSE.API" in the BuildCraft source code distribution.
 */
package buildcraft.api.items;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.NonNullList;
import net.minecraft.world.item.ItemStack;

import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.fluid.FluidResource;

/**
 * Turns the fluid a machine was holding into items when it is broken.
 *
 * <p>The tank overload took {@code IFluidTank...} in 1.12.2. That interface is gone on 26.x along with the rest
 * of the capability transfer API; the equivalent is a {@link ResourceHandler} of {@link FluidResource}, which is
 * slot-indexed, so the contents are read out per slot rather than as one stack per tank.
 */
public final class FluidItemDrops {

    @Nullable
    public static IItemFluidShard item;

    private FluidItemDrops() {
    }

    public static void addFluidDrops(NonNullList<ItemStack> toDrop, FluidStack... fluids) {
        IItemFluidShard shard = item;
        if (shard == null) {
            return;
        }
        for (FluidStack fluid : fluids) {
            shard.addFluidDrops(toDrop, fluid);
        }
    }

    @SafeVarargs
    public static void addFluidDrops(NonNullList<ItemStack> toDrop, ResourceHandler<FluidResource>... tanks) {
        IItemFluidShard shard = item;
        if (shard == null) {
            return;
        }
        for (ResourceHandler<FluidResource> tank : tanks) {
            for (int slot = 0; slot < tank.size(); slot++) {
                FluidResource resource = tank.getResource(slot);
                int amount = tank.getAmountAsInt(slot);
                if (!resource.isEmpty() && amount > 0) {
                    shard.addFluidDrops(toDrop, new FluidStack(resource.typeHolder(), amount));
                }
            }
        }
    }
}
