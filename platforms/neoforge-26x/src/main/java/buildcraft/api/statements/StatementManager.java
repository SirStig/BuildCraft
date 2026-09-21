/*
 * Copyright (c) 2011-2015, SpaceToad and the BuildCraft Team http://www.mod-buildcraft.com
 * Copyright (c) 2026 Joshua Kac -- NeoForge port (BuildCraft 10)
 *
 * The BuildCraft API is distributed under the terms of the MIT License. Please check the contents of the
 * license, which should be located as "LICENSE.API" in the BuildCraft source code distribution.
 */
package buildcraft.api.statements;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.level.block.entity.BlockEntity;

/**
 * The registry of every trigger, action and parameter, and of the providers that contribute them.
 *
 * <p>Two changes beyond the type renames. Both reader interfaces take a {@link HolderLookup.Provider}, because
 * reading a parameter can mean reading an {@link net.minecraft.world.item.ItemStack}, which needs registry access
 * on 26.x. And the provider lists are {@link CopyOnWriteArrayList} rather than {@code LinkedList}: mod loading is
 * parallel now, so two mods registering a provider at the same time is ordinary rather than impossible.
 *
 * <p>{@code registerParameter(IParameterReader)} no longer derives the parameter's name by reading an empty tag
 * and asking the result for its tag. That worked in 1.12.2 because reading an empty compound was cheap and total;
 * with registry-backed components it means constructing a parameter with no registries to hand. The name is now
 * passed explicitly.
 */
public final class StatementManager {

    public static final Map<String, IStatement> statements = new HashMap<>();
    public static final Map<String, IParameterReader> parameters = new HashMap<>();
    public static final Map<String, IParamReaderBuf> paramsBuf = new HashMap<>();

    private static final List<ITriggerProvider> triggerProviders = new CopyOnWriteArrayList<>();
    private static final List<IActionProvider> actionProviders = new CopyOnWriteArrayList<>();

    static {
        registerParameter(
            "buildcraft:stack",
            StatementParameterItemStack::new,
            (buffer, registries) -> new StatementParameterItemStack(buffer.readNbt(), registries)
        );
    }

    @FunctionalInterface
    public interface IParameterReader {
        IStatementParameter readFromNbt(CompoundTag nbt, HolderLookup.Provider registries);
    }

    @FunctionalInterface
    public interface IParamReaderBuf {
        IStatementParameter readFromBuf(FriendlyByteBuf buffer, HolderLookup.Provider registries);
    }

    /** Deactivate constructor */
    private StatementManager() {
    }

    public static void registerTriggerProvider(@Nullable ITriggerProvider provider) {
        if (provider != null && !triggerProviders.contains(provider)) {
            triggerProviders.add(provider);
        }
    }

    public static void registerActionProvider(@Nullable IActionProvider provider) {
        if (provider != null && !actionProviders.contains(provider)) {
            actionProviders.add(provider);
        }
    }

    public static void registerStatement(IStatement statement) {
        statements.put(statement.getUniqueTag(), statement);
    }

    /** Registers a parameter that reads itself from NBT, and from a buffer by way of that same NBT. */
    public static void registerParameter(String name, IParameterReader reader) {
        registerParameter(name, reader, (buffer, registries) -> reader.readFromNbt(buffer.readNbt(), registries));
    }

    /**
     * Registers a parameter with a hand-written buffer form, for anything that can serialise itself more compactly
     * than its NBT.
     */
    public static void registerParameter(String name, IParameterReader reader, IParamReaderBuf bufReader) {
        parameters.put(name, reader);
        paramsBuf.put(name, bufReader);
    }

    public static List<ITriggerExternal> getExternalTriggers(Direction side, BlockEntity entity) {
        if (entity instanceof IOverrideDefaultStatements override) {
            List<ITriggerExternal> result = override.overrideTriggers();
            if (result != null) {
                return result;
            }
        }

        LinkedHashSet<ITriggerExternal> triggers = new LinkedHashSet<>();
        for (ITriggerProvider provider : triggerProviders) {
            provider.addExternalTriggers(triggers, side, entity);
        }
        return new ArrayList<>(triggers);
    }

    public static List<IActionExternal> getExternalActions(Direction side, BlockEntity entity) {
        if (entity instanceof IOverrideDefaultStatements override) {
            List<IActionExternal> result = override.overrideActions();
            if (result != null) {
                return result;
            }
        }

        LinkedHashSet<IActionExternal> actions = new LinkedHashSet<>();
        for (IActionProvider provider : actionProviders) {
            provider.addExternalActions(actions, side, entity);
        }
        return new ArrayList<>(actions);
    }

    public static List<ITriggerInternal> getInternalTriggers(IStatementContainer container) {
        LinkedHashSet<ITriggerInternal> triggers = new LinkedHashSet<>();
        for (ITriggerProvider provider : triggerProviders) {
            provider.addInternalTriggers(triggers, container);
        }
        return new ArrayList<>(triggers);
    }

    public static List<IActionInternal> getInternalActions(IStatementContainer container) {
        LinkedHashSet<IActionInternal> actions = new LinkedHashSet<>();
        for (IActionProvider provider : actionProviders) {
            provider.addInternalActions(actions, container);
        }
        return new ArrayList<>(actions);
    }

    public static List<ITriggerInternalSided> getInternalSidedTriggers(
        IStatementContainer container,
        Direction side
    ) {
        LinkedHashSet<ITriggerInternalSided> triggers = new LinkedHashSet<>();
        for (ITriggerProvider provider : triggerProviders) {
            provider.addInternalSidedTriggers(triggers, container, side);
        }
        return new ArrayList<>(triggers);
    }

    public static List<IActionInternalSided> getInternalSidedActions(IStatementContainer container, Direction side) {
        LinkedHashSet<IActionInternalSided> actions = new LinkedHashSet<>();
        for (IActionProvider provider : actionProviders) {
            provider.addInternalSidedActions(actions, container, side);
        }
        return new ArrayList<>(actions);
    }

    @Nullable
    public static IParameterReader getParameterReader(String kind) {
        return parameters.get(kind);
    }

    @Nullable
    public static IParamReaderBuf getParameterBufReader(String kind) {
        return paramsBuf.get(kind);
    }
}
