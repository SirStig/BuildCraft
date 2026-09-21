/*
 * Copyright (c) 2026 Joshua Kac -- NeoForge port (BuildCraft 10)
 *
 * The BuildCraft API is distributed under the terms of the MIT License. Please check the contents of the
 * license, which should be located as "LICENSE.API" in the BuildCraft source code distribution.
 */
package buildcraft.api.blocks;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * Provides a way to paint blocks from any position. Either implement this on a block, or register an instance for a
 * block with {@link CustomPaintHelper}.
 */
public interface ICustomPaintHandler {
    /**
     * Attempts to paint the given block. This can also paint only a specific part of the block, as the hit position
     * is given.
     *
     * @param level The level that the block is contained within.
     * @param pos The position of the block.
     * @param state The current state of the block.
     * @param hitPos The absolute hit position of the paintbrush, relative to the level's origin.
     * @param hitSide The side of the block that was hit.
     * @param paintColour The colour to paint with, or null if the paint should be cleared -- so if this was a
     *            stained glass block and null was passed, this would set it to normal, clear, non-stained glass.
     * @return {@link InteractionResult#SUCCESS} if the block changed, {@link InteractionResult#FAIL} if it could
     *         have been handled but was already that colour, or {@link InteractionResult#PASS} if this handler has
     *         no idea how to handle the block.
     */
    InteractionResult attemptPaint(
        Level level,
        BlockPos pos,
        BlockState state,
        Vec3 hitPos,
        @Nullable Direction hitSide,
        @Nullable DyeColor paintColour
    );
}
