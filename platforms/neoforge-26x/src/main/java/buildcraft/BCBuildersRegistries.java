/*
 * Copyright (c) 2026 Joshua Kac -- NeoForge port (BuildCraft 10)
 *
 * This file is part of the BuildCraft 10 port and is distributed under the terms of the MIT License.
 * Please check the contents of the license, which should be located as "LICENSE.PORT" in the BuildCraft
 * source code distribution.
 */
package buildcraft;

import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.material.MapColor;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;

import buildcraft.api.mj.MjCapabilities;
import buildcraft.api.template.TemplateApi;

import buildcraft.lib.registry.BCRegistry;

import buildcraft.builders.block.BlockArchitectTable;
import buildcraft.builders.block.BlockBuilder;
import buildcraft.builders.block.BlockElectronicLibrary;
import buildcraft.builders.block.BlockFiller;
import buildcraft.builders.block.BlockFrame;
import buildcraft.builders.block.BlockQuarry;
import buildcraft.builders.block.BlockReplacer;
import buildcraft.builders.container.ContainerFiller;
import buildcraft.builders.item.ItemBlueprint;
import buildcraft.builders.item.ItemTemplate;
import buildcraft.builders.snapshot.TemplateHandlerDefault;
import buildcraft.builders.snapshot.TemplateRegistry;
import buildcraft.builders.tile.TileArchitectTable;
import buildcraft.builders.tile.TileBuilder;
import buildcraft.builders.tile.TileElectronicLibrary;
import buildcraft.builders.tile.TileFiller;
import buildcraft.builders.tile.TileQuarry;
import buildcraft.builders.tile.TileReplacer;

/**
 * Registrations belonging to the old {@code buildcraftbuilders} module -- the Quarry and its frame block are the
 * first ones ported; the Filler follows in the same batch as this class's own javadoc note. Mirrors
 * {@link BCFactoryRegistries}' structure exactly.
 */
public final class BCBuildersRegistries {

    private BCBuildersRegistries() {}

    private static final BCRegistry REGISTRY = new BCRegistry(BuildCraft.MOD_ID);

    /** Wires the template registry -- see {@code buildcraft.transport.BCTransportRegistries}' identical static-
     * block pattern for {@code PipeApi.stripeRegistry}. Nothing yet reads {@link TemplateApi#templateRegistry}
     * (see {@link buildcraft.builders.snapshot.Template}'s own javadoc for the not-yet-wired build mode), but the
     * registry and its default handler are real and independently usable now. */
    static {
        TemplateApi.templateRegistry = TemplateRegistry.INSTANCE;
        TemplateApi.templateRegistry.addHandler(TemplateHandlerDefault.INSTANCE);
    }

    /** Not obtainable by any recipe -- the quarry places and clears every frame block itself. See
     * {@link BlockFrame}'s own javadoc for the connected-strut rendering this drops. {@code strength(5.0F, 10.0F)}/
     * {@code SoundType.METAL} match every other iron-tier BuildCraft block ({@link #QUARRY}'s own {@code CHUTE}-
     * style defaults); {@code noOcclusion()} keeps 1.12.2's {@code isOpaqueCube() -> false}, and
     * {@code noLootTable()} matches 1.12.2's {@code getDrops() -> emptyList()}. */
    public static final DeferredBlock<BlockFrame> FRAME = REGISTRY.addBlock("frame", BlockFrame::new,
        properties -> properties
            .mapColor(MapColor.METAL)
            .strength(5.0F, 10.0F)
            .sound(SoundType.METAL)
            .noOcclusion()
            .noLootTable());

    /** Default properties match every other iron-tier BuildCraft machine ({@code buildcraft.factory.block.
     * BlockChute}'s own precedent); {@code SoundType.ANVIL} is pushed by {@link BlockQuarry#getSoundType}, matching
     * 1.12.2's override. */
    public static final DeferredBlock<BlockQuarry> QUARRY = REGISTRY.addBlockAndItem("quarry", BlockQuarry::new,
        properties -> properties
            .mapColor(MapColor.METAL)
            .strength(5.0F, 10.0F)
            .sound(SoundType.METAL)
            .requiresCorrectToolForDrops());

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<TileQuarry>> QUARRY_TYPE =
        REGISTRY.addBlockEntity("quarry", TileQuarry::new, QUARRY);

