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
 * @deprecated Superseded by {@link ILibraryTypeHandler}.
 */
@Deprecated
public abstract class LibraryTypeHandlerNBT extends LibraryTypeHandler {
    protected LibraryTypeHandlerNBT(String extension) {
        super(extension);
    }

    public abstract ItemStack load(ItemStack stack, CompoundTag nbt, HolderLookup.Provider registries);

    public abstract boolean store(ItemStack stack, CompoundTag nbt, HolderLookup.Provider registries);
}
