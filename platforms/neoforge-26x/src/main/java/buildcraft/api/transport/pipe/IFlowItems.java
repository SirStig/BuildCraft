/*
 * Copyright (c) 2026 Joshua Kac -- NeoForge port (BuildCraft 10)
 *
 * The BuildCraft API is distributed under the terms of the MIT License. Please check the contents of the
 * license, which should be located as "LICENSE.API" in the BuildCraft source code distribution.
 */
package buildcraft.api.transport.pipe;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.core.Direction;

import buildcraft.api.core.IStackFilter;
import buildcraft.api.transport.IInjectable;

public interface IFlowItems extends IInjectable {

    /** @deprecated Use the version below with a simulate paramater. */
    @Deprecated
    default int tryExtractItems(int count, Direction from, @Nullable DyeColor colour, IStackFilter filter) {
        return tryExtractItems(count, from, colour, filter, false);
    }

    /** Attempts to extract items from the inventory connected to this pipe on the given side.
     * 
     * @param count The maximum number of items to extract
     * @param from The direction to extract from.
     * @param colour The colour that extracted items should be painted.
     * @param filter The filter to determine what can be extracted.
     * @param simulate If true then only simulate the extraction.
     * @return The number of items extracted. */
    int tryExtractItems(int count, Direction from, @Nullable DyeColor colour, IStackFilter filter, boolean simulate);

    /** Inserts an item directly into the centre of this pipe, going in the given direction. This should ONLY be called
     * from an instance of {@link PipeBehaviour}, as otherwise it can lead to problems. (For example crashing if a pipe
     * contains an item that the pipe behaviour doesn't expect).
     * 
     * @param stack ItemStack offered for addition. Do not manipulate this!
     * @param from Orientation the ItemStack should pretend to be coming from.
     * @param colour The colour of the item to be added to the pipe, or null for no colour.
     * @param speed The speed of the item to be added (in blocks per tick) or {@code <=0} if a default should be
     *            used. */
    void insertItemsForce(@NotNull ItemStack stack, Direction from, @Nullable DyeColor colour, double speed);

    /** Sends a phantom (fake) item from the given facing, to the other facing. If from is null then it will start at
     * the center, or if to is null then it will end at the center. */
    void sendPhantomItem(@NotNull ItemStack stack, @Nullable Direction from, @Nullable Direction to, @Nullable DyeColor colour);
}
