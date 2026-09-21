/*
 * Copyright (c) 2026 Joshua Kac -- NeoForge port (BuildCraft 10)
 *
 * The BuildCraft API is distributed under the terms of the MIT License. Please check the contents of the
 * license, which should be located as "LICENSE.API" in the BuildCraft source code distribution.
 */
package buildcraft.api.enums;

import java.util.Locale;

import net.minecraft.util.StringRepresentable;

/**
 * The decorative variants of BuildCraft's decorated block.
 *
 * <p>{@code fromMeta(int)} is now {@link #fromIndex(int)}: block metadata is gone, and this value is a real
 * blockstate property. The index is still meaningful as a compact wire form, so the lookup is kept.
 */
public enum EnumDecoratedBlock implements StringRepresentable {
    DESTROY(0),
    BLUEPRINT(10),
    TEMPLATE(10),
    PAPER(10),
    LEATHER(10),
    LASER_BACK(0);

    public static final EnumDecoratedBlock[] VALUES = values();

    public final int lightValue;

    private final String serializedName = name().toLowerCase(Locale.ROOT);

    EnumDecoratedBlock(int lightValue) {
        this.lightValue = lightValue;
    }

    @Override
    public String getSerializedName() {
        return serializedName;
    }

    public static EnumDecoratedBlock fromIndex(int index) {
        if (index < 0 || index >= VALUES.length) {
            return DESTROY;
        }
        return VALUES[index];
    }
}
