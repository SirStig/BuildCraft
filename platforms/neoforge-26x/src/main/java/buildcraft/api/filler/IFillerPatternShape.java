/*
 * Copyright (c) 2011-2015, SpaceToad and the BuildCraft Team http://www.mod-buildcraft.com
 * Copyright (c) 2026 Joshua Kac -- NeoForge port (BuildCraft 10)
 *
 * The BuildCraft API is distributed under the terms of the MIT License. Please check the contents of the
 * license, which should be located as "LICENSE.API" in the BuildCraft source code distribution.
 */
package buildcraft.api.filler;

import org.jetbrains.annotations.Nullable;

import net.minecraft.world.level.Level;

import buildcraft.api.statements.IStatementParameter;
import buildcraft.api.statements.containers.IFillerStatementContainer;

/** An {@link IFillerPattern} whose shape is independent of the {@link Level} it is built in. */
public interface IFillerPatternShape extends IFillerPattern {
    /**
     * @param filledTemplate An empty template.
     * @return True if the template was filled, or false if this shouldn't make a template for the given params.
     */
    boolean fillTemplate(IFilledTemplate filledTemplate, IStatementParameter[] params);

    @Nullable
    @Override
    default IFilledTemplate createTemplate(IFillerStatementContainer filler, IStatementParameter[] params) {
        IFilledTemplate template = FillerManager.registry.createFilledTemplate(
            filler.getBox().min(),
            filler.getBox().size()
        );
        if (!fillTemplate(template, params)) {
            return null;
        }
        return template;
    }
}
