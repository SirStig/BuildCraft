/*
 * Copyright (c) 2026 Joshua Kac -- NeoForge port (BuildCraft 10)
 *
 * The BuildCraft API is distributed under the terms of the MIT License. Please check the contents of the
 * license, which should be located as "LICENSE.API" in the BuildCraft source code distribution.
 */
package buildcraft.api.schematics;

import java.util.function.Predicate;
import java.util.function.Supplier;

import org.jetbrains.annotations.NotNull;

import net.minecraft.resources.Identifier;

public class SchematicBlockFactory<S extends ISchematicBlock> implements Comparable<SchematicBlockFactory<?>> {
    @NotNull
    public final Identifier name;
    public final int priority;
    @NotNull
    public final Predicate<SchematicBlockContext> predicate;
    @NotNull
    public final Supplier<S> supplier;
    @NotNull
    public final Class<S> clazz;

    @SuppressWarnings("unchecked")
    public SchematicBlockFactory(@NotNull Identifier name,
                                 int priority,
                                 @NotNull Predicate<SchematicBlockContext> predicate,
                                 @NotNull Supplier<S> supplier) {
        this.name = name;
        this.priority = priority;
        this.predicate = predicate;
        this.supplier = supplier;
        clazz = (Class<S>) supplier.get().getClass();
    }

    @Override
    public int compareTo(@NotNull SchematicBlockFactory o) {
        return priority != o.priority
                ? Integer.compare(priority, o.priority)
                : name.toString().compareTo(o.name.toString());
    }
}
