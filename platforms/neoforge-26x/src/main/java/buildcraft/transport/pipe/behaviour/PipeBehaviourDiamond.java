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
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.phys.BlockHitResult;

import buildcraft.api.core.EnumPipePart;
import buildcraft.api.transport.pipe.IPipe;
import buildcraft.api.transport.pipe.PipeBehaviour;

import buildcraft.lib.tile.item.ItemHandlerSimple;

/**
 * Base of the two diamond (sorting/filtering) pipes -- item and fluid. A close port of 1.12.2's own
 * {@code PipeBehaviourDiamond}: 54 filter slots ({@link #FILTERS_PER_SIDE} x 6 faces), and a right-click on the
 * pipe (any item, no wrench check -- 1.12.2 has none here either) opens the filter-configuration GUI.
 *
 * <p><b>GUI opening, this port's own shape.</b> 1.12.2 reached the GUI through
 * {@code BCTransportGuis.PIPE_DIAMOND.openGui(...)}, a whole separate GUI-id registry this port does not have.
 * Here, {@link #onPipeActivate} just answers "yes, I have a menu" (unconditionally, matching the original's own
 * lack of any gating); {@code TilePipeHolder#createMenu} (a new {@code MenuProvider} implementation) does the
 * actual {@code instanceof PipeBehaviourDiamond}/{@code PipeBehaviourWoodDiamond} dispatch, and
 * {@code BlockPipeHolder#useWithoutItem} is the new entry point that calls {@link #onPipeActivate} and then
 * {@code Player#openMenu} on a true/server-side result -- see that block's own javadoc for why a wrench click
 * never reaches this method at all for a directional material ({@link PipeBehaviourWoodDiamond}), and does for
 * this plain (non-directional) class, exactly matching 1.12.2's own unconditional GUI-open here.
 *
 * <p><b>Filter persistence bridges {@link PipeBehaviour}'s raw {@code CompoundTag} contract to
 * {@link ItemHandlerSimple}'s modern {@code ValueInput}/{@code ValueOutput}-based
 * {@code serialize}/{@code deserialize}</b> (inherited from NeoForge's own {@code StacksResourceHandler} -- see
 * that class's own javadoc) via {@link TagValueOutput}/{@link TagValueInput}, the same bridge
 * {@code net.minecraft.world.level.storage} itself provides for exactly this "I have a raw tag, not a
 * {@code ValueOutput}" situation. {@link ProblemReporter#DISCARDING} is used throughout, matching this class's own
 * indifference to malformed-but-recoverable filter data (a corrupt slot is simply dropped, never worth crashing
 * a whole pipe load over).
 *
 * <p><b>Scope cut: the "too many filters" advancement is not ported.</b> 1.12.2 fires
 * {@code too_many_pipe_filters} once seven or more of a side's nine filter slots are full. No such advancement is
 * defined anywhere in this port's data pack, and it has zero effect on filtering/routing itself, so it is left
 * out rather than wiring a dead {@code AdvancementUtil} call against an advancement id nothing will ever load.
 */
public abstract class PipeBehaviourDiamond extends PipeBehaviour {

    public static final int FILTERS_PER_SIDE = 9;

    public final ItemHandlerSimple filters = new ItemHandlerSimple(FILTERS_PER_SIDE * 6);

    public PipeBehaviourDiamond(IPipe pipe) {
        super(pipe);
    }

    public PipeBehaviourDiamond(IPipe pipe, CompoundTag nbt, HolderLookup.Provider registries) {
        super(pipe, nbt, registries);
        ValueInput input = TagValueInput.create(ProblemReporter.DISCARDING, registries, nbt.getCompoundOrEmpty("filters"));
        filters.deserialize(input);
    }

    @Override
    public CompoundTag writeToNbt(HolderLookup.Provider registries) {
        CompoundTag nbt = super.writeToNbt(registries);
        TagValueOutput output = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, registries);
        filters.serialize(output);
        nbt.put("filters", output.buildResult());
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
