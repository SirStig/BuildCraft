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

import buildcraft.api.mj.MjCapabilities;

import buildcraft.lib.registry.BCRegistry;

import buildcraft.energy.block.BlockEngineStone;
import buildcraft.energy.container.ContainerEngineStone;
import buildcraft.energy.tile.TileEngineStone;

/**
 * Registrations belonging to the old {@code buildcraftenergy} module -- the first ones, and the first module in
 * this port with no registration holder yet. Mirrors {@link BCFactoryRegistries}' exact structure.
 */
public final class BCEnergyRegistries {

    private BCEnergyRegistries() {}

    private static final BCRegistry REGISTRY = new BCRegistry(BuildCraft.MOD_ID);

    /** Properties match every other machine ported so far ({@code BCCoreRegistries#ENGINE_WOOD}/
     * {@code #ENGINE_CREATIVE}, {@code BCFactoryRegistries#CHUTE}, ...): hardness 5, resistance 10,
     * {@code SoundType.METAL} -- what {@code BlockBCTile_Neptune}'s constructor gave every 1.12.2 BuildCraft
     * block by default, and {@code TileEngineBase_BC8} never overrode any of them either. */
    public static final DeferredBlock<BlockEngineStone> ENGINE_STONE = REGISTRY.addBlockAndItem(
        "engine_stone", BlockEngineStone::new,
        properties -> properties
            .mapColor(MapColor.METAL)
            .strength(5.0F, 10.0F)
            .sound(SoundType.METAL)
            .requiresCorrectToolForDrops());

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<TileEngineStone>> ENGINE_STONE_TYPE =
        REGISTRY.addBlockEntity("engine_stone", TileEngineStone::new, ENGINE_STONE);

    /** See {@code BCRegistry#addMenu}'s own javadoc for why this exists, and
     * {@code buildcraft.energy.client.BCEnergyClientRegistries} for the separate client-only screen registration
     * this pairs with. */
    public static final DeferredHolder<MenuType<?>, MenuType<ContainerEngineStone>> ENGINE_STONE_MENU =
        REGISTRY.addMenu("engine_stone", ContainerEngineStone::new);

    public static void register(IEventBus modBus) {
        REGISTRY.register(modBus);
        modBus.addListener(BCEnergyRegistries::registerCapabilities);
    }

    /**
     * The fuel slot is reachable from every {@code EnumPipePart} it registered with ({@code EnumAccess.BOTH,
     * EnumPipePart.VALUES} -- see {@link TileEngineStone}), so this delegates to
     * {@code itemManager.getHandlerForFace} unconditionally, exactly like {@code BCFactoryRegistries}' own
     * {@code CHUTE}/{@code AUTO_WORKBENCH_ITEMS} entries. The MJ connector is guarded to a single facing, matching
     * {@code BCCoreRegistries}' {@code ENGINE_WOOD}/{@code ENGINE_CREATIVE} entries -- see that class's own
     * reasoning for the guard.
     */
    private static void registerCapabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(Capabilities.Item.BLOCK, ENGINE_STONE_TYPE.get(),
            (tile, side) -> tile.itemManager.getHandlerForFace(side));
        event.registerBlockEntity(MjCapabilities.CONNECTOR, ENGINE_STONE_TYPE.get(),
            (tile, side) -> side == tile.getCurrentFacing() ? tile.mjConnector : null);
    }
}
