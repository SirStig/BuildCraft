/*
 * Copyright (c) 2011-2017, SpaceToad and the BuildCraft Team https://mod-buildcraft.com/
 * Copyright (c) 2026 Joshua Kac -- NeoForge port (BuildCraft 10)
 *
 * The BuildCraft API is distributed under the terms of the MIT License. Please check the contents of the
 * license, which should be located as "LICENSE.API" in the BuildCraft source code distribution.
 */
package buildcraft.api.transport.pipe;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import buildcraft.api.transport.IStripesActivator;

/** Drives the stripes pipe's ability to lay and retract a pipeline ahead of itself. */
public interface IPipeExtensionManager {

    /**
     * Requests an extension by one block from an {@link IStripesActivator}, usually a stripes pipe, using the
     * pipe supplied by the stack: the stripes pipe moves forward and the new pipe is placed behind it.
     *
     * <p>If the pipe is a registered retraction pipe -- by default only the void pipe is, register one with
     * {@link #registerRetractionPipe} -- the pipeline retracts instead, moving the stripes pipe one block in
     * the opposite direction and replacing the previous pipe.
     *
     * @param pos The origin of the request, usually the stripes pipe's position.
     * @param dir The direction of the proposed extension.
     * @param stack The pipe stack to use. Only one item is used; the rest is sent back.
     * @return True on success, false otherwise.
     */
    boolean requestPipeExtension(
        Level level,
        BlockPos pos,
        Direction dir,
        IStripesActivator stripes,
        ItemStack stack
    );

    /** Registers a pipe as a retraction trigger for pipe extension requests. */
    void registerRetractionPipe(PipeDefinition pipeDefinition);
}
