/*
 * Copyright (c) 2026 Joshua Kac -- NeoForge port (BuildCraft 10)
 *
 * The BuildCraft API is distributed under the terms of the MIT License. Please check the contents of the
 * license, which should be located as "LICENSE.API" in the BuildCraft source code distribution.
 */
package buildcraft.api.transport.pipe;

import java.util.Map;
import java.util.function.Consumer;

import org.jetbrains.annotations.Nullable;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;

/** Every pipe kind BuildCraft and other mods have registered, and the item for each. */
public interface IPipeRegistry {

    @Nullable
    PipeDefinition getDefinition(ResourceLocation identifier);

    void registerPipe(PipeDefinition definition);

    /**
     * Maps the given {@link PipeDefinition} to an {@link IItemPipe}, exactly as
     * {@link Map#put(Object, Object)} does.
     */
    void setItemForPipe(PipeDefinition definition, @Nullable IItemPipe item);

    @Nullable
    IItemPipe getItemForPipe(PipeDefinition definition);

    /**
     * Creates an {@link IItemPipe} for the given {@link PipeDefinition}. If the definition was registered with
     * {@link #registerPipe(PipeDefinition)} then it is also registered with {@link #setItemForPipe}. The
     * returned item is registered with the loader automatically.
     */
    IItemPipe createItemForPipe(PipeDefinition definition);

    /**
     * Identical to {@link #createItemForPipe(PipeDefinition)}, but does not require registering tags with
     * BuildCraft lib first.
     *
     * <p>{@code postCreate} configured the item's registry name and unlocalised name in 1.12.2. Neither exists
     * now -- a registry object carries its own id, and the display name comes from the lang JSON -- so the
     * hook is kept only for whatever else an implementor wants to do to the item before it is registered.
     */
    IItemPipe createUnnamedItemForPipe(PipeDefinition definition, Consumer<Item> postCreate);

    Iterable<PipeDefinition> getAllRegisteredPipes();
}
