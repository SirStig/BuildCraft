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

import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/**
 * Registrations belonging to the old {@code buildcraftcore} module, for 1.20.1.
 *
 * <p>Mirrors the 26.x holder of the same name. Kept in sync by hand rather than shared,
 * because 1.20.1 has no {@code DeferredHolder} and registers items through
 * {@code ForgeRegistries} rather than vanilla's {@code Registries} keys.
 */
public final class BCCoreRegistries {

    private BCCoreRegistries() {}

    public static final DeferredRegister<Item> ITEMS =
        DeferredRegister.create(ForgeRegistries.ITEMS, BuildCraft.MOD_ID);

    public static final DeferredRegister<CreativeModeTab> CREATIVE_TABS =
        DeferredRegister.create(Registries.CREATIVE_MODE_TAB, BuildCraft.MOD_ID);

    // --- Gears ------------------------------------------------------------------
    public static final RegistryObject<Item> GEAR_WOOD = simpleItem("gear_wood");
    public static final RegistryObject<Item> GEAR_STONE = simpleItem("gear_stone");
    public static final RegistryObject<Item> GEAR_IRON = simpleItem("gear_iron");
    public static final RegistryObject<Item> GEAR_GOLD = simpleItem("gear_gold");
    public static final RegistryObject<Item> GEAR_DIAMOND = simpleItem("gear_diamond");

    public static final RegistryObject<CreativeModeTab> TAB_MAIN =
        CREATIVE_TABS.register("main", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.buildcraft.main"))
            .icon(() -> GEAR_WOOD.get().getDefaultInstance())
            .displayItems((params, output) -> {
                output.accept(GEAR_WOOD.get());
                output.accept(GEAR_STONE.get());
                output.accept(GEAR_IRON.get());
                output.accept(GEAR_GOLD.get());
                output.accept(GEAR_DIAMOND.get());
            })
            .build());

    private static RegistryObject<Item> simpleItem(String name) {
        return ITEMS.register(name, () -> new Item(new Item.Properties()));
    }

    public static void register(IEventBus modBus) {
        ITEMS.register(modBus);
        CREATIVE_TABS.register(modBus);
    }
}
