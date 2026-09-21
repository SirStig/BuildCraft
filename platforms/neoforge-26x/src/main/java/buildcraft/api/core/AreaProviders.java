/*
 * Copyright (c) 2026 Joshua Kac -- NeoForge port (BuildCraft 10)
 *
 * The BuildCraft API is distributed under the terms of the MIT License. Please check the contents of the
 * license, which should be located as "LICENSE.API" in the BuildCraft source code distribution.
 */
package buildcraft.api.core;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

/** Registry of everything that can answer "which marked-out areas contain this position?". */
public class AreaProviders {
    public static final List<IAreaProviderGetter> providers = new ArrayList<>();

    public interface IAreaProviderGetter {
        /** @return All of the {@link IAreaProvider}s that contain the specified block position. */
        List<IAreaProvider> getAreaProviders(Level level, BlockPos at);
    }

    public static List<IAreaProvider> getAreaProviders(Level level, BlockPos at) {
        List<IAreaProvider> list = new ArrayList<>();
        for (IAreaProviderGetter getter : providers) {
            list.addAll(getter.getAreaProviders(level, at));
        }
        return list;
    }
}
