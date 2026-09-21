/*
 * Copyright (c) 2026 Joshua Kac -- NeoForge port (BuildCraft 10)
 *
 * The BuildCraft API is distributed under the terms of the MIT License. Please check the contents of the
 * license, which should be located as "LICENSE.API" in the BuildCraft source code distribution.
 */
package buildcraft.api.lists;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import net.minecraft.world.item.Item;

/** Every {@link ListMatchHandler} mods have registered, plus the item classes lists treat as a type. */
public final class ListRegistry {

    public static final List<Class<? extends Item>> itemClassAsType = new CopyOnWriteArrayList<>();

    private static final List<ListMatchHandler> handlers = new CopyOnWriteArrayList<>();

    private ListRegistry() {
    }

    public static void registerHandler(@org.jetbrains.annotations.Nullable ListMatchHandler h) {
        if (h != null) {
            handlers.add(h);
        }
    }

    public static List<ListMatchHandler> getHandlers() {
        return Collections.unmodifiableList(handlers);
    }
}
