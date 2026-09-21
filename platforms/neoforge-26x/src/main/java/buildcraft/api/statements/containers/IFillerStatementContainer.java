/*
 * Copyright (c) 2026 Joshua Kac -- NeoForge port (BuildCraft 10)
 *
 * The BuildCraft API is distributed under the terms of the MIT License. Please check the contents of the
 * license, which should be located as "LICENSE.API" in the BuildCraft source code distribution.
 */
package buildcraft.api.statements.containers;

import org.jetbrains.annotations.Nullable;

import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

import buildcraft.api.core.IBox;
import buildcraft.api.filler.IFillerPattern;
import buildcraft.api.statements.IStatementContainer;
import buildcraft.api.statements.IStatementParameter;

/** A statement container that drives a filler: the filler block itself, or a volume box. */
public interface IFillerStatementContainer extends IStatementContainer {

    /** Unlike {@link IStatementContainer}, some containers are not block-entity based -- the volume box. */
    @Override
    @Nullable
    BlockEntity getTile();

    Level getFillerLevel();

    /** @return True if this filler has a non-zero sized box. */
    boolean hasBox();

    /**
     * @return The box that the filler will default to building in.
     * @throws IllegalStateException if {@link #hasBox()} returns false.
     */
    IBox getBox() throws IllegalStateException;

    void setPattern(IFillerPattern pattern, IStatementParameter[] params);
}
