/*
 * Copyright (c) 2026 Joshua Kac -- NeoForge port (BuildCraft 10)
 *
 * The BuildCraft API is distributed under the terms of the MIT License. Please check the contents of the
 * license, which should be located as "LICENSE.API" in the BuildCraft source code distribution.
 */
package buildcraft.api.library;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;

/**
 * Reads and writes one kind of item the engineering library can store, such as a blueprint or a book.
 *
 * <p>{@code NBTTagCompound} is {@link CompoundTag}, and both methods gained a {@link HolderLookup.Provider}:
 * anything storing an {@link ItemStack} needs registry access on 26.x, because a stack's data components are
 * registry-backed. The 1.20.1 copy takes the same argument and ignores it, so the two stay one signature.
 */
public interface ILibraryTypeHandler {
    boolean isHandler(ItemStack stack, boolean store);

    String getFileExtension();

    int getTextColor();

    String getName(ItemStack stack);

    ItemStack load(ItemStack stack, CompoundTag compound, HolderLookup.Provider registries);

    boolean store(ItemStack stack, CompoundTag compound, HolderLookup.Provider registries);
}
