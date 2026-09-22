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

import buildcraft.factory.block.BlockChute;
import buildcraft.factory.tile.TileChute;
import buildcraft.lib.registry.BCRegistry;

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

    public static void register(IEventBus modBus) {
        REGISTRY.register(modBus);
        modBus.addListener(ItemTransactorCapabilities::register);
    }
}
