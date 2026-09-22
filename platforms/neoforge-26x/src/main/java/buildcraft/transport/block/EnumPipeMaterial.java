/*
 * Copyright (c) 2026 Joshua Kac -- NeoForge port (BuildCraft 10)
 *
 * This file is part of the BuildCraft 10 port and is distributed under the terms of the MIT License.
 * Please check the contents of the license, which should be located as "LICENSE.PORT" in the BuildCraft
 * source code distribution.
 */
package buildcraft.transport.block;

import java.util.Locale;

import net.minecraft.util.StringRepresentable;

/**
 * The pipe materials {@link BlockPipeHolder} can carry, as a real blockstate property -- one value per
 * {@code PipeDefinition} currently registered in {@code BCTransportRegistries} ({@code PIPE_COBBLESTONE}/
 * {@code PIPE_WOOD}/{@code PIPE_STONE}/{@code PIPE_SANDSTONE}/{@code PIPE_QUARTZ}/{@code PIPE_GOLD}/
 * {@code PIPE_IRON}/{@code PIPE_CLAY}/{@code PIPE_VOID}), matching each definition's own
 * {@code identifier.getPath()} string exactly (confirmed against {@code BCTransportRegistries}: every one of
 * those definitions is built with {@code idTexPrefix(...)}, which sets both the registry id and the texture
 * prefix from the same single string, so {@code identifier.getPath()} -- {@code "cobblestone"}/{@code "wood"}/
 * {@code "stone"}/{@code "sandstone"}/{@code "quartz"}/{@code "gold"}/{@code "iron"}/{@code "clay"}/
 * {@code "void"} -- is the real, live material name, not a guess). New values are appended: a blockstate property
 * value is saved by its serialized name, so the order does not affect existing worlds either way.
 *
 * <p>The nine {@code *_fluid} values belong to the fluid pipes ({@code PIPE_*_FLUID}), whose definition ids keep
 * 1.12.2's own {@code <material>_fluid} naming ({@code buildcraft:cobblestone_fluid}, ...), so the same
 * {@code identifier.getPath()} lookup covers them with no special case.
 *
 * <p>New, transport-local, following the exact shape {@code buildcraft.api.enums.EnumEngineType} already
 * established for a custom blockstate enum on this port ({@link StringRepresentable}, a lower-case
 * {@link #getSerializedName()} enforced by the same static assertion) -- kept inside {@code buildcraft.transport}
 * rather than added to the shared {@code buildcraft.api.properties.BuildCraftProperties}/{@code buildcraft.api.
 * enums} classes, since (unlike {@code EnumEngineType}, which spans the {@code core}/{@code energy} modules) no
 * other module needs this property -- pipes are entirely within {@code buildcraft.transport}, so this stays
 * local rather than growing a shared API class for a single consumer.
 */
public enum EnumPipeMaterial implements StringRepresentable {
    COBBLESTONE("cobblestone"),
    WOOD("wood"),
    STONE("stone"),
    SANDSTONE("sandstone"),
    QUARTZ("quartz"),
    GOLD("gold"),
    IRON("iron"),
    CLAY("clay"),
    VOID("void"),
    // The fluid-carrying variants of the nine materials above: same behaviours, a different PipeDefinition
    // (flowFluid() instead of flowItem()) and their own textures -- see BCTransportRegistries.
    COBBLESTONE_FLUID("cobblestone_fluid"),
    WOOD_FLUID("wood_fluid"),
    STONE_FLUID("stone_fluid"),
    SANDSTONE_FLUID("sandstone_fluid"),
    QUARTZ_FLUID("quartz_fluid"),
    GOLD_FLUID("gold_fluid"),
    IRON_FLUID("iron_fluid"),
    CLAY_FLUID("clay_fluid"),
    VOID_FLUID("void_fluid");

    public static final EnumPipeMaterial[] VALUES = values();

    public final String serializedName;

    EnumPipeMaterial(String name) {
        this.serializedName = name;
    }

    @Override
    public String getSerializedName() {
        return serializedName;
    }

    /** Maps a {@code PipeDefinition.identifier.getPath()} string back to the matching value, falling back to
     * {@link #COBBLESTONE} for anything unrecognised -- mirrors {@code EnumEngineType#fromIndex}'s own defensive
     * fallback shape, for the same reason: this is read from live registry data, not user input, but a block
     * property setter still has to return *something* rather than throw if a future material is ever registered
     * without updating this enum in step. */
    public static EnumPipeMaterial fromId(String id) {
        for (EnumPipeMaterial material : VALUES) {
            if (material.serializedName.equals(id)) {
                return material;
            }
        }
        return COBBLESTONE;
    }

    static {
        // The lower-case assumption above is what lets these be used as blockstate property values.
        for (EnumPipeMaterial material : VALUES) {
            assert material.serializedName.equals(material.serializedName.toLowerCase(Locale.ROOT));
        }
    }
}
