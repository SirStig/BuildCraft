/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * The BuildCraft API is distributed under the terms of the MIT License. Please check the contents of the license, which
 * should be located as "LICENSE.API" in the BuildCraft source code distribution.
 */
package buildcraft.api.blocks;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import buildcraft.api.core.BCDebugging;
import buildcraft.api.core.BCLog;

/**
 * Provides a simple way to paint a single block, iterating through all {@link ICustomPaintHandler}s registered for
 * the block.
 *
 * <p>Two things changed in the port. {@code Block.REGISTRY} became {@link BuiltInRegistries#BLOCK}, and the
 * default fallback no longer has Forge's {@code recolorBlock} hook to call -- it was removed with no replacement
 * on either target -- so it goes through {@link DyedBlockVariants} instead. See that class for what it covers.
 */
public enum CustomPaintHelper {
    INSTANCE;

    /* If you want to test your class-based painting registration then add the system property
     * "-Dbuildcraft.api.painting.debug.class=true" to your launch. */
    private static final boolean DEBUG = BCDebugging.shouldDebugLog("api.painting");

    private final Map<Block, List<ICustomPaintHandler>> handlers = new IdentityHashMap<>();
    private final List<ICustomPaintHandler> allHandlers = new ArrayList<>();

    /**
     * Registers a handler that will be called LAST for ALL blocks, if all other paint handlers have returned PASS
     * or none are registered for that block.
     */
    public void registerHandlerForAll(ICustomPaintHandler handler) {
        if (DEBUG) {
            BCLog.logger.info("[api.painting] Adding a paint handler for ALL blocks (" + handler.getClass() + ")");
        }
        allHandlers.add(handler);
    }

    /** Registers a paint handler for every block of a given class. */
    public void registerHandlerForAll(Class<? extends Block> blockClass, ICustomPaintHandler handler) {
        for (Block block : BuiltInRegistries.BLOCK) {
            Class<? extends Block> foundClass = block.getClass();
            if (blockClass.isAssignableFrom(foundClass)) {
                if (DEBUG) {
                    BCLog.logger.info(
                        "[api.painting] Found an assignable block " + CustomRotationHelper.nameOf(block) + " ("
                            + foundClass + ") for " + blockClass
                    );
                }
                registerHandlerInternal(block, handler);
            }
        }
    }

    public void registerHandler(Block block, ICustomPaintHandler handler) {
        if (registerHandlerInternal(block, handler)) {
            if (DEBUG) {
                BCLog.logger.info(
                    "[api.painting] Setting a paint handler for block " + CustomRotationHelper.nameOf(block) + " ("
                        + handler.getClass() + ")"
                );
            }
        } else if (DEBUG) {
            BCLog.logger.info(
                "[api.painting] Adding another paint handler for block " + CustomRotationHelper.nameOf(block) + " ("
                    + handler.getClass() + ")"
            );
        }
    }

    private boolean registerHandlerInternal(Block block, ICustomPaintHandler handler) {
        List<ICustomPaintHandler> forBlock = handlers.get(block);
        if (forBlock == null) {
            forBlock = new ArrayList<>();
            forBlock.add(handler);
            handlers.put(block, forBlock);
            return true;
        }
        forBlock.add(handler);
        return false;
    }

    /** Attempts to paint a block at the given position, iterating through all registered paint handlers. */
    public InteractionResult attemptPaintBlock(
        Level level,
        BlockPos pos,
        BlockState state,
        Vec3 hitPos,
        @Nullable Direction hitSide,
        @Nullable DyeColor paint
    ) {
        Block block = state.getBlock();
        if (block instanceof ICustomPaintHandler custom) {
            return custom.attemptPaint(level, pos, state, hitPos, hitSide, paint);
        }
        List<ICustomPaintHandler> forBlock = handlers.get(block);
        if (forBlock != null) {
            for (ICustomPaintHandler handler : forBlock) {
                InteractionResult result = handler.attemptPaint(level, pos, state, hitPos, hitSide, paint);
                if (result != InteractionResult.PASS) {
                    return result;
                }
            }
        }
        return defaultAttemptPaint(level, pos, state, hitPos, hitSide, paint);
    }

    private InteractionResult defaultAttemptPaint(
        Level level,
        BlockPos pos,
        BlockState state,
        Vec3 hitPos,
        @Nullable Direction hitSide,
        @Nullable DyeColor paint
    ) {
        for (ICustomPaintHandler handler : allHandlers) {
            InteractionResult result = handler.attemptPaint(level, pos, state, hitPos, hitSide, paint);
            if (result != InteractionResult.PASS) {
                return result;
            }
        }
        if (paint == null) {
            // Clearing paint has no generic form: there is no way to know which colour is the "plain" one.
            return InteractionResult.FAIL;
        }
        Block block = state.getBlock();
        Block recoloured = DyedBlockVariants.recolour(block, paint);
        if (recoloured == null) {
            return InteractionResult.FAIL;
        }
        if (recoloured == block) {
            // Already that colour. FAIL rather than PASS, matching 1.12.2: the handler understood the block.
            return InteractionResult.FAIL;
        }
        if (!level.isClientSide()) {
            level.setBlockAndUpdate(pos, copyState(state, recoloured));
        }
        return InteractionResult.SUCCESS;
    }

    /**
     * Moves every property the two blocks share onto the recoloured block's default state, so that painting a
     * slab, stair or glass pane does not reset its shape, facing or waterlogging.
     */
    private static BlockState copyState(BlockState from, Block to) {
        BlockState result = to.defaultBlockState();
        for (net.minecraft.world.level.block.state.properties.Property<?> property : from.getProperties()) {
            if (result.hasProperty(property)) {
                result = copyProperty(from, result, property);
            }
        }
        return result;
    }

    private static <T extends Comparable<T>> BlockState copyProperty(
        BlockState from,
        BlockState to,
        net.minecraft.world.level.block.state.properties.Property<T> property
    ) {
        return to.setValue(property, from.getValue(property));
    }
}
