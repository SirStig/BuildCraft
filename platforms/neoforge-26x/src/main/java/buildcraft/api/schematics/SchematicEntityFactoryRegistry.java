/*
 * Copyright (c) 2026 Joshua Kac -- NeoForge port (BuildCraft 10)
 *
 * The BuildCraft API is distributed under the terms of the MIT License. Please check the contents of the
 * license, which should be located as "LICENSE.API" in the BuildCraft source code distribution.
 */
package buildcraft.api.schematics;

import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Predicate;
import java.util.function.Supplier;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import com.google.common.collect.ImmutableList;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.resources.Identifier;

import buildcraft.api.core.BuildCraftAPI;

public class SchematicEntityFactoryRegistry {
    private static final Set<SchematicEntityFactory<?>> FACTORIES = new TreeSet<>();

    public static <S extends ISchematicEntity> void registerFactory(String name,
                                                                    int priority,
                                                                    Predicate<SchematicEntityContext> predicate,
                                                                    Supplier<S> supplier) {
        FACTORIES.add(new SchematicEntityFactory<>(
            BuildCraftAPI.nameToResourceId(name),
            priority,
            predicate,
            supplier
        ));
    }

    public static <S extends ISchematicEntity> void registerFactory(String name,
                                                                    int priority,
                                                                    List<Identifier> entities,
                                                                    Supplier<S> supplier) {
        registerFactory(
            name,
            priority,
            context -> entities.contains(BuiltInRegistries.ENTITY_TYPE.getKey(context.entity.getType())),
            supplier
        );
    }

    public static List<SchematicEntityFactory<?>> getFactories() {
        return ImmutableList.copyOf(FACTORIES);
    }

    @NotNull
    public static <S extends ISchematicEntity> SchematicEntityFactory<S> getFactoryByInstance(S instance) {
        // noinspection unchecked
        return (SchematicEntityFactory<S>) FACTORIES.stream()
            .filter(schematicEntityFactory -> schematicEntityFactory.clazz == instance.getClass())
            .findFirst()
            .orElseThrow(() -> new IllegalStateException("Didn't find a factory for " + instance.getClass()));
    }

    @Nullable
    public static SchematicEntityFactory<?> getFactoryByName(Identifier name) {
        return FACTORIES.stream()
            .filter(schematicEntityFactory -> schematicEntityFactory.name.equals(name))
            .findFirst()
            .orElse(null);
    }
}
