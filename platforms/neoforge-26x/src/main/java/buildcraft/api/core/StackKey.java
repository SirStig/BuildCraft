/*
 * Copyright (c) 2011-2015, SpaceToad and the BuildCraft Team http://www.mod-buildcraft.com
 * Copyright (c) 2026 Joshua Kac -- NeoForge port (BuildCraft 10)
 *
 * The BuildCraft API is distributed under the terms of the MIT License. Please check the contents of the
 * license, which should be located as "LICENSE.API" in the BuildCraft source code distribution.
 */
package buildcraft.api.core;

import org.jetbrains.annotations.Nullable;

import net.minecraft.world.item.ItemStack;

import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.item.ItemResource;

/**
 * Used whenever stacks need to be stored as map keys.
 *
 * <p>The 1.12.2 version hand-wrote equality as "same item, plus same damage if the item has subtypes, plus
 * equal NBT tag". Every clause of that is obsolete: damage is durability rather than a subtype discriminator,
 * {@code getHasSubtypes} is gone, and NBT on a stack is data components. The modern spelling of the whole thing
 * is {@link ItemResource}, which is a value type with exactly these semantics and is what the transfer API uses
 * as a key already -- so this delegates to it rather than reimplementing the comparison.
 *
 * <p>New code should prefer {@link ItemResource} and {@link FluidResource} directly. This remains for the
 * recipe code that still passes stacks around, and because 1.20.1 has no equivalent type.
 *
 * <p>Note the amount is deliberately not part of the key for items, matching 1.12.2, but <em>is</em> for
 * fluids, also matching 1.12.2 -- {@code fluidStack.amount != k.fluidStack.amount} made two different amounts
 * unequal there.
 */
public final class StackKey {

    @Nullable
    public final ItemStack stack;

    @Nullable
    public final FluidStack fluidStack;

    private final int hash;

    public StackKey(@Nullable ItemStack stack, @Nullable FluidStack fluidStack) {
        this.stack = stack;
        this.fluidStack = fluidStack;
        int result = 7;
        if (stack != null && !stack.isEmpty()) {
            result = 31 * result + ItemResource.of(stack).hashCode();
        }
        if (fluidStack != null && !fluidStack.isEmpty()) {
            result = 31 * result + FluidResource.of(fluidStack).hashCode();
            result = 31 * result + fluidStack.getAmount();
        }
        this.hash = result;
    }

    public StackKey(ItemStack stack) {
        this(stack, null);
    }

    public StackKey(FluidStack fluidStack) {
        this(null, fluidStack);
    }

    public static StackKey stack(ItemStack itemStack) {
        return new StackKey(itemStack);
    }

    public static StackKey fluid(FluidStack fluidStack) {
        return new StackKey(fluidStack);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || o.getClass() != StackKey.class) {
            return false;
        }
        StackKey other = (StackKey) o;
        if (!sameItem(stack, other.stack)) {
            return false;
        }
        return sameFluid(fluidStack, other.fluidStack);
    }

    private static boolean sameItem(@Nullable ItemStack a, @Nullable ItemStack b) {
        boolean aEmpty = a == null || a.isEmpty();
        boolean bEmpty = b == null || b.isEmpty();
        if (aEmpty || bEmpty) {
            return aEmpty == bEmpty;
        }
        return ItemStack.isSameItemSameComponents(a, b);
    }

    private static boolean sameFluid(@Nullable FluidStack a, @Nullable FluidStack b) {
        boolean aEmpty = a == null || a.isEmpty();
        boolean bEmpty = b == null || b.isEmpty();
        if (aEmpty || bEmpty) {
            return aEmpty == bEmpty;
        }
        return FluidStack.isSameFluidSameComponents(a, b) && a.getAmount() == b.getAmount();
    }

    @Override
    public int hashCode() {
        return hash;
    }

    @Override
    public String toString() {
        return "StackKey[" + stack + ", " + fluidStack + "]";
    }
}
