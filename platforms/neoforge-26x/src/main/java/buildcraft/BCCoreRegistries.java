/*
 * Copyright (c) 2026 Joshua Kac -- NeoForge port (BuildCraft 10)
 *
 * This file is part of the BuildCraft 10 port and is distributed under the terms of the MIT License.
 * Please check the contents of the license, which should be located as "LICENSE.PORT" in the BuildCraft
 * source code distribution.
 */
package buildcraft;

import java.util.function.UnaryOperator;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

import buildcraft.api.enums.EnumDecoratedBlock;
import buildcraft.api.mj.MjCapabilities;
import buildcraft.api.tiles.TilesAPI;
import buildcraft.core.block.BlockDecoration;
import buildcraft.core.block.BlockEngineCreative;
import buildcraft.core.block.BlockEngineWood;
import buildcraft.core.block.BlockMarkerPath;
import buildcraft.core.block.BlockMarkerVolume;
import buildcraft.core.block.BlockPowerConsumerTester;
import buildcraft.core.block.BlockSpringWater;
import buildcraft.core.item.ItemGoggles;
import buildcraft.core.item.ItemMarkerConnector;
import buildcraft.core.item.ItemWrench;
import buildcraft.core.marker.PathCache;
import buildcraft.core.marker.VolumeCache;
import buildcraft.core.tile.TileEngineCreative;
import buildcraft.core.tile.TileEngineWood;
import buildcraft.core.tile.TileMarkerPath;
import buildcraft.core.tile.TileMarkerVolume;
import buildcraft.core.tile.TilePowerConsumerTester;
import buildcraft.lib.marker.MarkerCache;
import buildcraft.lib.registry.BCRegistry;

/**
 * Registrations belonging to the old {@code buildcraftcore} module.
 *
 * <p>Ported from {@code buildcraft.core.BCCoreItems} / {@code BCCoreBlocks}, which registered through BuildCraft's
 * own {@code RegistrationHelper} during FML preInit. That is now {@link BCRegistry}, over {@link DeferredRegister}.
 */
public final class BCCoreRegistries {

    private BCCoreRegistries() {}

    private static final BCRegistry REGISTRY = new BCRegistry(BuildCraft.MOD_ID);

    public static final DeferredRegister<CreativeModeTab> CREATIVE_TABS =
        DeferredRegister.create(Registries.CREATIVE_MODE_TAB, BuildCraft.MOD_ID);

    // --- Gears ------------------------------------------------------------------
    // Plain crafting components with no behaviour, so they port across as simple items.
    public static final DeferredItem<Item> GEAR_WOOD = REGISTRY.addItem("gear_wood");
    public static final DeferredItem<Item> GEAR_STONE = REGISTRY.addItem("gear_stone");
    public static final DeferredItem<Item> GEAR_IRON = REGISTRY.addItem("gear_iron");
    public static final DeferredItem<Item> GEAR_GOLD = REGISTRY.addItem("gear_gold");
    public static final DeferredItem<Item> GEAR_DIAMOND = REGISTRY.addItem("gear_diamond");

    // --- Tools --------------------------------------------------------------------
    public static final DeferredItem<ItemWrench> WRENCH = REGISTRY.addItem("wrench", ItemWrench::new);
    public static final DeferredItem<ItemMarkerConnector> MARKER_CONNECTOR =
        REGISTRY.addItem("marker_connector", ItemMarkerConnector::new);
    public static final DeferredItem<ItemGoggles> GOGGLES = REGISTRY.addItem("goggles", ItemGoggles::new);

    // --- Markers ------------------------------------------------------------------
    private static final UnaryOperator<BlockBehaviour.Properties> MARKER_PROPERTIES = properties -> properties
        .mapColor(MapColor.NONE)
        .noCollision()
        .noOcclusion()
        .strength(0.25F)
        .sound(SoundType.WOOD);

    public static final DeferredBlock<BlockMarkerVolume> MARKER_VOLUME =
        REGISTRY.addBlockAndItem("marker_volume", BlockMarkerVolume::new, MARKER_PROPERTIES);

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<TileMarkerVolume>> MARKER_VOLUME_TYPE =
        REGISTRY.addBlockEntity("marker_volume", TileMarkerVolume::new, MARKER_VOLUME);

    public static final DeferredBlock<BlockMarkerPath> MARKER_PATH =
        REGISTRY.addBlockAndItem("marker_path", BlockMarkerPath::new, MARKER_PROPERTIES);

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<TileMarkerPath>> MARKER_PATH_TYPE =
        REGISTRY.addBlockEntity("marker_path", TileMarkerPath::new, MARKER_PATH);

