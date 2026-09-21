/*
 * Copyright (c) 2011-2015, SpaceToad and the BuildCraft Team http://www.mod-buildcraft.com
 * Copyright (c) 2026 Joshua Kac -- NeoForge port (BuildCraft 10)
 *
 * The BuildCraft API is distributed under the terms of the MIT License. Please check the contents of the
 * license, which should be located as "LICENSE.API" in the BuildCraft source code distribution.
 */
package buildcraft.api.statements;

import java.util.List;
import java.util.Objects;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import buildcraft.api.core.render.ISprite;

/**
 * The default statement parameter: a single item stack, used by filter triggers and the like.
 *
 * <p>Serialisation is where this differs most from 1.12.2. {@code ItemStack.writeToNBT}/{@code new ItemStack(tag)}
 * are gone, and 26.x has no imperative replacement at all -- not even the {@code save}/{@code parse} pair that
 * 1.20.5 introduced. A stack is read and written only through {@link ItemStack#CODEC}, run against ops carrying
 * registry access, because its data components are registry-backed. A stack that no longer exists reads back as
 * empty rather than throwing, which is the behaviour this wants anyway: a filter referring to an uninstalled
 * mod's item should become "no filter", not break the gate holding it.
 *
 * <p>Equality no longer needs the {@code areItemStacksEqual} plus {@code areItemStackTagsEqual} pair --
 * {@link ItemStack#isSameItemSameComponents} covers both, since components replaced the tag compound. Note the
 * 1.12.2 version compared item and NBT but <em>not</em> count, and that is kept: a parameter describes a filter,
 * not an amount.
 *
 * <p>{@code getTooltip} returns {@link Component}s now rather than pre-formatted strings, so the rarity colour is
 * applied through a style rather than by prefixing a formatting code.
 */
public class StatementParameterItemStack implements IStatementParameter {

    /** Immutable parameter holding {@link ItemStack#EMPTY}. */
    public static final StatementParameterItemStack EMPTY = new StatementParameterItemStack();

    @NotNull
    protected final ItemStack stack;

    public StatementParameterItemStack() {
        this.stack = ItemStack.EMPTY;
    }

    public StatementParameterItemStack(@NotNull ItemStack stack) {
        this.stack = stack;
    }

    public StatementParameterItemStack(CompoundTag nbt, HolderLookup.Provider registries) {
        Tag stackTag = nbt.get("stack");
        if (stackTag == null) {
            this.stack = ItemStack.EMPTY;
        } else {
            this.stack = ItemStack.CODEC
                .parse(registries.createSerializationContext(NbtOps.INSTANCE), stackTag)
                .result()
                .orElse(ItemStack.EMPTY);
        }
    }

    @Override
    public void writeToNbt(CompoundTag compound, HolderLookup.Provider registries) {
        if (!stack.isEmpty()) {
            ItemStack.CODEC
                .encodeStart(registries.createSerializationContext(NbtOps.INSTANCE), stack)
                .result()
                .ifPresent(tag -> compound.put("stack", tag));
        }
    }

    @Override
    @Nullable
    public ISprite getSprite() {
        return null;
    }

    @Override
    @NotNull
    public ItemStack getItemStack() {
        return stack;
    }

    @Override
    public StatementParameterItemStack onClick(
        IStatementContainer source,
        IStatement stmt,
        ItemStack stack,
        StatementMouseClick mouse
    ) {
        if (stack.isEmpty()) {
            return EMPTY;
        }
        return new StatementParameterItemStack(stack.copyWithCount(1));
    }

    @Override
    public boolean equals(Object object) {
        if (object instanceof StatementParameterItemStack param) {
            return ItemStack.isSameItemSameComponents(stack, param.stack);
        }
        return false;
    }

    @Override
    public int hashCode() {
        return stack.isEmpty() ? 0 : ItemStack.hashItemAndComponents(stack);
    }

    @Override
    public String getUniqueTag() {
        return "buildcraft:stack";
    }

    @Override
    public Component getDescription() {
        return stack.isEmpty() ? Component.empty() : stack.getHoverName();
    }

    @Override
    public List<Component> getTooltip() {
        if (stack.isEmpty()) {
            return List.of();
        }
        return stack.getTooltipLines(Item.TooltipContext.EMPTY, null, TooltipFlag.NORMAL);
    }

    @Override
    public IStatementParameter rotateLeft() {
        return this;
    }

    @Override
    public IStatementParameter[] getPossible(IStatementContainer source) {
        return new IStatementParameter[0];
    }

    @Override
    public String toString() {
        return "StatementParameterItemStack[" + stack + "]";
    }
}
