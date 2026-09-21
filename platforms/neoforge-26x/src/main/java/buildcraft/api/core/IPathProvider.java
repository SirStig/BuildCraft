/*
 * Copyright (c) 2026 Joshua Kac -- NeoForge port (BuildCraft 10)
 *
 * The BuildCraft API is distributed under the terms of the MIT License. Please check the contents of the
 * license, which should be located as "LICENSE.API" in the BuildCraft source code distribution.
 */
package buildcraft.api.core;

import java.util.List;

import net.minecraft.core.BlockPos;

import buildcraft.api.items.IMapLocation.MapLocationType;

/** Implemented by block entities able to provide a path in the world, typically BuildCraft path markers. */
public interface IPathProvider {
    /**
     * @return The completed path. This should loop back onto itself -- the last position being the same as the
     *         first -- if you are {@link MapLocationType#PATH_REPEATING}.
     */
    List<BlockPos> getPath();

    /** Remove from the world all objects used to define the path. */
    void removeFromWorld();
}
