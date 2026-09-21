/*
 * Copyright (c) 2011-2015, SpaceToad and the BuildCraft Team http://www.mod-buildcraft.com
 * Copyright (c) 2026 Joshua Kac -- NeoForge port (BuildCraft 10)
 *
 * The BuildCraft API is distributed under the terms of the MIT License. Please check the contents of the
 * license, which should be located as "LICENSE.API" in the BuildCraft source code distribution.
 */
package buildcraft.api.facades;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

/** The facade item, which reads and writes the facade a stack represents. */
public interface IFacadeItem {

    @Nullable
    default FacadeType getFacadeType(@NotNull ItemStack stack) {
        IFacade facade = getFacade(stack);
        if (facade == null) {
            return null;
        }
        return facade.getType();
    }

    @NotNull
    ItemStack getFacadeForBlock(BlockState state);

    /**
     * @param facade The {@link IFacade} instance. NOTE: this MUST be an object returned from
     *            {@link IFacadeRegistry#createBasicFacade} or {@link IFacadeRegistry#createPhasedFacade},
     *            otherwise a {@link ClassCastException} will be thrown.
     */
    ItemStack createFacadeStack(IFacade facade);

    @Nullable
    IFacade getFacade(@NotNull ItemStack facade);
}
