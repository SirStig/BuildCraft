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

import buildcraft.core.block.BlockPowerConsumerTester;
import buildcraft.core.block.BlockSpringWater;
import buildcraft.core.item.ItemWrench;
import buildcraft.core.tile.TilePowerConsumerTester;
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
    }
}
