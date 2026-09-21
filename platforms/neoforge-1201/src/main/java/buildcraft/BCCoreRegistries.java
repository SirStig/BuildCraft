/*
 * Copyright (c) 2026 Joshua Kac -- NeoForge port (BuildCraft 10)
 *
 * This file is part of the BuildCraft 10 port and is distributed under the terms of the MIT License.
 * Please check the contents of the license, which should be located as "LICENSE.PORT" in the BuildCraft
 * source code distribution.
 */
package buildcraft;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;

import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

import buildcraft.api.enums.EnumDecoratedBlock;
import buildcraft.core.block.BlockDecoration;
import buildcraft.core.block.BlockMarkerPath;
import buildcraft.core.block.BlockMarkerVolume;
import buildcraft.core.block.BlockPowerConsumerTester;
import buildcraft.core.block.BlockSpringWater;
import buildcraft.core.item.ItemGoggles;
import buildcraft.core.item.ItemMarkerConnector;
import buildcraft.core.item.ItemWrench;
import buildcraft.core.marker.PathCache;
import buildcraft.core.marker.VolumeCache;
import buildcraft.core.tile.TileMarkerPath;
import buildcraft.core.tile.TileMarkerVolume;
import buildcraft.core.tile.TilePowerConsumerTester;
import buildcraft.lib.marker.MarkerCache;
import buildcraft.lib.registry.BCRegistry;

/**
 * Registrations belonging to the old {@code buildcraftcore} module, for 1.20.1.
 *
 * <p>Mirrors the 26.x holder of the same name. Kept separate because 1.20.1 has no
 * {@code DeferredBlock}/{@code DeferredItem} and block properties are built eagerly rather than passed as a
 * {@code UnaryOperator}.
 */
public final class BCCoreRegistries {

    private BCCoreRegistries() {}

    private static final BCRegistry REGISTRY = new BCRegistry(BuildCraft.MOD_ID);

    public static final DeferredRegister<CreativeModeTab> CREATIVE_TABS =
        DeferredRegister.create(Registries.CREATIVE_MODE_TAB, BuildCraft.MOD_ID);

    // --- Gears ------------------------------------------------------------------
    public static final RegistryObject<Item> GEAR_WOOD = REGISTRY.addItem("gear_wood");
    public static final RegistryObject<Item> GEAR_STONE = REGISTRY.addItem("gear_stone");
    public static final RegistryObject<Item> GEAR_IRON = REGISTRY.addItem("gear_iron");
    public static final RegistryObject<Item> GEAR_GOLD = REGISTRY.addItem("gear_gold");
    public static final RegistryObject<Item> GEAR_DIAMOND = REGISTRY.addItem("gear_diamond");

    // --- Tools --------------------------------------------------------------------
    public static final RegistryObject<ItemWrench> WRENCH =
        REGISTRY.addItem("wrench", () -> new ItemWrench(new Item.Properties()));
    public static final RegistryObject<ItemMarkerConnector> MARKER_CONNECTOR =
        REGISTRY.addItem("marker_connector", () -> new ItemMarkerConnector(new Item.Properties()));
    public static final RegistryObject<ItemGoggles> GOGGLES =
        REGISTRY.addItem("goggles", () -> new ItemGoggles(new Item.Properties()));

    // --- Markers ------------------------------------------------------------------
    private static BlockBehaviour.Properties markerProperties() {
        return BlockBehaviour.Properties.of()
            .mapColor(MapColor.NONE)
            .noCollission()
            .noOcclusion()
            .strength(0.25F)
            .sound(SoundType.WOOD);
    }

    public static final RegistryObject<BlockMarkerVolume> MARKER_VOLUME =
        REGISTRY.addBlockAndItem("marker_volume", () -> new BlockMarkerVolume(markerProperties()));

    public static final RegistryObject<BlockEntityType<TileMarkerVolume>> MARKER_VOLUME_TYPE =
        REGISTRY.addBlockEntity("marker_volume", TileMarkerVolume::new, MARKER_VOLUME);

    public static final RegistryObject<BlockMarkerPath> MARKER_PATH =
        REGISTRY.addBlockAndItem("marker_path", () -> new BlockMarkerPath(markerProperties()));

    public static final RegistryObject<BlockEntityType<TileMarkerPath>> MARKER_PATH_TYPE =
        REGISTRY.addBlockEntity("marker_path", TileMarkerPath::new, MARKER_PATH);