    // --- Machines ---------------------------------------------------------------
    public static final DeferredBlock<BlockPowerConsumerTester> POWER_TESTER =
        REGISTRY.addBlockAndItem("power_tester", BlockPowerConsumerTester::new,
            properties -> properties
                .mapColor(MapColor.METAL)
                .strength(5.0F, 6.0F)
                .sound(SoundType.METAL)
                .requiresCorrectToolForDrops());

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<TilePowerConsumerTester>> POWER_TESTER_TYPE =
        REGISTRY.addBlockEntity("power_tester", TilePowerConsumerTester::new, POWER_TESTER);

    /** Renamed from 1.12.2's {@code WOOD} variant of the shared, multi-variant {@code BlockEngine_BC8}/
     * {@code TileEngineRedstone_BC8} pair -- see {@code TileEngineWood}'s own javadoc. Properties match what
     * {@code BlockBCTile_Neptune}'s constructor gave every 1.12.2 BuildCraft block by default (hardness 5,
     * resistance 10, {@code SoundType.METAL}), the same reasoning already worked out for {@code BlockDecoration}
     * below -- {@code BlockEngineBase_BC8} never overrode any of them either. */
    public static final DeferredBlock<BlockEngineWood> ENGINE_WOOD =
        REGISTRY.addBlockAndItem("engine_wood", BlockEngineWood::new,
            properties -> properties
                .mapColor(MapColor.METAL)
                .strength(5.0F, 10.0F)
                .sound(SoundType.METAL)
                .requiresCorrectToolForDrops());

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<TileEngineWood>> ENGINE_WOOD_TYPE =
        REGISTRY.addBlockEntity("engine_wood", TileEngineWood::new, ENGINE_WOOD);

    /** Ported unchanged in name from 1.12.2's {@code CREATIVE} variant of the same shared block -- see
     * {@code TileEngineCreative}'s own javadoc. Same default properties as {@link #ENGINE_WOOD}. */
    public static final DeferredBlock<BlockEngineCreative> ENGINE_CREATIVE =
        REGISTRY.addBlockAndItem("engine_creative", BlockEngineCreative::new,
            properties -> properties
                .mapColor(MapColor.METAL)
                .strength(5.0F, 10.0F)
                .sound(SoundType.METAL)
                .requiresCorrectToolForDrops());

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<TileEngineCreative>> ENGINE_CREATIVE_TYPE =
        REGISTRY.addBlockEntity("engine_creative", TileEngineCreative::new, ENGINE_CREATIVE);

    /** The water half of 1.12.2's single metadata-subtyped {@code BlockSpring} -- see
     * {@link BlockSpringWater}'s own javadoc. Spawned by {@code core.gen.SpringGenerator}, registered
     * alongside its {@code Feature} type in {@link BCCoreFeatures} -- see that class and
     * {@code SpringGenerator}'s own javadoc for the modern {@code Feature}/datapack world-gen system this
     * replaces 1.12.2's {@code SpringPopulate} event handler with. */
    public static final DeferredBlock<BlockSpringWater> SPRING_WATER =
        REGISTRY.addBlockAndItem("spring_water", BlockSpringWater::new,
            properties -> properties
                .mapColor(MapColor.STONE)
                .strength(-1.0F, 6000000.0F)
                .sound(SoundType.STONE)
                .noLootTable());

    /**
     * The six variants of 1.12.2's single metadata-subtyped {@code BlockDecoration} -- see
     * {@link BlockDecoration}'s own javadoc for why each is its own block/properties pair instead of one block
     * with a blockstate property. Light level is the one piece of real per-variant behaviour the old blockstate
     * property carried, so it is read straight from {@link EnumDecoratedBlock#lightValue} rather than
     * re-declared here.
     */
    private static UnaryOperator<BlockBehaviour.Properties> decoratedProperties(EnumDecoratedBlock variant) {
        return properties -> properties
            .mapColor(MapColor.METAL)
            .strength(5.0F, 10.0F)
            .sound(SoundType.METAL)
            .lightLevel(state -> variant.lightValue);
    }

