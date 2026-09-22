/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * Copyright (c) 2026 Joshua Kac -- NeoForge port (BuildCraft 10)
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL
 * was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.transport.item;

import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;

import buildcraft.api.transport.pipe.IItemPipe;
import buildcraft.api.transport.pipe.PipeApi;
import buildcraft.api.transport.pipe.PipeDefinition;

/**
 * The item a player actually holds and places -- one instance per {@link PipeDefinition}, exactly one in this
 * batch (the cobblestone pipe's own item). Placing it stamps its {@link PipeDefinition} onto the shared
 * {@code TilePipeHolder}/{@code Pipe} the block creates -- see {@code TilePipeHolder#onPlacedBy}.
 *
 * <p>A close port of 1.12.2's own {@code ItemPipeHolder} ({@code extends ItemBlock}, here {@link BlockItem}),
 * adapted the way every other item in this port has needed: a modern {@link Item.Properties}-driven constructor,
 * no metadata (1.12.2 packed the pipe's dye colour into item metadata 1-16, plus 0 for colourless; colouring is
 * out of this batch's scope entirely -- see {@code BCTransportRegistries}' own javadoc for why
 * {@code canBeColoured} is {@code false} on the cobblestone {@link PipeDefinition}, so there is no metadata
 * variant to reproduce here at all). {@code getSubItems}/{@code addModelVariants} (17-metadata iteration),
 * {@code getItemStackDisplayName}/{@code getFontRenderer} (the coloured-name/special-font handling for the same
 * metadata variants) and the fluid/power-flow tooltip branch in {@code addInformation} are all dropped with it --
 * every one of those existed only to serve the colour-metadata system or a flow type (fluid/power) this batch
 * does not register.
 *
 * <p>Self-registers with {@link PipeApi#pipeRegistry} from its own constructor -- see
 * {@code buildcraft.transport.pipe.PipeRegistry}'s own javadoc for why this replaces 1.12.2's
 * {@code registerWithPipeApi()} call site (this batch has no {@code RegistrationHelper}-driven "explicit register
 * after construction" step; {@code BCTransportRegistries}' {@code DeferredRegister.Items} factory constructs this
 * class directly, so registering here is the natural place).
 */
public class ItemPipeHolder extends BlockItem implements IItemPipe {
    public final PipeDefinition definition;

    public ItemPipeHolder(Block pipeHolderBlock, Properties properties, PipeDefinition definition) {
        super(pipeHolderBlock, properties);
        this.definition = definition;
        PipeApi.pipeRegistry.setItemForPipe(definition, this);
    }

    @Override
    public PipeDefinition getDefinition() {
        return definition;
    }
}
