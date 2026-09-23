/*
 * Copyright (c) 2026 Joshua Kac -- NeoForge port (BuildCraft 10)
 *
 * This file is part of the BuildCraft 10 port and is distributed under the terms of the MIT License.
 * Please check the contents of the license, which should be located as "LICENSE.PORT" in the BuildCraft
 * source code distribution.
 */
package buildcraft.lib.fluid;

import java.util.function.Supplier;

import org.jetbrains.annotations.Nullable;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.material.Fluid;

import net.minecraftforge.common.capabilities.ICapabilityProvider;
import net.minecraftforge.fluids.capability.wrappers.FluidBucketWrapper;

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

    /** Forge's {@code Supplier} constructor, so the bucket never needs the fluid resolved at construction. */
    public BCFluidBucketItem(Supplier<? extends Fluid> fluid, Properties properties) {
        super(fluid, properties);
    }

    /** Forge's {@code BucketItem#initCapabilities} only hands out a {@link FluidBucketWrapper} when
     * {@code getClass() == BucketItem.class}, so without this override none of BuildCraft's buckets had a fluid
     * handler: a full oil or fuel bucket could not be emptied into a tank or engine (found while testing the
     * Combustion Engine). {@link FluidBucketWrapper} reads any {@link BucketItem}'s fluid, so it serves ours as-is. */
    @Override
    public ICapabilityProvider initCapabilities(ItemStack stack, @Nullable CompoundTag nbt) {
        return new FluidBucketWrapper(stack);
    }

    @Override
    public Component getName(ItemStack stack) {
        return Component.translatable("item.buildcraft.fluid_bucket", getFluid().getFluidType().getDescription());
    }
}
