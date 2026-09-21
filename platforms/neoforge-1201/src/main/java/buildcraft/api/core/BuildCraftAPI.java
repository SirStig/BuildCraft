/*
 * Copyright (c) 2011-2015, SpaceToad and the BuildCraft Team http://www.mod-buildcraft.com
 * Copyright (c) 2026 Joshua Kac -- NeoForge port (BuildCraft 10)
 *
 * The BuildCraft API is distributed under the terms of the MIT License. Please check the contents of the
 * license, which should be located as "LICENSE.API" in the BuildCraft source code distribution.
 */
package buildcraft.api.core;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;

import net.minecraftforge.fml.ModList;

/** The handful of global hooks BuildCraft exposes to other mods. */
public final class BuildCraftAPI {

    /** The mod id everything BuildCraft registers now lives under. See {@code buildcraft.BuildCraft}. */
    public static final String MOD_ID = "buildcraft";

    @Nullable
    public static IFakePlayerProvider fakePlayerProvider;

    public static final Set<Block> softBlocks = new HashSet<>();
    public static final Map<String, IWorldProperty> worldProperties = new HashMap<>();

    private BuildCraftAPI() {
    }

    /**
     * 1.12.2 asked FML for the {@code buildcraftlib} container. The eight mod ids collapsed into one during the
     * port, so this now reports the version of the single {@code buildcraft} mod.
     */
    public static String getVersion() {
        return ModList.get()
            .getModContainerById(MOD_ID)
            .map(container -> container.getModInfo().getVersion().toString())
            .orElse("UNKNOWN VERSION");
    }

    @Nullable
    public static IWorldProperty getWorldProperty(String name) {
        return worldProperties.get(name);
    }

    public static void registerWorldProperty(String name, IWorldProperty property) {
        if (worldProperties.containsKey(name)) {
            BCLog.logger.warn(
                "The WorldProperty key '" + name + "' is being overridden with "
                    + property.getClass().getSimpleName() + "!"
            );
        }
        worldProperties.put(name, property);
    }

    public static boolean isSoftBlock(Level level, BlockPos pos) {
        IWorldProperty soft = worldProperties.get("soft");
        return soft != null && soft.get(level, pos);
    }

    /**
     * Resolves a bare name against BuildCraft's namespace, or parses it whole if it already carries one.
     *
     * <p>1.12.2 inferred the namespace from FML's <em>active mod container</em>, which only had a meaningful value
     * during a mod's own loading callback. There is no such ambient state now -- loading is parallel -- so the
     * fallback namespace is BuildCraft's own rather than "whoever happens to be loading".
     *
     * <p>1.20.1 still constructs a {@link ResourceLocation} directly; the {@code parse}/{@code fromNamespaceAndPath}
     * factories the 26.x copy uses only arrived in 1.21.
     */
    public static ResourceLocation nameToResourceId(String name) {
        if (name.indexOf(':') > 0) {
            return new ResourceLocation(name);
        }
        return new ResourceLocation(MOD_ID, name);
    }
}