    /** Default properties match {@link #QUARRY}'s own iron-tier precedent; {@code SoundType.METAL} is pushed by
     * {@link BlockFiller#getSoundType}, matching 1.12.2's override. */
    public static final DeferredBlock<BlockFiller> FILLER = REGISTRY.addBlockAndItem("filler", BlockFiller::new,
        properties -> properties
            .mapColor(MapColor.METAL)
            .strength(5.0F, 10.0F)
            .sound(SoundType.METAL)
            .requiresCorrectToolForDrops());

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<TileFiller>> FILLER_TYPE =
        REGISTRY.addBlockEntity("filler", TileFiller::new, FILLER);

    public static final DeferredHolder<MenuType<?>, MenuType<ContainerFiller>> FILLER_MENU =
        REGISTRY.addMenu("filler", ContainerFiller::new);

    /** Blank/filled data carrier for the {@link #ARCHITECT_TABLE}/{@link #BUILDER} pair -- see
     * {@link ItemBlueprint}'s own javadoc for how this replaces 1.12.2's {@code ItemSnapshot}. Stack size 1: each
     * stack carries its own independent captured structure once filled, so merging two filled stacks together
     * would silently discard one of them. */
    public static final DeferredItem<ItemBlueprint> BLUEPRINT =
        REGISTRY.addItem("blueprint", properties -> new ItemBlueprint(properties.stacksTo(1)));

    /** The {@link buildcraft.builders.snapshot.Template} equivalent of {@link #BLUEPRINT} -- see
     * {@link ItemTemplate}'s own javadoc for why nothing yet filters a slot on it. Stack size 1 for the same
     * per-stack-is-independent-data reason as {@link #BLUEPRINT}. */
    public static final DeferredItem<ItemTemplate> TEMPLATE =
        REGISTRY.addItem("template", properties -> new ItemTemplate(properties.stacksTo(1)));

    /** Default properties match {@link #QUARRY}'s own iron-tier precedent. No GUI exists yet for this tile -- see
     * {@link buildcraft.builders.tile.TileArchitectTable}'s own javadoc. */
    public static final DeferredBlock<BlockArchitectTable> ARCHITECT_TABLE =
        REGISTRY.addBlockAndItem("architect_table", BlockArchitectTable::new,
            properties -> properties
                .mapColor(MapColor.METAL)
                .strength(5.0F, 10.0F)
                .sound(SoundType.METAL)
                .requiresCorrectToolForDrops());

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<TileArchitectTable>> ARCHITECT_TABLE_TYPE =
        REGISTRY.addBlockEntity("architect_table", TileArchitectTable::new, ARCHITECT_TABLE);

    /** Default properties match {@link #QUARRY}'s own iron-tier precedent; {@code SoundType.METAL} is pushed by
     * {@link BlockBuilder#getSoundType}, matching 1.12.2's override. */
    public static final DeferredBlock<BlockBuilder> BUILDER = REGISTRY.addBlockAndItem("builder", BlockBuilder::new,
        properties -> properties
            .mapColor(MapColor.METAL)
            .strength(5.0F, 10.0F)
            .sound(SoundType.METAL)
            .requiresCorrectToolForDrops());

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<TileBuilder>> BUILDER_TYPE =
        REGISTRY.addBlockEntity("builder", TileBuilder::new, BUILDER);

