/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
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

import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

import buildcraft.api.mj.MjCapabilities;
import buildcraft.core.block.BlockPowerConsumerTester;
import buildcraft.core.tile.TilePowerConsumerTester;
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
    }

    /**
     * Binds MJ capabilities to block entity types.
     *
     * <p>In 1.12.2 a tile attached its own capabilities by holding an {@code ICapabilityProvider}. NeoForge inverts
     * that: capabilities are registered per block entity type here, and the lookup is what finds the instance.
     */
    private static void registerCapabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(MjCapabilities.RECEIVER, POWER_TESTER_TYPE.get(), (tile, side) -> tile);
        event.registerBlockEntity(MjCapabilities.CONNECTOR, POWER_TESTER_TYPE.get(), (tile, side) -> tile);
    }
}
