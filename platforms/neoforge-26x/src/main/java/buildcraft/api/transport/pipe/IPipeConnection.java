/*
 * Copyright (c) 2011-2015, SpaceToad and the BuildCraft Team http://www.mod-buildcraft.com
 * Copyright (c) 2026 Joshua Kac -- NeoForge port (BuildCraft 10)
 *
 * The BuildCraft API is distributed under the terms of the MIT License. Please check the contents of the
 * license, which should be located as "LICENSE.API" in the BuildCraft source code distribution.
 */
package buildcraft.api.transport.pipe;

import net.minecraft.core.Direction;

/**
 * @deprecated Test whether this is necessary, or perhaps make it a capability.
 */
@Deprecated
public interface IPipeConnection {

    enum ConnectOverride {
        CONNECT,
        DISCONNECT,
        DEFAULT
    }

    /**
     * Allows you to override pipe connection logic.
     *
     * @return CONNECT to force a connection, DISCONNECT to force no connection, and DEFAULT to let the pipe
     *         decide.
     */
    ConnectOverride overridePipeConnection(Object type, Direction with);
}
