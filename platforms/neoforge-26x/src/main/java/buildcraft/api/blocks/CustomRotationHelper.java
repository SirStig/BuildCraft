/*
 * Copyright (c) 2026 Joshua Kac -- NeoForge port (BuildCraft 10)
 *
 * The BuildCraft API is distributed under the terms of the MIT License. Please check the contents of the
 * license, which should be located as "LICENSE.API" in the BuildCraft source code distribution.
 */
package buildcraft.api.blocks;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import buildcraft.api.core.BCDebugging;
import buildcraft.api.core.BCLog;

/**
 * Rotates blocks for BuildCraft's wrench, through handlers registered per block.
 *
 * <p>The only change from 1.12.2 is the iteration source: {@code Block.REGISTRY} is now
 * {@link BuiltInRegistries#BLOCK}, and a block's id comes from the registry rather than from
 * {@code Block.getRegistryName()}, which no longer exists.
 *
 * <p>Note that {@link #registerHandlerForAll(Class, ICustomRotationHandler)} walks the block registry at the point
 * it is called, so it must run after block registration has finished -- the same constraint it had in 1.12.2.
 */
public enum CustomRotationHelper {
    INSTANCE;

    /* If you want to test your class-based rotation registration then add the system property
     * "-Dbuildcraft.api.rotation.debug.class=true" to your launch. */
    private static final boolean DEBUG = BCDebugging.shouldDebugLog("api.rotation");

    private final Map<Block, List<ICustomRotationHandler>> handlers = new IdentityHashMap<>();

    public void registerHandlerForAll(Class<? extends Block> blockClass, ICustomRotationHandler handler) {
        for (Block block : BuiltInRegistries.BLOCK) {
            Class<? extends Block> foundClass = block.getClass();
            if (blockClass.isAssignableFrom(foundClass)) {
                if (DEBUG) {
                    BCLog.logger.info(
                        "[api.rotation] Found an assignable block " + nameOf(block) + " (" + foundClass + ") for "
                            + blockClass
                    );
                }
                registerHandlerInternal(block, handler);
            }
        }
    }

    public void registerHandler(Block block, ICustomRotationHandler handler) {
        if (registerHandlerInternal(block, handler)) {
            if (DEBUG) {
                BCLog.logger.info("[api.rotation] Setting a rotation handler for block " + nameOf(block));
            }
        } else if (DEBUG) {
            BCLog.logger.info("[api.rotation] Adding another rotation handler for block " + nameOf(block));
        }
    }

    private boolean registerHandlerInternal(Block block, ICustomRotationHandler handler) {
        List<ICustomRotationHandler> forBlock = handlers.get(block);
        if (forBlock == null) {
            forBlock = new ArrayList<>();
            forBlock.add(handler);
            handlers.put(block, forBlock);
            return true;
        }
        forBlock.add(handler);
        return false;
    }

    public InteractionResult attemptRotateBlock(Level level, BlockPos pos, BlockState state, Direction sideWrenched) {
        Block block = state.getBlock();
        if (block instanceof ICustomRotationHandler custom) {
            return custom.attemptRotation(level, pos, state, sideWrenched);
        }
        List<ICustomRotationHandler> forBlock = handlers.get(block);
        if (forBlock == null) {
            return InteractionResult.PASS;
        }
        for (ICustomRotationHandler handler : forBlock) {
            InteractionResult result = handler.attemptRotation(level, pos, state, sideWrenched);
            if (result != InteractionResult.PASS) {
                return result;
            }
        }
        return InteractionResult.PASS;
    }

    static String nameOf(Block block) {
        return BuiltInRegistries.BLOCK.getKey(block).toString();
    }
}
