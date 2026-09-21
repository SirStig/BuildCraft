/*
 * Copyright (c) 2026 Joshua Kac -- NeoForge port (BuildCraft 10)
 *
 * The BuildCraft API is distributed under the terms of the MIT License. Please check the contents of the
 * license, which should be located as "LICENSE.API" in the BuildCraft source code distribution.
 */
package buildcraft.api.facades;

import org.jetbrains.annotations.Nullable;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import net.neoforged.fml.InterModComms;

/**
 * How another mod tells BuildCraft which of its blocks may be made into facades.
 *
 * <p>Three things changed, and the messages are not wire-compatible with the 1.12.2 ones.
 *
 * <ul>
 * <li>{@code FMLInterModComms.sendMessage} is {@link InterModComms#sendTo}, and the payload is a
 *     {@link java.util.function.Supplier} of any object rather than one of a handful of accepted types. That
 *     removes the reason the old API packed its arguments into an NBT compound with string keys, so the two
 *     messages carry {@link CustomMapping} and a {@link Block} directly. The three {@code NBT_*} key constants
 *     go with it.</li>
 * <li>The target mod id was {@code buildcraftsilicon}. The eight 1.12.2 mod ids collapsed into one during the
 *     port, so it is {@code buildcraft}.</li>
 * <li>{@code mapStateToStack} wrote {@code block.getMetaFromState(state)} alongside the block's registry name,
 *     because a state could not be addressed directly. Metadata is gone; a {@link BlockState} is passed
 *     whole.</li>
 * </ul>
 *
 * <p>Send these from your mod's {@code InterModEnqueueEvent}.
 */
public final class FacadeAPI {

    /** The mod id to send facade messages to. Was {@code buildcraftsilicon} before the modules merged. */
    public static final String IMC_MOD_TARGET = "buildcraft";

    /** Payload: the {@link Block} to refuse to make facades from. */
    public static final String IMC_FACADE_DISABLE = "facade_disable_block";

    /** Payload: a {@link CustomMapping}. */
    public static final String IMC_FACADE_CUSTOM = "facade_custom_map_block_item";

    @Nullable
    public static IFacadeItem facadeItem;

    @Nullable
    public static IFacadeRegistry registry;

    private FacadeAPI() {
    }

    /**
     * Says that a facade of {@code state} should cost {@code stack} rather than whatever BuildCraft would work
     * out for itself.
     */
    public record CustomMapping(BlockState state, ItemStack stack) {
    }

    /** Stops BuildCraft offering facades of the given block. */
    public static void disableBlock(Block block) {
        InterModComms.sendTo(IMC_MOD_TARGET, IMC_FACADE_DISABLE, () -> block);
    }

    /** Overrides the item a facade of the given state is crafted from. */
    public static void mapStateToStack(BlockState state, ItemStack stack) {
        InterModComms.sendTo(IMC_MOD_TARGET, IMC_FACADE_CUSTOM, () -> new CustomMapping(state, stack));
    }

    public static boolean isFacadeMessageId(String id) {
        return IMC_FACADE_CUSTOM.equals(id) || IMC_FACADE_DISABLE.equals(id);
    }
}
