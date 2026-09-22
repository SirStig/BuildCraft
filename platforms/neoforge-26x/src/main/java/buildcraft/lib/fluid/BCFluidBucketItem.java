/*
 * Copyright (c) 2026 Joshua Kac -- NeoForge port (BuildCraft 10)
 *
 * This file is part of the BuildCraft 10 port and is distributed under the terms of the MIT License.
 * Please check the contents of the license, which should be located as "LICENSE.PORT" in the BuildCraft
 * source code distribution.
 */
package buildcraft.lib.fluid;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.material.Fluid;

/**
 * A bucket of one BuildCraft fluid. 1.12.2 never registered bucket items of its own: {@code BCEnergy}'s static
 * block called {@code FluidRegistry.enableUniversalBucket()} and {@code FluidManager#register} called
 * {@code FluidRegistry.addBucketForFluid}, so every BuildCraft fluid lived inside Forge's single
 * {@code forge:bucketfilled} item as NBT. Neither the universal bucket nor anything like it exists on either
 * target any more, so each fluid now gets a real {@link BucketItem} of its own.
 *
 * <p>The only addition over a plain {@link BucketItem} is the name: the universal bucket displayed
 * {@code "<fluid name> Bucket"}, and doing that from the fluid's own (heat-suffixed) description through one
 * {@code item.buildcraft.fluid_bucket} format key avoids thirty near-identical lang entries.
 */
public class BCFluidBucketItem extends BucketItem {

    public BCFluidBucketItem(Fluid fluid, Properties properties) {
        super(fluid, properties);
    }

    @Override
    public Component getName(ItemStack stack) {
        return Component.translatable("item.buildcraft.fluid_bucket", content.getFluidType().getDescription());
    }
}
