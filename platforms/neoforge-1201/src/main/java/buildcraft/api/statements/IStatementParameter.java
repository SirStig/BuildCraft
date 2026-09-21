/*
 * Copyright (c) 2011-2015, SpaceToad and the BuildCraft Team http://www.mod-buildcraft.com
 * Copyright (c) 2026 Joshua Kac -- NeoForge port (BuildCraft 10)
 *
 * The BuildCraft API is distributed under the terms of the MIT License. Please check the contents of the
 * license, which should be located as "LICENSE.API" in the BuildCraft source code distribution.
 */
package buildcraft.api.statements;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.item.ItemStack;

import buildcraft.api.statements.StatementManager.IParamReaderBuf;
import buildcraft.api.statements.StatementManager.IParameterReader;

/**
 * One configurable slot on a statement, such as the item a filter trigger matches.
 *
 * <p>Both serialisation methods gained a {@link HolderLookup.Provider}. Writing an {@link ItemStack} needs registry
 * access on 26.x because a stack's data components are registry-backed, and the 1.20.1 copy takes the same
 * argument -- ignoring it -- so that the two platforms keep one signature and implementations copy between them.
 *
 * <p>Note statements are stored as plain {@link CompoundTag}, not through {@code ValueInput}/{@code ValueOutput}.
 * That pair is only for block entity serialisation; statements live inside a gate's own data.
 */
public interface IStatementParameter extends IGuiSlot {

    /**
     * @return An {@link ItemStack} to render for this parameter, or {@link ItemStack#EMPTY} if this should not
     *         render one.
     */
    @NotNull
    ItemStack getItemStack();

    default DrawType getDrawType() {
        return DrawType.SPRITE_STACK;
    }

    /**
     * Return a non-null value to be set as the statement parameter if you handled the mouse click and do not want
     * all possible values to be shown, or null if you did nothing and wish to show all possible values.
     *
     * @see #getPossible(IStatementContainer)
     */
    @Nullable
    IStatementParameter onClick(
        IStatementContainer source,
        IStatement stmt,
        ItemStack stack,
        StatementMouseClick mouse
    );

    void writeToNbt(CompoundTag nbt, HolderLookup.Provider registries);

    /**
     * Writes this parameter to the given buffer. The default implementation writes out the value of
     * {@link #writeToNbt}, which is passed back into {@link IParameterReader#readFromNbt}.
     *
     * <p>It is likely that implementors can write a more compact form of themselves, so they are encouraged to
     * override this and also register an {@link IParamReaderBuf} through
     * {@link StatementManager#registerParameter(String, IParamReaderBuf)} or
     * {@link StatementManager#registerParameter(IParameterReader, IParamReaderBuf)}.
     */
    default void writeToBuf(FriendlyByteBuf buffer, HolderLookup.Provider registries) {
        CompoundTag nbt = new CompoundTag();
        writeToNbt(nbt, registries);
        buffer.writeNbt(nbt);
    }

    /** @return This parameter after a left rotation. Used in particular in blueprint orientation. */
    IStatementParameter rotateLeft();

    /**
     * @return All the possible alternative parameters for this state. The array may contain null entries to space
     *         out the values.
     */
    IStatementParameter[] getPossible(IStatementContainer source);

    /**
     * @return True if the possible array is laid out for a direction, false if not. This affects the selection
     *         hover layout: if this returns false then {@link #getPossible(IStatementContainer)} is offset up by
     *         one, null is added at 0, and all other nulls are removed.
     */
    default boolean isPossibleOrdered() {
        return false;
    }

    enum DrawType {
        /** Draws the sprite, as returned by {@link IStatementParameter#getSprite()}. */
        SPRITE_ONLY,

        /** Draws the {@link ItemStack}, as returned by {@link IStatementParameter#getItemStack()}. */
        STACK_ONLY,

        /**
         * Draws the {@link ItemStack}, as returned by {@link IStatementParameter#getItemStack()}, except when it
         * is empty, in which case a question mark is drawn.
         */
        STACK_ONLY_OR_QUESTION_MARK,

        /** Draws {@link #SPRITE_ONLY}, then also {@link #STACK_ONLY}. */
        SPRITE_STACK,

        /** Draws {@link #SPRITE_ONLY}, then also {@link #STACK_ONLY_OR_QUESTION_MARK}. */
        SPRITE_STACK_OR_QUESTION_MARK,

        /** Draws {@link #STACK_ONLY}, then also {@link #SPRITE_ONLY}. */
        STACK_SPRITE,

        /** Draws {@link #STACK_ONLY_OR_QUESTION_MARK}, then also {@link #SPRITE_ONLY}. */
        STACK_OR_QUESTION_MARK_THEN_SPRITE
    }
}
