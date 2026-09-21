/*
 * Copyright (c) 2026 Joshua Kac -- NeoForge port (BuildCraft 10)
 *
 * The BuildCraft API is distributed under the terms of the MIT License. Please check the contents of the
 * license, which should be located as "LICENSE.API" in the BuildCraft source code distribution.
 */
package buildcraft.api.enums;

import java.util.Locale;

import net.minecraft.util.StringRepresentable;

import buildcraft.api.core.IEngineType;

/**
 * The engine kinds, and where each one's inventory model lives.
 *
 * <p>1.12.2 built the model path from the owning mod id -- {@code buildcraftcore:blocks/engine/inv/wood},
 * {@code buildcraftenergy:blocks/engine/inv/stone} -- because the engines were split across two of the eight mods.
 * Those collapsed into a single {@code buildcraft} id, so the mod name is no longer part of the path, and the
 * resource folder is {@code block/} rather than {@code blocks/} as of 1.13.
 */
public enum EnumEngineType implements StringRepresentable, IEngineType {
    WOOD("wood"),
    STONE("stone"),
    IRON("iron"),
    CREATIVE("creative"),
    RF("rf");

    public static final EnumEngineType[] VALUES = values();

    /** Kept local rather than referencing the mod class, so the api package stays self-contained. */
    private static final String NAMESPACE = "buildcraft";

    public final String serializedName;
    public final String modelLocation;

    EnumEngineType(String name) {
        this.serializedName = name;
        this.modelLocation = NAMESPACE + ":block/engine/inv/" + name;
    }

    @Override
    public String getItemModelLocation() {
        return modelLocation;
    }

    @Override
    public String getSerializedName() {
        return serializedName;
    }

    /** Block metadata is gone; the index survives only as a compact wire form. */
    public static EnumEngineType fromIndex(int index) {
        if (index < 0 || index >= VALUES.length) {
            return WOOD;
        }
        return VALUES[index];
    }

    static {
        // The lower-case assumption above is what lets these be used as blockstate property values.
        for (EnumEngineType type : VALUES) {
            assert type.serializedName.equals(type.serializedName.toLowerCase(Locale.ROOT));
        }
    }
}