    // --- Machines ---------------------------------------------------------------
    public static final RegistryObject<BlockPowerConsumerTester> POWER_TESTER =
        REGISTRY.addBlockAndItem("power_tester", () -> new BlockPowerConsumerTester(
            BlockBehaviour.Properties.of()
                .mapColor(MapColor.METAL)
                .strength(5.0F, 6.0F)
                .sound(SoundType.METAL)
                .requiresCorrectToolForDrops()));

    public static final RegistryObject<BlockEntityType<TilePowerConsumerTester>> POWER_TESTER_TYPE =
        REGISTRY.addBlockEntity("power_tester", TilePowerConsumerTester::new, POWER_TESTER);

    /** The water half of 1.12.2's single metadata-subtyped {@code BlockSpring} -- see
     * {@link BlockSpringWater}'s own javadoc. Not yet spawned anywhere: {@code core.gen.SpringPopulate}, the
     * world-generation hook that placed it in 1.12.2, needs its own redesign against the modern
     * {@code Feature}/datapack world-gen system (PORTING.md's {@code buildcraft.core} survey). */
    public static final RegistryObject<BlockSpringWater> SPRING_WATER =
        REGISTRY.addBlockAndItem("spring_water", () -> new BlockSpringWater(
            BlockBehaviour.Properties.of()
                .mapColor(MapColor.STONE)
                .strength(-1.0F, 6000000.0F)
                .sound(SoundType.STONE)
                .noLootTable()));

    /**
     * The six variants of 1.12.2's single metadata-subtyped {@code BlockDecoration} -- see
     * {@link BlockDecoration}'s own javadoc for why each is its own block/properties pair instead of one block
     * with a blockstate property. Light level is the one piece of real per-variant behaviour the old blockstate
     * property carried, so it is read straight from {@link EnumDecoratedBlock#lightValue} rather than
     * re-declared here.
     */
    private static BlockBehaviour.Properties decoratedProperties(EnumDecoratedBlock variant) {
        return BlockBehaviour.Properties.of()
            .mapColor(MapColor.METAL)
            .strength(5.0F, 10.0F)
            .sound(SoundType.METAL)
            .lightLevel(state -> variant.lightValue);
    }

    public static final RegistryObject<BlockDecoration> DECORATED_DESTROY = REGISTRY.addBlockAndItem(
        "decorated_destroy", () -> new BlockDecoration(decoratedProperties(EnumDecoratedBlock.DESTROY)));
    public static final RegistryObject<BlockDecoration> DECORATED_BLUEPRINT = REGISTRY.addBlockAndItem(
        "decorated_blueprint", () -> new BlockDecoration(decoratedProperties(EnumDecoratedBlock.BLUEPRINT)));
    public static final RegistryObject<BlockDecoration> DECORATED_TEMPLATE = REGISTRY.addBlockAndItem(
        "decorated_template", () -> new BlockDecoration(decoratedProperties(EnumDecoratedBlock.TEMPLATE)));
    public static final RegistryObject<BlockDecoration> DECORATED_PAPER = REGISTRY.addBlockAndItem(
        "decorated_paper", () -> new BlockDecoration(decoratedProperties(EnumDecoratedBlock.PAPER)));
    public static final RegistryObject<BlockDecoration> DECORATED_LEATHER = REGISTRY.addBlockAndItem(
        "decorated_leather", () -> new BlockDecoration(decoratedProperties(EnumDecoratedBlock.LEATHER)));
    public static final RegistryObject<BlockDecoration> DECORATED_LASER_BACK = REGISTRY.addBlockAndItem(
        "decorated_laser_back", () -> new BlockDecoration(decoratedProperties(EnumDecoratedBlock.LASER_BACK)));

    /** BuildCraft's creative tab. {@link BCRegistry} keeps registration order, as the 1.12.2 tabs did. */
    public static final RegistryObject<CreativeModeTab> TAB_MAIN =
        CREATIVE_TABS.register("main", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.buildcraft.main"))
            .icon(() -> GEAR_WOOD.get().getDefaultInstance())
            .displayItems((params, output) -> {
                for (ItemLike entry : REGISTRY.creativeTabEntries()) {
                    output.accept(entry);
                }
            })
            .build());

    public static void register(IEventBus modBus) {
        REGISTRY.register(modBus);
        CREATIVE_TABS.register(modBus);
        // MarkerCache.registerCache has no other caller yet (see buildcraft.lib.marker's PORTING.md entry) --
        // without this, VolumeSubCache/PathSubCache's own MarkerCache.CACHES.indexOf(...) lookup returns -1,
        // and every MessageMarker they send would carry an invalid cache id.
        MarkerCache.registerCache(VolumeCache.INSTANCE);
        MarkerCache.registerCache(PathCache.INSTANCE);
    }
}
