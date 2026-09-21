/*
 * Copyright (c) 2026 Joshua Kac -- NeoForge port (BuildCraft 10)
 *
 * The BuildCraft API is distributed under the terms of the MIT License. Please check the contents of the
 * license, which should be located as "LICENSE.API" in the BuildCraft source code distribution.
 */
package buildcraft.api.transport.pipe;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.jetbrains.annotations.Nullable;

import net.minecraft.world.level.block.Block;

/**
 * Register blocks with custom sizes here so that pipes connect to them properly.
 *
 * <p>You do not need to register a connection if your block implements {@link ICustomPipeConnection}; the
 * registered version does not override your own implementation.
 *
 * <p>The map is a {@link ConcurrentHashMap} because mod loading is parallel now, so two mods registering at
 * the same time is ordinary rather than impossible.
 */
public final class PipeConnectionAPI {

    private static final Map<Block, ICustomPipeConnection> connections = new ConcurrentHashMap<>();
    private static final ICustomPipeConnection NOTHING = (level, pos, face, state) -> 0;

    private PipeConnectionAPI() {
    }

    /**
     * Registers a block with a custom connection. Useful if you do not own the block class, or are adding it
     * for someone else.
     */
    public static void registerConnection(Block block, ICustomPipeConnection connection) {
        connections.put(block, connection);
    }

    /**
     * Ensures that a particular block will always have the default connection, no matter what its bounding box
     * is.
     */
    public static void registerConnectionAsNothing(Block block) {
        connections.put(block, NOTHING);
    }

    /** @return The custom connection the block uses, or null if nothing has been set. */
    @Nullable
    public static ICustomPipeConnection getCustomConnection(Block block) {
        if (block instanceof ICustomPipeConnection custom) {
            return custom;
        }
        return connections.get(block);
    }
}
