/*
 * Copyright (c) 2026 Joshua Kac -- NeoForge port (BuildCraft 10)
 *
 * The BuildCraft API is distributed under the terms of the MIT License. Please check the contents of the
 * license, which should be located as "LICENSE.API" in the BuildCraft source code distribution.
 */
package buildcraft.api.registry;

import java.util.Collection;
import java.util.Map;

import net.minecraft.resources.ResourceLocation;

/** A registry of any Java object. This does not perform serialisation of any kind to the entries. */
public interface IReloadableRegistry<E> {

    public enum PackType {
        RESOURCE_PACK("assets"),
        DATA_PACK("data");

        public final String prefix;

        PackType(String prefix) {
            this.prefix = prefix;
        }
    }

    IReloadableRegistryManager getManager();

    default void reload() {
        getManager().reload(this);
    }

    /** Adds an entry permanently to this reloadable manager. This will throw an exception if used during
     * {@link EventBuildCraftReload} event firings. */
    <T extends E> T addPermanent(T recipe);

    /** @return A collection of every permanent entry added via {@link #addPermanent(Object)}. */
    Collection<E> getPermanent();

    /** @return A map of all the reloadable entries, mapped from name to value. */
    Map<ResourceLocation, E> getReloadableEntryMap();

    /** @return An iterable that has both {@link #getPermanent()} and
     *         {@link #getReloadableEntryMap()}.{@link Map#values() values()}. */
    Iterable<E> getAllEntries();
}
