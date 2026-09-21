/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * Copyright (c) 2026 Joshua Kac -- NeoForge port (BuildCraft 10)
 *
 * The BuildCraft API is distributed under the terms of the MIT License. Please check the contents of the
 * license, which should be located as "LICENSE.API" in the BuildCraft source code distribution.
 */
package buildcraft.api.items;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.NonNullList;
import net.minecraft.world.item.ItemStack;

import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.IFluidTank;

/**
 * Turns the fluid a machine was holding into items when it is broken.
 *
 * <p>The tank overload still takes {@link IFluidTank} here. That interface is gone on 26.x along with the rest
 * of the capability transfer API, so the 26.x copy takes a slot-indexed {@code ResourceHandler<FluidResource>}
 * and reads the contents out per slot instead.
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

    public static void addFluidDrops(NonNullList<ItemStack> toDrop, IFluidTank... tanks) {
        IItemFluidShard shard = item;
        if (shard == null) {
            return;
        }
        for (IFluidTank tank : tanks) {
            shard.addFluidDrops(toDrop, tank.getFluid());
        }
    }
}
