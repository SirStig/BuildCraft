/*
 * Copyright (c) 2026 Joshua Kac -- NeoForge port (BuildCraft 10)
 *
 * The BuildCraft API is distributed under the terms of the MIT License. Please check the contents of the
 * license, which should be located as "LICENSE.API" in the BuildCraft source code distribution.
 */
package buildcraft.api.filler;

import java.util.Collection;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.BlockPos;

import buildcraft.api.statements.IStatement;

/** Every filler pattern BuildCraft and other mods have registered. */
public interface IFillerRegistry {
    void addPattern(IFillerPattern pattern);

    /** @return An {@link IFillerPattern} from its {@link IStatement#getUniqueTag()}. */
    @Nullable
    IFillerPattern getPattern(String name);

    Collection<IFillerPattern> getPatterns();

    IFilledTemplate createFilledTemplate(BlockPos pos, BlockPos size);
}
