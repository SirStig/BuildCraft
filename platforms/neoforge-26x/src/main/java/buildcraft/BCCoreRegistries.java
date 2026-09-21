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

import buildcraft.api.mj.MjCapabilities;
import buildcraft.api.tiles.TilesAPI;
import buildcraft.core.block.BlockMarkerPath;
import buildcraft.core.block.BlockMarkerVolume;
import buildcraft.core.block.BlockPowerConsumerTester;
import buildcraft.core.block.BlockSpringWater;
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

    /** The water half of 1.12.2's single metadata-subtyped {@code BlockSpring} -- see
     * {@link BlockSpringWater}'s own javadoc. Not yet spawned anywhere: {@code core.gen.SpringPopulate}, the
     * world-generation hook that placed it in 1.12.2, needs its own redesign against the modern
     * {@code Feature}/datapack world-gen system (PORTING.md's {@code buildcraft.core} survey). */
    public static final DeferredBlock<BlockSpringWater> SPRING_WATER =
        REGISTRY.addBlockAndItem("spring_water", BlockSpringWater::new,
            properties -> properties
                .mapColor(MapColor.STONE)
                .strength(-1.0F, 6000000.0F)
                .sound(SoundType.STONE)
                .noLootTable());

    /**
     * BuildCraft's creative tab. The 1.12.2 build had one tab per module via {@code CreativeTabManager}; modern
     * versions build tabs declaratively, and {@link BCRegistry} keeps registration order so the contents still
     * appear in the order the module declares them.
     */
    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> TAB_MAIN =
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
    }
}
