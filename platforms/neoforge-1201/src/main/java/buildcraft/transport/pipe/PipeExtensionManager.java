/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * Copyright (c) 2026 Joshua Kac -- NeoForge port (BuildCraft 10)
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL
 * was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.transport.pipe;

import java.util.HashSet;
import java.util.Set;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import buildcraft.api.transport.IStripesActivator;
import buildcraft.api.transport.pipe.IPipeExtensionManager;
import buildcraft.api.transport.pipe.PipeDefinition;

/**
 * 1.12.2's own {@code PipeExtensionManager} lets a stripes pipe carrying a pipe item lay (or, for a registered
 * "retraction" material -- the void pipe -- retract) a whole pipe run one block per tick, by relocating the
 * stripes pipe's own block entity via a {@code BlockSnapshot}/{@code FakePlayer}/cancellable-placement-event
 * dance so other mods' block-protection listeners still see and can veto each step.
 *
 * <p><b>{@link #requestPipeExtension} is a documented scope cut for this round, not a bug: it always declines.</b>
 * The real 1.12.2 {@code extend}/{@code retract} methods (roughly 200 lines together) relocate a live block entity
 * by hand -- copy its full NBT, break the old position, place the new one, restore the NBT at the new
 * coordinates, rebuild the wire-system cache around it -- while replaying cancellable vanilla placement events at
 * each step so protection mods keep working. That is substantial, novel engineering this round's time budget does
 * not cover with confidence on a block-entity-relocation API this port has never previously exercised. Declining
 * is safe: {@code StripesHandlerPipes} (this request's only real caller) treats "declined" exactly like "no
 * handler wanted this item" and lets it fall through to the pipe's ordinary item-ejection behaviour, so a pipe
 * item offered to a stripes pipe's open end is simply ejected as a normal item instead of being laid as a block.
 * {@link #registerRetractionPipe} is fully real (a plain set), ready for {@link #requestPipeExtension} to consult
 * whenever that relocation logic is built.
 */
public enum PipeExtensionManager implements IPipeExtensionManager {
    INSTANCE;

    private final Set<PipeDefinition> retractionPipeDefs = new HashSet<>();

    @Override
    public boolean requestPipeExtension(Level level, BlockPos pos, Direction dir, IStripesActivator stripes, ItemStack stack) {
        return false;
    }

    @Override
    public void registerRetractionPipe(PipeDefinition pipeDefinition) {
        if (pipeDefinition != null) {
            retractionPipeDefs.add(pipeDefinition);
        }
    }
}
