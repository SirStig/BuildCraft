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

import buildcraft.lib.registry.BCRegistry;

import buildcraft.builders.block.BlockFrame;
import buildcraft.builders.block.BlockQuarry;
import buildcraft.builders.tile.TileQuarry;

/**
 * Registrations belonging to the old {@code buildcraftbuilders} module. Mirrors the 26.x class of the same name --
 * see that one's javadoc for what each block's default properties match. Unlike 26.x, {@link TileQuarry} exposes
 * its own MJ capabilities through {@code getCapability} (see that class's own javadoc), so there is no
 * capability-registration listener here.
 */
public final class BCBuildersRegistries {

    private BCBuildersRegistries() {}

    private static final BCRegistry REGISTRY = new BCRegistry(BuildCraft.MOD_ID);

    public static final RegistryObject<BlockFrame> FRAME = REGISTRY.addBlock("frame", () -> new BlockFrame(
        BlockBehaviour.Properties.of()
            .mapColor(MapColor.METAL)
            .strength(5.0F, 10.0F)
            .sound(SoundType.METAL)
            .noOcclusion()
            .noLootTable()));

    public static final RegistryObject<BlockQuarry> QUARRY = REGISTRY.addBlockAndItem("quarry", () -> new BlockQuarry(
        BlockBehaviour.Properties.of()
            .mapColor(MapColor.METAL)
            .strength(5.0F, 10.0F)
            .sound(SoundType.METAL)
            .requiresCorrectToolForDrops()));

    public static final RegistryObject<BlockEntityType<TileQuarry>> QUARRY_TYPE =
        REGISTRY.addBlockEntity("quarry", TileQuarry::new, QUARRY);

    public static void register(IEventBus modBus) {
        REGISTRY.register(modBus);
    }
}
