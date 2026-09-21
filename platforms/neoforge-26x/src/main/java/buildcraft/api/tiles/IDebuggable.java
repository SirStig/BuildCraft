/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * The BuildCraft API is distributed under the terms of the MIT License. Please check the contents of the license, which
 * should be located as "LICENSE.API" in the BuildCraft source code distribution.
 */
package buildcraft.api.tiles;

import java.util.List;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.Direction;

/**
 * Exposes a block entity's internal state to BuildCraft's debug overlay.
 *
 * <p>The 1.12.2 version marked {@link #getClientDebugInfo} with {@code @SideOnly(Side.CLIENT)}. That annotation is
 * gone, and its replacement ({@code @OnlyIn}) is for vanilla-patching code rather than for a mod's own API -- a
 * class carrying it cannot be loaded on a server at all, which would break anything holding a reference to this
 * interface. The method is therefore plain, and is simply only called from client code.
 */
public interface IDebuggable {
    /**
     * Gets the debug information from a block entity as a list of strings, for the F3 debug menu. The left and
     * right parameters correspond to the sides of the F3 screen.
     *
     * @param side The side the block was clicked on. May be null if we don't know, or if it is the "centre" side.
     */
    void getDebugInfo(List<String> left, List<String> right, @Nullable Direction side);

    /**
     * As {@link #getDebugInfo(List, List, Direction)}, but only called on the client.
     *
     * @param side As for {@link #getDebugInfo(List, List, Direction)}.
     */
    default void getClientDebugInfo(List<String> left, List<String> right, @Nullable Direction side) {
    }
}
