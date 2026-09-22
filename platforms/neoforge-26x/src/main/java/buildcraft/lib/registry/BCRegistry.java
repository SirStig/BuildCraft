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
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.network.IContainerFactory;
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

    /** Every {@link BCRegistry} any module has constructed, in construction order. Each BuildCraft module
     * (core, factory, ...) owns its own private {@code BCRegistry} instance rather than sharing one, so a
     * single module's {@link #creativeTabEntries()} only ever sees that module's own contributions -- this is
     * what lets {@link #allCreativeTabEntries()} present all of them together in BuildCraft's one creative tab
     * without {@code BCCoreRegistries} (where that tab is built) needing a compile-time reference to every
     * other module's registration holder. Safe precisely because {@code CreativeModeTab}'s {@code displayItems}
     * callback is only ever invoked lazily, well after every module's {@code register(modBus)} has already run
     * during mod construction -- by the time anything asks, every module's {@link BCRegistry} already exists. */
    private static final List<BCRegistry> ALL = new ArrayList<>();

    private final DeferredRegister.Blocks blocks;
    private final DeferredRegister.Items items;
    private final DeferredRegister<BlockEntityType<?>> blockEntities;
    private final DeferredRegister<MenuType<?>> menus;

    /** Everything that should show up in BuildCraft's creative tab, in registration order. */
    private final List<Supplier<? extends ItemLike>> creativeOrder = new ArrayList<>();

    public BCRegistry(String modId) {
        this.blocks = DeferredRegister.createBlocks(modId);
        this.items = DeferredRegister.createItems(modId);
        this.blockEntities = DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, modId);
        this.menus = DeferredRegister.create(Registries.MENU, modId);
        ALL.add(this);
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

    /** Registers a block with no {@code BlockItem} at all -- the 1.12.2 {@code addBlock} half of
     * {@code RegistrationHelper} that {@code addBlockAndItem} always paired with an item. First needed by
     * {@code buildcraft.factory.block.BlockTube}: a purely cosmetic shaft segment a player is never meant to
     * obtain directly, so registering an item for it would just be dead weight (and a spurious creative-tab/
     * recipe-book entry for something {@code noLootTable()} already guarantees never drops). Not added to the
     * creative tab, for the same reason. */
    public <B extends Block> DeferredBlock<B> addBlock(
        String name,
        Function<BlockBehaviour.Properties, ? extends B> factory,
        UnaryOperator<BlockBehaviour.Properties> properties
    ) {
        return blocks.registerBlock(name, factory, properties);
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
    // Menus
    //
    // ###############

    /** Registers a {@link MenuType} bound to the given tile-menu factory -- first needed by
     * {@code buildcraft.factory.container.ContainerAutoCraftItems}, this port's first real GUI/container. Wraps
     * NeoForge's own {@link IMenuTypeExtension#create}, which threads the extra
     * {@code RegistryFriendlyByteBuf} client-side lookup data through automatically (see
     * {@code buildcraft.factory.tile.TileAutoWorkbenchBase#writeClientSideData} for the server-side half of that
     * hand-off). Client-side screen registration is a separate call entirely -- see
     * {@code buildcraft.factory.client.BCFactoryClientRegistries}'s own javadoc for why that one is never routed
     * through this class. */
    public <M extends AbstractContainerMenu> DeferredHolder<MenuType<?>, MenuType<M>> addMenu(
        String name,
        IContainerFactory<M> factory
    ) {
        return menus.register(name, () -> IMenuTypeExtension.create(factory));
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
        menus.register(modBus);
    }

    /** Everything this module contributes to the creative tab, in registration order. */
    public List<ItemLike> creativeTabEntries() {
        List<ItemLike> entries = new ArrayList<>(creativeOrder.size());
        for (Supplier<? extends ItemLike> supplier : creativeOrder) {
            entries.add(supplier.get());
        }
        return entries;
    }

    /** Every module's creative-tab contributions combined, in the order their {@link BCRegistry} instances were
     * constructed (module registration order -- see {@link #ALL}'s own javadoc). {@code BCCoreRegistries#TAB_MAIN}
     * calls this instead of its own {@code REGISTRY.creativeTabEntries()} so that every module lands in
     * BuildCraft's single creative tab, not just core's. */
    public static List<ItemLike> allCreativeTabEntries() {
        List<ItemLike> entries = new ArrayList<>();
        for (BCRegistry registry : ALL) {
            entries.addAll(registry.creativeTabEntries());
        }
        return entries;
    }

    private <I extends Item> DeferredItem<I> remember(DeferredItem<I> item) {
        creativeOrder.add(item);
        return item;
    }
}
