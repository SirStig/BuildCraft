/*
 * Copyright (c) 2026 Joshua Kac -- NeoForge port (BuildCraft 10)
 *
 * The BuildCraft API is distributed under the terms of the MIT License. Please check the contents of the
 * license, which should be located as "LICENSE.API" in the BuildCraft source code distribution.
 */
package buildcraft.api.library;

import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;

import org.jetbrains.annotations.Nullable;

/**
 * @deprecated Superseded by the {@link ILibraryTypeHandler} registry in the builders module.
 */
@Deprecated
public final class LibraryAPI {

    private static final Set<LibraryTypeHandler> handlers = new CopyOnWriteArraySet<>();

    private LibraryAPI() {
    }

    public static Set<LibraryTypeHandler> getHandlerSet() {
        return handlers;
    }

    public static void registerHandler(LibraryTypeHandler handler) {
        handlers.add(handler);
    }

    @Nullable
    public static LibraryTypeHandler getHandlerFor(String extension) {
        for (LibraryTypeHandler h : handlers) {
            if (h.isInputExtension(extension)) {
                return h;
            }
        }
        return null;
    }
}
