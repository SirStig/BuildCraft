/*
 * Copyright (c) 2011-2015, SpaceToad and the BuildCraft Team http://www.mod-buildcraft.com
 * Copyright (c) 2026 Joshua Kac -- NeoForge port (BuildCraft 10)
 *
 * The BuildCraft API is distributed under the terms of the MIT License. Please check the contents of the
 * license, which should be located as "LICENSE.API" in the BuildCraft source code distribution.
 */
package buildcraft.api.filler;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.BlockPos;

import buildcraft.api.core.render.ISprite;
import buildcraft.api.statements.IStatement;
import buildcraft.api.statements.IStatementParameter;
import buildcraft.api.statements.containers.IFillerStatementContainer;

/** A type of statement that is used for filler patterns. */
public interface IFillerPattern extends IStatement {
    /**
     * @param filler The filler to create the pattern for. NOTE: this should never be called when
     *            {@link IFillerStatementContainer#hasBox()} returns false.
     * @return The template to fill, which should be created with
     *         {@link IFillerRegistry#createFilledTemplate(BlockPos, BlockPos)}, or null if this shouldn't make a
     *         template for the given filler.
     */
    @Nullable
    IFilledTemplate createTemplate(IFillerStatementContainer filler, IStatementParameter[] params);

    @Override
    IFillerPattern[] getPossible();

    /**
     * Note that this sprite <em>must</em> be stitched to the block atlas, as it is drawn on the side of the filler
     * block.
     */
    @Override
    ISprite getSprite();
}
