/*
 * Copyright (c) 2011-2015, SpaceToad and the BuildCraft Team http://www.mod-buildcraft.com
 * Copyright (c) 2026 Joshua Kac -- NeoForge port (BuildCraft 10)
 *
 * The BuildCraft API is distributed under the terms of the MIT License. Please check the contents of the
 * license, which should be located as "LICENSE.API" in the BuildCraft source code distribution.
 */
package buildcraft.api.statements;

import java.util.List;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import buildcraft.api.core.render.ISprite;

/**
 * The default statement parameter: a single item stack, used by filter triggers and the like.
 *
 * <p>This is the 1.20.1 copy, and serialisation is the whole reason it cannot be shared with the 26.x one.
 * 1.20.1 still has the imperative {@code ItemStack.save(CompoundTag)}/{@code ItemStack.of(CompoundTag)} pair and
 * needs no registry access to use them, so the {@link HolderLookup.Provider} the interface passes is accepted and
 * ignored -- keeping the signature identical to 26.x, where it is required.
 *
 * <p>Likewise equality is {@link ItemStack#isSameItemSameTags}, since 1.20.1 predates data components; 26.x uses
 * {@code isSameItemSameComponents}. As in 1.12.2, count is deliberately not compared: a parameter describes a
 * filter, not an amount.
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
        this.stack = nbt.contains("stack") ? ItemStack.of(nbt.getCompound("stack")) : ItemStack.EMPTY;
    }

    @Override
    public void writeToNbt(CompoundTag compound, HolderLookup.Provider registries) {
        if (!stack.isEmpty()) {
            compound.put("stack", stack.save(new CompoundTag()));
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
            return ItemStack.isSameItemSameTags(stack, param.stack);
        }
        return false;
    }

    @Override
    public int hashCode() {
        if (stack.isEmpty()) {
            return 0;
        }
        int result = stack.getItem().hashCode();
        return 31 * result + (stack.getTag() == null ? 0 : stack.getTag().hashCode());
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
        return stack.getTooltipLines(null, TooltipFlag.NORMAL);
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
