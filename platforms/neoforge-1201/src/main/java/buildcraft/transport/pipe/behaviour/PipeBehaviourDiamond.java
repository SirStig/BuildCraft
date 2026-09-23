/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * Copyright (c) 2026 Joshua Kac -- NeoForge port (BuildCraft 10)
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL
 * was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.transport.pipe.behaviour;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.BlockHitResult;

import buildcraft.api.core.EnumPipePart;
import buildcraft.api.transport.pipe.IPipe;
import buildcraft.api.transport.pipe.PipeBehaviour;

import buildcraft.lib.tile.item.ItemHandlerSimple;

/**
 * Base of the two diamond (sorting/filtering) pipes -- item and fluid. See the 26.x copy of this class for the
 * full account of the filter-slot layout and GUI-opening design; this copy keeps 1.12.2's own shape almost
 * unchanged, since {@link ItemHandlerSimple} here still implements {@code INBTSerializable<CompoundTag>}
 * directly (unlike 26.x's rewrite against NeoForge's {@code StacksResourceHandler}) -- {@link #writeToNbt}/the
 * NBT constructor are a straight {@code serializeNBT}/{@code deserializeNBT} round-trip, no
 * {@code TagValueOutput}/{@code TagValueInput} bridge needed.
 *
 * <p>Scope cut: the "too many filters" advancement is not ported -- see the 26.x copy's own javadoc for why.
 */
public abstract class PipeBehaviourDiamond extends PipeBehaviour {

    public static final int FILTERS_PER_SIDE = 9;

    public final ItemHandlerSimple filters = new ItemHandlerSimple(FILTERS_PER_SIDE * 6);

    public PipeBehaviourDiamond(IPipe pipe) {
        super(pipe);
    }

    public PipeBehaviourDiamond(IPipe pipe, CompoundTag nbt, HolderLookup.Provider registries) {
        super(pipe, nbt, registries);
        filters.deserializeNBT(nbt.getCompound("filters"));
    }

    @Override
    public CompoundTag writeToNbt(HolderLookup.Provider registries) {
        CompoundTag nbt = super.writeToNbt(registries);
        nbt.put("filters", filters.serializeNBT());
        return nbt;
    }

    @Override
    public int getTextureIndex(@Nullable Direction face) {
        return face == null ? 0 : face.ordinal() + 1;
    }

    @Override
    public boolean onPipeActivate(Player player, BlockHitResult trace, EnumPipePart part) {
        return true;
    }
}
