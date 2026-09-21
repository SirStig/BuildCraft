/*
 * Copyright (c) 2026 Joshua Kac -- NeoForge port (BuildCraft 10)
 *
 * The BuildCraft API is distributed under the terms of the MIT License. Please check the contents of the
 * license, which should be located as "LICENSE.API" in the BuildCraft source code distribution.
 */
package buildcraft.api.library;

import net.minecraft.world.item.ItemStack;

/**
 * @deprecated Superseded by {@link ILibraryTypeHandler}, which does not require subclassing.
 */
@Deprecated
public abstract class LibraryTypeHandler {
    public enum HandlerType {
        LOAD,
        STORE
    }

    private final String extension;

    protected LibraryTypeHandler(String extension) {
        this.extension = extension;
    }

    public abstract boolean isHandler(ItemStack stack, HandlerType type);

    public boolean isInputExtension(String ext) {
        return extension.equals(ext);
    }

    public String getOutputExtension() {
        return extension;
    }

    public abstract int getTextColor();

    public abstract String getName(ItemStack stack);
}
