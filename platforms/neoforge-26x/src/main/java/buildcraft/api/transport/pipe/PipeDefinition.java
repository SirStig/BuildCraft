/*
 * Copyright (c) 2026 Joshua Kac -- NeoForge port (BuildCraft 10)
 *
 * The BuildCraft API is distributed under the terms of the MIT License. Please check the contents of the
 * license, which should be located as "LICENSE.API" in the BuildCraft source code distribution.
 */
package buildcraft.api.transport.pipe;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;

/**
 * Everything that makes one kind of pipe: its id, textures, behaviour constructor and flow type.
 *
 * <p>Two changes worth knowing about.
 *
 * <p>The builder's {@code id()} and {@code texPrefix()} called a private {@code getActiveModId()}, which asked
 * FML for the <em>active mod container</em> and threw if there was not one. That only ever had a meaningful
 * value inside a mod's own loading callback, and there is no such ambient state now that loading is parallel.
 * The builder therefore carries an explicit namespace, defaulting to BuildCraft's own; call
 * {@link PipeDefinitionBuilder#namespace(String)} first if you are another mod.
 *
 * <p>The deprecated {@code itemTextureTop}/{@code Center}/{@code Bottom} int fields are dropped. They were
 * superseded by the {@link PipeFaceTex} triple in the same class and their own javadoc was a truncated
 * "Use #"; carrying two parallel representations of the same thing through a port is not worth it.
 */
public final class PipeDefinition {

    public final Identifier identifier;
    public final IPipeCreator logicConstructor;
    public final IPipeLoader logicLoader;
    public final PipeFlowType flowType;
    public final String[] textures;
    public final PipeFaceTex itemModelTop;
    public final PipeFaceTex itemModelCenter;
    public final PipeFaceTex itemModelBottom;
    public final boolean canBeColoured;

    @Nullable
    private EnumPipeColourType colourType;

    public PipeDefinition(PipeDefinitionBuilder builder) {
        this.identifier = builder.identifier;
        this.textures = new String[builder.textureSuffixes.length];
        for (int i = 0; i < textures.length; i++) {
            textures[i] = builder.texturePrefix + builder.textureSuffixes[i];
        }
        this.logicConstructor = builder.logicConstructor;
        this.logicLoader = builder.logicLoader;
        this.flowType = builder.flowType;
        this.itemModelBottom = builder.itemModelBottom;
        this.itemModelCenter = builder.itemModelCenter;
        this.itemModelTop = builder.itemModelTop;
        this.canBeColoured = builder.canBeColoured;
        this.colourType = builder.colourType;
    }

    @NotNull
    public EnumPipeColourType getColourType() {
        if (colourType != null) {
            return colourType;
        }
        if (flowType.fallbackColourType != null) {
            return flowType.fallbackColourType;
        }
        return EnumPipeColourType.TRANSLUCENT;
    }

    public void setColourType(@Nullable EnumPipeColourType colourType) {
        this.colourType = colourType;
    }

    @FunctionalInterface
    public interface IPipeCreator {
        PipeBehaviour createBehaviour(IPipe pipe);
    }

    @FunctionalInterface
    public interface IPipeLoader {
        PipeBehaviour loadBehaviour(IPipe pipe, CompoundTag tag, HolderLookup.Provider registries);
    }

    public static class PipeDefinitionBuilder {

        /** Kept local rather than referencing the mod class, so the api package stays self-contained. */
        private static final String DEFAULT_NAMESPACE = "buildcraft";

        public Identifier identifier;
        public String texturePrefix;
        public String[] textureSuffixes = { "" };
        public IPipeCreator logicConstructor;
        public IPipeLoader logicLoader;
        public PipeFlowType flowType;
        public PipeFaceTex itemModelTop = PipeFaceTex.get(0);
        public PipeFaceTex itemModelCenter = PipeFaceTex.get(0);
        public PipeFaceTex itemModelBottom = PipeFaceTex.get(0);
        public boolean canBeColoured;

        @Nullable
        public EnumPipeColourType colourType;

        /** Replaces FML's "active mod container", which no longer exists. */
        private String namespace = DEFAULT_NAMESPACE;

