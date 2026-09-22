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
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;

import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.RegistryObject;

import buildcraft.api.inventory.ItemTransactorCapabilities;

import buildcraft.lib.registry.BCRegistry;

import buildcraft.factory.block.BlockChute;
import buildcraft.factory.block.BlockFloodGate;
import buildcraft.factory.block.BlockMiningWell;
import buildcraft.factory.block.BlockPump;
import buildcraft.factory.block.BlockTank;
import buildcraft.factory.block.BlockTube;
import buildcraft.factory.tile.TileChute;
import buildcraft.factory.tile.TileFloodGate;
import buildcraft.factory.tile.TileMiningWell;
import buildcraft.factory.tile.TilePump;
import buildcraft.factory.tile.TileTank;

/**
 * Registrations belonging to the old {@code buildcraftfactory} module -- the first ones. Mirrors
 * {@link BCCoreRegistries}' structure. Unlike 26.x, {@code TileChute} exposes its own capabilities through
 * {@code getCapability} (see that class's own javadoc), so there is no capability-registration listener here --
 * {@link ItemTransactorCapabilities#register} still needs a hook, since 1.20.1 lost {@code @CapabilityInject} and
 * every capability this port defines has to be declared somewhere (matching {@code MjCapabilities}' own
 * precedent), even though nothing in this pass provides {@code IItemTransactor} as a capability yet.
 */
public final class BCFactoryRegistries {

    private BCFactoryRegistries() {}

    private static final BCRegistry REGISTRY = new BCRegistry(BuildCraft.MOD_ID);

    /** Default properties match what {@code BlockBCTile_Neptune}'s constructor gave every 1.12.2 BuildCraft
     * block (hardness 5, resistance 10, {@code SoundType.METAL}), the same reasoning already worked out for
     * {@code BlockDecoration}/{@code BlockEngineWood}. {@code noOcclusion()} keeps the one non-cosmetic half of
     * 1.12.2's {@code isOpaqueCube() -> false} -- see {@link BlockChute}'s own javadoc for why no shape override
     * accompanies it. */
    public static final RegistryObject<BlockChute> CHUTE = REGISTRY.addBlockAndItem("chute", () -> new BlockChute(
        BlockBehaviour.Properties.of()
            .mapColor(MapColor.METAL)
            .strength(5.0F, 10.0F)
            .sound(SoundType.METAL)
            .noOcclusion()
            .requiresCorrectToolForDrops()));

    public static final RegistryObject<BlockEntityType<TileChute>> CHUTE_TYPE =
        REGISTRY.addBlockEntity("chute", TileChute::new, CHUTE);

    /** Same default properties as {@link #CHUTE} -- see that field's own javadoc. */
    public static final RegistryObject<BlockMiningWell> MINING_WELL = REGISTRY.addBlockAndItem("mining_well", () -> new BlockMiningWell(
        BlockBehaviour.Properties.of()
            .mapColor(MapColor.METAL)
            .strength(5.0F, 10.0F)
            .sound(SoundType.METAL)
            .requiresCorrectToolForDrops()));

    public static final RegistryObject<BlockEntityType<TileMiningWell>> MINING_WELL_TYPE =
        REGISTRY.addBlockEntity("mining_well", TileMiningWell::new, MINING_WELL);

    /** Same default properties as {@link #CHUTE}/{@link #MINING_WELL} -- see {@link #CHUTE}'s own javadoc. */
    public static final RegistryObject<BlockPump> PUMP = REGISTRY.addBlockAndItem("pump", () -> new BlockPump(
        BlockBehaviour.Properties.of()
            .mapColor(MapColor.METAL)
            .strength(5.0F, 10.0F)
            .sound(SoundType.METAL)
            .requiresCorrectToolForDrops()));

    public static final RegistryObject<BlockEntityType<TilePump>> PUMP_TYPE =
        REGISTRY.addBlockEntity("pump", TilePump::new, PUMP);

    /** Same default properties as {@link #CHUTE}/{@link #MINING_WELL}/{@link #PUMP} -- see {@link #CHUTE}'s own
     * javadoc. */
    public static final RegistryObject<BlockTank> TANK = REGISTRY.addBlockAndItem("tank", () -> new BlockTank(
        BlockBehaviour.Properties.of()
            .mapColor(MapColor.METAL)
            .strength(5.0F, 10.0F)
            .sound(SoundType.METAL)
            .requiresCorrectToolForDrops()));

    public static final RegistryObject<BlockEntityType<TileTank>> TANK_TYPE =
        REGISTRY.addBlockEntity("tank", TileTank::new, TANK);

    /** Same default properties as {@link #CHUTE}/{@link #MINING_WELL}/{@link #PUMP}/{@link #TANK} -- see
     * {@link #CHUTE}'s own javadoc. A plain full cube -- see {@link BlockFloodGate}'s own javadoc, and the 26.x
     * copy of this file, for why {@code openSides} is real gameplay state even though its 1.12.2 blockstate
     * visualisation is dropped. */
    public static final RegistryObject<BlockFloodGate> FLOOD_GATE = REGISTRY.addBlockAndItem("flood_gate", () -> new BlockFloodGate(
        BlockBehaviour.Properties.of()
            .mapColor(MapColor.METAL)
            .strength(5.0F, 10.0F)
            .sound(SoundType.METAL)
            .requiresCorrectToolForDrops()));

    public static final RegistryObject<BlockEntityType<TileFloodGate>> FLOOD_GATE_TYPE =
        REGISTRY.addBlockEntity("flood_gate", TileFloodGate::new, FLOOD_GATE);

    /** No {@code BlockItem} ({@link BCRegistry#addBlock}, not {@code addBlockAndItem}) and an empty loot table --
     * see {@link BlockTube}'s own javadoc for why. {@code strength(-1.0F, ...)} matches
     * {@code BlockSpringWater}'s "always unbreakable" precedent; {@code noOcclusion()} keeps the one non-cosmetic
     * half of 1.12.2's {@code isOpaqueCube()}/{@code isFullCube() -> false}. */
    public static final RegistryObject<BlockTube> TUBE = REGISTRY.addBlock("tube", () -> new BlockTube(
        BlockBehaviour.Properties.of()
            .mapColor(MapColor.METAL)
            .strength(-1.0F, 6_000_000.0F)
            .sound(SoundType.METAL)
            .noOcclusion()
            .noLootTable()));

    public static void register(IEventBus modBus) {
        REGISTRY.register(modBus);
        modBus.addListener(ItemTransactorCapabilities::register);
    }
}
