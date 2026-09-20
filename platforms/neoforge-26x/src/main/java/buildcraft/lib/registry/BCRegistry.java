/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.lib.registry;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Registration helper for one BuildCraft module.
 *
 * <p>This replaces the 1.12.2 trio of {@code RegistrationHelper}, {@code TagManager} and
 * {@code RegistryConfig}. Most of what they did no longer needs doing:
 *
 * <ul>
 * <li>{@code TagManager} kept a central map of id -> registry name, unlocalised name, model location and ore
 *     dictionary name. {@link DeferredRegister} carries the registry name itself, the lang JSON carries the display
 *     name, the model JSON carries the model, and ore dictionary entries are now tag JSON. The whole indirection
 *     goes away.</li>
 * <li>{@code RegistrationHelper} subscribed to {@code RegistryEvent.Register} per registry type and pushed objects
 *     into it. {@link DeferredRegister} does that.</li>
 * <li>{@code RegistryConfig} skipped registration entirely for anything disabled in the config. That is no longer
 *     safe: registries are synced to the client, so a server and client disagreeing produces a connection error
 *     rather than a missing block. Registration is now unconditional, and disabling is done further up, by leaving
 *     an entry out of the creative tab and gating its recipe.</li>
 * </ul>
 *
 * <p>Ordering is preserved: entries appear in the creative tab in the order they were added, which is how the
 * 1.12.2 tabs were built.
 */
public final class BCRegistry {

    private final DeferredRegister.Blocks blocks;
    private final DeferredRegister.Items items;
    private final DeferredRegister<BlockEntityType<?>> blockEntities;

    /** Everything that should show up in BuildCraft's creative tab, in registration order. */
    private final List<Supplier<? extends ItemLike>> creativeOrder = new ArrayList<>();

    public BCRegistry(String modId) {
        this.blocks = DeferredRegister.createBlocks(modId);
        this.items = DeferredRegister.createItems(modId);
        this.blockEntities = DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, modId);
    }

    // ###############
    //
    // Items
    //
    // ###############

    /** Registers a plain item with no behaviour, such as a gear. */
    public DeferredItem<Item> addItem(String name) {
        return remember(items.registerSimpleItem(name));
    }

    /** Registers an item built from the given factory, for anything that needs its own class. */
    public <I extends Item> DeferredItem<I> addItem(String name, Function<Item.Properties, ? extends I> factory) {
        return remember(items.registerItem(name, factory));
    }

    // ###############
    //
    // Blocks
    //
    // ###############

    /** Registers a block and its {@code BlockItem} together -- the 1.12.2 {@code addBlockAndItem}. */
    public <B extends Block> DeferredBlock<B> addBlockAndItem(
        String name,
        Function<BlockBehaviour.Properties, ? extends B> factory,
        UnaryOperator<BlockBehaviour.Properties> properties
    ) {
        DeferredBlock<B> block = blocks.registerBlock(name, factory, properties);
        items.registerSimpleBlockItem(block);
        creativeOrder.add(block);
        return block;
    }

    /** As {@link #addBlockAndItem}, with default block properties. */
    public <B extends Block> DeferredBlock<B> addBlockAndItem(
        String name,
        Function<BlockBehaviour.Properties, ? extends B> factory
    ) {
        return addBlockAndItem(name, factory, UnaryOperator.identity());
    }

    // ###############
    //
    // Block entities
    //
    // ###############

    /**
     * Registers a block entity type bound to the given blocks.
     *
     * <p>Note this is 26.x-specific: {@code BlockEntityType.Builder} was removed, so the type is constructed
     * directly. On 1.20.1 the same call has to go through {@code BlockEntityType.Builder.of(...).build(null)}.
     *
     * <p>The valid blocks are passed as suppliers because block entity types are registered in the same pass as the
     * blocks they belong to, so the blocks are not resolved yet at the point this is called.
     */
    @SafeVarargs
    public final <T extends BlockEntity> DeferredHolder<BlockEntityType<?>, BlockEntityType<T>> addBlockEntity(
        String name,
        BlockEntityType.BlockEntitySupplier<T> factory,
        Supplier<? extends Block>... validBlocks
    ) {
        return blockEntities.register(name, () -> {
            Block[] resolved = new Block[validBlocks.length];
            for (int i = 0; i < validBlocks.length; i++) {
                resolved[i] = validBlocks[i].get();
            }
            return new BlockEntityType<>(factory, resolved);
        });
    }

    // ###############
    //
    // Hook-up
    //
    // ###############

    public void register(IEventBus modBus) {
        blocks.register(modBus);
        items.register(modBus);
        blockEntities.register(modBus);
    }

    /** Everything this module contributes to the creative tab, in registration order. */
    public List<ItemLike> creativeTabEntries() {
        List<ItemLike> entries = new ArrayList<>(creativeOrder.size());
        for (Supplier<? extends ItemLike> supplier : creativeOrder) {
            entries.add(supplier.get());
        }
        return entries;
    }

    private <I extends Item> DeferredItem<I> remember(DeferredItem<I> item) {
        creativeOrder.add(item);
        return item;
    }
}
