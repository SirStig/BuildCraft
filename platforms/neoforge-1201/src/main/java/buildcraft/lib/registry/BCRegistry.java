/*
 * Copyright (c) 2026 Joshua Kac -- NeoForge port (BuildCraft 10)
 *
 * This file is part of the BuildCraft 10 port and is distributed under the terms of the MIT License.
 * Please check the contents of the license, which should be located as "LICENSE.PORT" in the BuildCraft
 * source code distribution.
 */
package buildcraft.lib.registry;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;

import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/**
 * Registration helper for one BuildCraft module, for Minecraft 1.20.1.
 *
 * <p>Mirrors the 26.x class of the same name -- see that one for why the 1.12.2
 * {@code RegistrationHelper}/{@code TagManager}/{@code RegistryConfig} trio mostly disappears. The two cannot be
 * shared because the registry API itself differs: 1.20.1 has no {@code DeferredRegister.Blocks}/{@code .Items}
 * subclasses and no {@code DeferredBlock}/{@code DeferredItem}, so block items are registered by hand and
 * everything comes back as a plain {@link RegistryObject}.
 */
public final class BCRegistry {

    private final DeferredRegister<Block> blocks;
    private final DeferredRegister<Item> items;
    private final DeferredRegister<BlockEntityType<?>> blockEntities;

    /** Everything that should show up in BuildCraft's creative tab, in registration order. */
    private final List<Supplier<? extends ItemLike>> creativeOrder = new ArrayList<>();

    public BCRegistry(String modId) {
        this.blocks = DeferredRegister.create(ForgeRegistries.BLOCKS, modId);
        this.items = DeferredRegister.create(ForgeRegistries.ITEMS, modId);
        this.blockEntities = DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, modId);
    }

    // ###############
    //
    // Items
    //
    // ###############

    /** Registers a plain item with no behaviour, such as a gear. */
    public RegistryObject<Item> addItem(String name) {
        return addItem(name, () -> new Item(new Item.Properties()));
    }

    /** Registers an item built from the given supplier, for anything that needs its own class. */
    public <I extends Item> RegistryObject<I> addItem(String name, Supplier<? extends I> factory) {
        RegistryObject<I> item = items.register(name, factory);
        creativeOrder.add(item);
        return item;
    }

    // ###############
    //
    // Blocks
    //
    // ###############

    /** Registers a block and its {@code BlockItem} together -- the 1.12.2 {@code addBlockAndItem}. */
    public <B extends Block> RegistryObject<B> addBlockAndItem(String name, Supplier<? extends B> factory) {
        RegistryObject<B> block = blocks.register(name, factory);
        items.register(name, () -> new BlockItem(block.get(), new Item.Properties()));
        creativeOrder.add(block);
        return block;
    }

    /** Registers a block with no {@code BlockItem} at all -- see the 26.x copy of this class for why
     * {@code buildcraft.factory.block.BlockTube} needs this rather than {@link #addBlockAndItem}. Not added to
     * the creative tab, for the same reason. */
    public <B extends Block> RegistryObject<B> addBlock(String name, Supplier<? extends B> factory) {
        return blocks.register(name, factory);
    }

    // ###############
    //
    // Block entities
    //
    // ###############

    /**
     * Registers a block entity type bound to the given blocks.
     *
     * <p>1.20.1-specific: the type is built through {@code BlockEntityType.Builder}, which 26.x removed in favour of
     * constructing {@code BlockEntityType} directly. The {@code null} passed to {@code build} is the data fixer type,
     * which mods do not supply.
     */
    @SafeVarargs
    public final <T extends BlockEntity> RegistryObject<BlockEntityType<T>> addBlockEntity(
        String name,
        BlockEntityType.BlockEntitySupplier<T> factory,
        Supplier<? extends Block>... validBlocks
    ) {
        return blockEntities.register(name, () -> {
            Block[] resolved = new Block[validBlocks.length];
            for (int i = 0; i < validBlocks.length; i++) {
                resolved[i] = validBlocks[i].get();
            }
            return BlockEntityType.Builder.of(factory, resolved).build(null);
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
}
