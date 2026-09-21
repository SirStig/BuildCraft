/*
 * Copyright (c) 2026 Joshua Kac -- NeoForge port (BuildCraft 10)
 *
 * The BuildCraft API is distributed under the terms of the MIT License. Please check the contents of the
 * license, which should be located as "LICENSE.API" in the BuildCraft source code distribution.
 */
package buildcraft.api.transport.pipe;

/**
 * Implemented by the real pipe item in the transport module, so that classes without a direct dependency on
 * transport can still recognise one.
 */
public interface IItemPipe {
    PipeDefinition getDefinition();
}