    public static final DeferredBlock<BlockDecoration> DECORATED_DESTROY = REGISTRY.addBlockAndItem(
        "decorated_destroy", BlockDecoration::new, decoratedProperties(EnumDecoratedBlock.DESTROY));
    public static final DeferredBlock<BlockDecoration> DECORATED_BLUEPRINT = REGISTRY.addBlockAndItem(
        "decorated_blueprint", BlockDecoration::new, decoratedProperties(EnumDecoratedBlock.BLUEPRINT));
    public static final DeferredBlock<BlockDecoration> DECORATED_TEMPLATE = REGISTRY.addBlockAndItem(
        "decorated_template", BlockDecoration::new, decoratedProperties(EnumDecoratedBlock.TEMPLATE));
    public static final DeferredBlock<BlockDecoration> DECORATED_PAPER = REGISTRY.addBlockAndItem(
        "decorated_paper", BlockDecoration::new, decoratedProperties(EnumDecoratedBlock.PAPER));
    public static final DeferredBlock<BlockDecoration> DECORATED_LEATHER = REGISTRY.addBlockAndItem(
        "decorated_leather", BlockDecoration::new, decoratedProperties(EnumDecoratedBlock.LEATHER));
    public static final DeferredBlock<BlockDecoration> DECORATED_LASER_BACK = REGISTRY.addBlockAndItem(
        "decorated_laser_back", BlockDecoration::new, decoratedProperties(EnumDecoratedBlock.LASER_BACK));

    /**
     * BuildCraft's creative tab. The 1.12.2 build had one tab per module via {@code CreativeTabManager}; modern
     * versions build tabs declaratively, and {@link BCRegistry} keeps registration order so the contents still
     * appear in the order the module declares them.
     *
     * <p>Pulls from {@link BCRegistry#allCreativeTabEntries()}, not this class's own {@code REGISTRY}: every
     * module (core, factory, ...) owns a separate {@code BCRegistry} instance, so reading only this one would
     * silently drop every other module's blocks/items from the single tab BuildCraft actually has -- this bug
     * existed from {@code buildcraft.factory}'s very first machine onward, invisible to every RCON-based
     * verification pass this port has run since none of them ever open the creative-tab UI itself (they
     * {@code /give} items directly instead).
     */
    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> TAB_MAIN =
        CREATIVE_TABS.register("main", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.buildcraft.main"))
            .icon(() -> GEAR_WOOD.get().getDefaultInstance())
            .displayItems((params, output) -> {
                for (ItemLike entry : BCRegistry.allCreativeTabEntries()) {
                    output.accept(entry);
                }
            })
            .build());

    public static void register(IEventBus modBus) {
        REGISTRY.register(modBus);
        CREATIVE_TABS.register(modBus);
        BCCoreFeatures.register(modBus);
        modBus.addListener(BCCoreRegistries::registerCapabilities);
        // MarkerCache.registerCache has no other caller yet (see buildcraft.lib.marker's PORTING.md entry) --
        // without this, VolumeSubCache/PathSubCache's own MarkerCache.CACHES.indexOf(...) lookup returns -1,
        // and every MessageMarker they send would carry an invalid cache id.
        MarkerCache.registerCache(VolumeCache.INSTANCE);
        MarkerCache.registerCache(PathCache.INSTANCE);
    }

    /**
     * Binds MJ/area-provider capabilities to block entity types.
     *
     * <p>In 1.12.2 a tile attached its own capabilities by holding an {@code ICapabilityProvider}. NeoForge inverts
     * that: capabilities are registered per block entity type here, and the lookup is what finds the instance.
     */
    private static void registerCapabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(MjCapabilities.RECEIVER, POWER_TESTER_TYPE.get(), (tile, side) -> tile);
        event.registerBlockEntity(MjCapabilities.CONNECTOR, POWER_TESTER_TYPE.get(), (tile, side) -> tile);
        event.registerBlockEntity(TilesAPI.TILE_AREA_PROVIDER, MARKER_VOLUME_TYPE.get(), (tile, side) -> tile);

        // An engine's own mjConnector is the only MJ capability it exposes -- see TileEngineBase's own javadoc
        // ("Capabilities" entry) for why the other four (receiver/redstone-receiver/readable/passive-provider)
        // never apply to an engine. Guarded to the tile's currentDirection, matching 1.12.2's
        // "if (facing == currentDirection)" check in TileEngineBase_BC8#getCapability.
        event.registerBlockEntity(MjCapabilities.CONNECTOR, ENGINE_WOOD_TYPE.get(),
            (tile, side) -> side == tile.getCurrentFacing() ? tile.mjConnector : null);
        event.registerBlockEntity(MjCapabilities.CONNECTOR, ENGINE_CREATIVE_TYPE.get(),
            (tile, side) -> side == tile.getCurrentFacing() ? tile.mjConnector : null);
    }
}
