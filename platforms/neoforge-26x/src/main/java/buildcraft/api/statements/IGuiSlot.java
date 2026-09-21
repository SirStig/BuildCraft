/*
 * Copyright (c) 2026 Joshua Kac -- NeoForge port (BuildCraft 10)
 *
 * The BuildCraft API is distributed under the terms of the MIT License. Please check the contents of the
 * license, which should be located as "LICENSE.API" in the BuildCraft source code distribution.
 */
package buildcraft.api.statements;

import java.util.List;

import org.jetbrains.annotations.Nullable;

import net.minecraft.network.chat.Component;

import buildcraft.api.core.IConvertable;
import buildcraft.api.core.render.ISprite;

/**
 * Something that occupies a slot in one of BuildCraft's GUIs: a trigger, an action, or a parameter of one.
 *
 * <p>Two changes from 1.12.2. The description and tooltip were {@code String}s built from a client-only
 * {@code I18n} lookup; they are {@link Component}s now, which carry the translation key and are resolved at draw
 * time, so they work on a server too.
 *
 * <p>That is also why the {@code @SideOnly(Side.CLIENT)} annotations are gone. They were there because the
 * old return values could only be produced on a client. A {@code Component} cannot be, and an annotation that
 * stops the class loading on a server would break anything holding a reference to this interface.
 */
public interface IGuiSlot extends IConvertable {
    /**
     * Every statement needs a unique tag, in the format {@code <modid>:<name>}.
     *
     * @return The unique id.
     */
    String getUniqueTag();

    /** @return The one-line description shown in the UI. Acts as a bridge for {@link #getTooltip()}. */
    Component getDescription();

    /** @return The full tooltip for the UI. */
    default List<Component> getTooltip() {
        Component desc = getDescription();
        if (desc == null) {
            return List.of();
        }
        return List.of(desc);
    }

    /** @return A sprite to show in a GUI, or null if this should not render a sprite. */
    @Nullable
    ISprite getSprite();
}