        public PipeDefinitionBuilder() {
        }

        public PipeDefinitionBuilder(
            Identifier identifier,
            IPipeCreator logicConstructor,
            IPipeLoader logicLoader,
            PipeFlowType flowType
        ) {
            this.identifier = identifier;
            this.logicConstructor = logicConstructor;
            this.logicLoader = logicLoader;
            this.flowType = flowType;
        }

        /** Sets the namespace used by {@link #id(String)} and {@link #texPrefix(String)}. */
        public PipeDefinitionBuilder namespace(String namespace) {
            this.namespace = namespace;
            return this;
        }

        public PipeDefinitionBuilder idTexPrefix(String both) {
            return id(both).texPrefix(both);
        }

        public PipeDefinitionBuilder idTex(String both) {
            return id(both).tex(both);
        }

        public PipeDefinitionBuilder id(String post) {
            identifier = Identifier.fromNamespaceAndPath(namespace, post);
            return this;
        }

        public PipeDefinitionBuilder tex(String prefix, String... suffixes) {
            return texPrefix(prefix).texSuffixes(suffixes);
        }

        /** Sets the texture prefix to {@code <namespace>:pipes/<prefix>}. */
        public PipeDefinitionBuilder texPrefix(String prefix) {
            return texPrefixDirect(namespace + ":pipes/" + prefix);
        }

        /** Sets {@link #texturePrefix} to the input string verbatim, unlike {@link #texPrefix(String)}. */
        public PipeDefinitionBuilder texPrefixDirect(String prefix) {
            texturePrefix = prefix;
            return this;
        }

        /** Sets {@link #textureSuffixes}, or to <code>{""}</code> if the argument list is empty or null. */
        public PipeDefinitionBuilder texSuffixes(String... suffixes) {
            if (suffixes == null || suffixes.length == 0) {
                textureSuffixes = new String[] { "" };
            } else {
                textureSuffixes = suffixes;
            }
            return this;
        }

        public PipeDefinitionBuilder itemTex(int all) {
            itemModelBottom = PipeFaceTex.get(all);
            itemModelCenter = itemModelBottom;
            itemModelTop = itemModelBottom;
            return this;
        }

        public PipeDefinitionBuilder itemTex(int top, int center, int bottom) {
            itemModelBottom = PipeFaceTex.get(bottom);
            itemModelCenter = PipeFaceTex.get(center);
            itemModelTop = PipeFaceTex.get(top);
            return this;
        }

        public PipeDefinitionBuilder logic(IPipeCreator creator, IPipeLoader loader) {
            logicConstructor = creator;
            logicLoader = loader;
            return this;
        }

        public PipeDefinitionBuilder disableColouring() {
            canBeColoured = false;
            return this;
        }

        public PipeDefinitionBuilder enableColouring(@Nullable EnumPipeColourType type) {
            canBeColoured = true;
            colourType = type;
            return this;
        }

        public PipeDefinitionBuilder enableColouring() {
            return enableColouring(null);
        }

        public PipeDefinitionBuilder enableTranslucentColouring() {
            return enableColouring(EnumPipeColourType.TRANSLUCENT);
        }

        public PipeDefinitionBuilder enableBorderColouring() {
            return enableColouring(EnumPipeColourType.BORDER_OUTER);
        }

        public PipeDefinitionBuilder enableInnerBorderColouring() {
            return enableColouring(EnumPipeColourType.BORDER_INNER);
        }

        public PipeDefinitionBuilder enableCustomColouring() {
            return enableColouring(EnumPipeColourType.CUSTOM);
        }

        public PipeDefinitionBuilder flowItem() {
            return flow(PipeApi.flowItems);
        }

        public PipeDefinitionBuilder flowFluid() {
            return flow(PipeApi.flowFluids);
        }

        public PipeDefinitionBuilder flowPower() {
            return flow(PipeApi.flowPower);
        }

        public PipeDefinitionBuilder flow(PipeFlowType flow) {
            flowType = flow;
            return this;
        }

        public PipeDefinition define() {
            PipeDefinition def = new PipeDefinition(this);
            PipeApi.pipeRegistry.registerPipe(def);
            return def;
        }
    }
}
