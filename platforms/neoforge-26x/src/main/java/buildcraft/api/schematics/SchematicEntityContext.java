/*
 * Copyright (c) 2026 Joshua Kac -- NeoForge port (BuildCraft 10)
 *
 * The BuildCraft API is distributed under the terms of the MIT License. Please check the contents of the
 * license, which should be located as "LICENSE.API" in the BuildCraft source code distribution.
 */
package buildcraft.api.schematics;

import org.jetbrains.annotations.NotNull;

import net.minecraft.world.entity.Entity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

public class SchematicEntityContext {
    @NotNull
    public final Level level;
    @NotNull
    public final BlockPos basePos;
    @NotNull
    public final Entity entity;

    public SchematicEntityContext(@NotNull Level level,
                                  @NotNull BlockPos basePos,
                                  @NotNull Entity entity) {
        this.level = level;
        this.basePos = basePos;
        this.entity = entity;
    }
}
