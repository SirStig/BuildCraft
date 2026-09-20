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

import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Registrations belonging to the old {@code buildcraftcore} module.
 *
 * <p>Ported from {@code buildcraft.core.BCCoreItems} / {@code BCCoreBlocks}. The 1.12.2 code
 * registered through BuildCraft's own {@code RegistrationHelper} during FML preInit; on modern
 * NeoForge that is replaced by {@link DeferredRegister}, which also carries the registry id so
 * items no longer need an unlocalised-name string threaded through their constructor.
 */
public final class BCCoreRegistries {

    private BCCoreRegistries() {}

    public static final DeferredRegister.Items ITEMS =
        DeferredRegister.createItems(BuildCraft.MOD_ID);

    public static final DeferredRegister<CreativeModeTab> CREATIVE_TABS =
        DeferredRegister.create(Registries.CREATIVE_MODE_TAB, BuildCraft.MOD_ID);

    // --- Gears ------------------------------------------------------------------
    // Plain crafting components with no behaviour, so they port across as simple items.
    public static final DeferredItem<Item> GEAR_WOOD = ITEMS.registerSimpleItem("gear_wood");
    public static final DeferredItem<Item> GEAR_STONE = ITEMS.registerSimpleItem("gear_stone");
    public static final DeferredItem<Item> GEAR_IRON = ITEMS.registerSimpleItem("gear_iron");
    public static final DeferredItem<Item> GEAR_GOLD = ITEMS.registerSimpleItem("gear_gold");
    public static final DeferredItem<Item> GEAR_DIAMOND = ITEMS.registerSimpleItem("gear_diamond");

    /**
     * BuildCraft's creative tab. The 1.12.2 build had one tab per module via
     * {@code CreativeTabManager}; modern versions build tabs declaratively instead.
     */
    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> TAB_MAIN =
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

    public static void register(IEventBus modBus) {
        ITEMS.register(modBus);
        CREATIVE_TABS.register(modBus);
    }
}
