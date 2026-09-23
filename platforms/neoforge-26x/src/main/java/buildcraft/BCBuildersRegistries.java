/*
 * Copyright (c) 2026 Joshua Kac -- NeoForge port (BuildCraft 10)
 *
 * This file is part of the BuildCraft 10 port and is distributed under the terms of the MIT License.
 * Please check the contents of the license, which should be located as "LICENSE.PORT" in the BuildCraft
 * source code distribution.
 */
package buildcraft;

import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.material.MapColor;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;

import buildcraft.api.mj.MjCapabilities;

import buildcraft.lib.registry.BCRegistry;

import buildcraft.builders.block.BlockFrame;
import buildcraft.builders.block.BlockQuarry;
import buildcraft.builders.tile.TileQuarry;

/**
 * Registrations belonging to the old {@code buildcraftbuilders} module -- the Quarry and its frame block are the
 * first ones ported. Mirrors {@link BCFactoryRegistries}' structure exactly.
 */
public final class BCBuildersRegistries {

    private BCBuildersRegistries() {}

    private static final BCRegistry REGISTRY = new BCRegistry(BuildCraft.MOD_ID);

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

    public static void register(IEventBus modBus) {
        REGISTRY.register(modBus);
        modBus.addListener(BCBuildersRegistries::registerCapabilities);
    }

    /** The quarry receives MJ on every side, the same unconditional wiring {@code buildcraft.factory.tile.
     * TileMiner}/{@code TileDistiller} already use for their own {@code MjBatteryReceiver}. */
    private static void registerCapabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(MjCapabilities.RECEIVER, QUARRY_TYPE.get(), (tile, side) -> tile.mjReceiver);
        event.registerBlockEntity(MjCapabilities.READABLE, QUARRY_TYPE.get(), (tile, side) -> tile.mjReceiver);
    }
}
