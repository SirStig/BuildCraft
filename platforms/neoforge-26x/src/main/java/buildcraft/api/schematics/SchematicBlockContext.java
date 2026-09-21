/*
 * Copyright (c) 2026 Joshua Kac -- NeoForge port (BuildCraft 10)
 *
 * The BuildCraft API is distributed under the terms of the MIT License. Please check the contents of the
 * license, which should be located as "LICENSE.API" in the BuildCraft source code distribution.
 */
package buildcraft.api.schematics;

import org.jetbrains.annotations.NotNull;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

public class SchematicBlockContext {
    @NotNull
    public final Level level;
    @NotNull
    public final BlockPos basePos;
    @NotNull
    public final BlockPos pos;
    @NotNull
    public final BlockState blockState;
    @NotNull
    public final Block block;

    public SchematicBlockContext(@NotNull Level level,
                                 @NotNull BlockPos basePos,
                                 @NotNull BlockPos pos,
                                 @NotNull BlockState blockState,
                                 @NotNull Block block) {
        this.level = level;
        this.basePos = basePos;
        this.pos = pos;
        this.blockState = blockState;
        this.block = block;
    }
}