    /** Default properties match {@link #QUARRY}'s own iron-tier precedent; {@code SoundType.ANVIL} is pushed by
     * {@link BlockReplacer#getSoundType}, matching 1.12.2's override. See {@link TileReplacer}'s own javadoc for
     * this round's from/to-as-plain-block-item scope cut and why there is no GUI. */
    public static final DeferredBlock<BlockReplacer> REPLACER = REGISTRY.addBlockAndItem("replacer", BlockReplacer::new,
        properties -> properties
            .mapColor(MapColor.METAL)
            .strength(5.0F, 10.0F)
            .sound(SoundType.METAL)
            .requiresCorrectToolForDrops());

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<TileReplacer>> REPLACER_TYPE =
        REGISTRY.addBlockEntity("replacer", TileReplacer::new, REPLACER);

    /** Default properties match {@link #QUARRY}'s own iron-tier precedent. See {@link TileElectronicLibrary}'s own
     * javadoc for this round's master/duplicate-in/duplicate-out scope cut and why there is no GUI. */
    public static final DeferredBlock<BlockElectronicLibrary> ELECTRONIC_LIBRARY =
        REGISTRY.addBlockAndItem("electronic_library", BlockElectronicLibrary::new,
            properties -> properties
                .mapColor(MapColor.METAL)
                .strength(5.0F, 10.0F)
                .sound(SoundType.METAL)
                .requiresCorrectToolForDrops());

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<TileElectronicLibrary>> ELECTRONIC_LIBRARY_TYPE =
        REGISTRY.addBlockEntity("electronic_library", TileElectronicLibrary::new, ELECTRONIC_LIBRARY);

    public static void register(IEventBus modBus) {
        REGISTRY.register(modBus);
        modBus.addListener(BCBuildersRegistries::registerCapabilities);
    }

    /** The quarry and filler both receive MJ on every side, the same unconditional wiring
     * {@code buildcraft.factory.tile.TileMiner}/{@code TileDistiller} already use for their own
     * {@code MjBatteryReceiver}; {@link #BUILDER} joins them here. {@link #ARCHITECT_TABLE}/{@link #BUILDER} also
     * expose their item slots as a generic {@link Capabilities.Item#BLOCK} capability, matching
     * {@code TileEngineStone}'s own {@code itemManager.getHandlerForFace} precedent -- this is how a hopper or
     * pipe reaches their inventories with no GUI in the way. {@link #REPLACER}/{@link #ELECTRONIC_LIBRARY} join
     * them for the same reason (see each tile's own javadoc); neither needs MJ. */
    private static void registerCapabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(MjCapabilities.RECEIVER, QUARRY_TYPE.get(), (tile, side) -> tile.mjReceiver);
        event.registerBlockEntity(MjCapabilities.READABLE, QUARRY_TYPE.get(), (tile, side) -> tile.mjReceiver);
        event.registerBlockEntity(MjCapabilities.RECEIVER, FILLER_TYPE.get(), (tile, side) -> tile.mjReceiver);
        event.registerBlockEntity(MjCapabilities.READABLE, FILLER_TYPE.get(), (tile, side) -> tile.mjReceiver);
        event.registerBlockEntity(Capabilities.Item.BLOCK, ARCHITECT_TABLE_TYPE.get(),
            (tile, side) -> tile.itemManager.getHandlerForFace(side));
        event.registerBlockEntity(Capabilities.Item.BLOCK, BUILDER_TYPE.get(),
            (tile, side) -> tile.itemManager.getHandlerForFace(side));
        event.registerBlockEntity(MjCapabilities.RECEIVER, BUILDER_TYPE.get(), (tile, side) -> tile.mjReceiver);
        event.registerBlockEntity(MjCapabilities.READABLE, BUILDER_TYPE.get(), (tile, side) -> tile.mjReceiver);
        event.registerBlockEntity(Capabilities.Item.BLOCK, REPLACER_TYPE.get(),
            (tile, side) -> tile.itemManager.getHandlerForFace(side));
        event.registerBlockEntity(Capabilities.Item.BLOCK, ELECTRONIC_LIBRARY_TYPE.get(),
            (tile, side) -> tile.itemManager.getHandlerForFace(side));
    }
}
