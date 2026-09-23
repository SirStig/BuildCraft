/*
 * Copyright (c) 2026 Joshua Kac -- NeoForge port (BuildCraft 10)
 *
 * This file is part of the BuildCraft 10 port and is distributed under the terms of the MIT License.
 * Please check the contents of the license, which should be located as "LICENSE.PORT" in the BuildCraft
 * source code distribution.
 */
package buildcraft;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.levelgen.feature.Feature;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import buildcraft.energy.gen.OilLakeGenerator;
import buildcraft.energy.gen.OilSpringGenerator;

/**
 * World-generation {@link Feature} <em>types</em> belonging to {@code buildcraft.energy} -- mirrors
 * {@link BCCoreFeatures} exactly (a separate holder, since {@code Registries.FEATURE_TYPE} is a different kind of
 * registry than blocks/items -- see that class's own javadoc for the full account of why the actual configured
 * {@link OilSpringGenerator}/{@link OilLakeGenerator} instances and their {@code PlacedFeature}/biome-modifier
 * wrappers are pure datapack JSON instead, under {@code data/buildcraft/worldgen/feature/spring_oil.json} and
 * friends).
 */
public final class BCEnergyFeatures {

    private BCEnergyFeatures() {}

    private static final DeferredRegister<MapCodec<? extends Feature>> FEATURE_TYPES =
        DeferredRegister.create(Registries.FEATURE_TYPE, BuildCraft.MOD_ID);

    public static final DeferredHolder<MapCodec<? extends Feature>, MapCodec<OilSpringGenerator>> SPRING_OIL_TYPE =
        FEATURE_TYPES.register("spring_oil", () -> OilSpringGenerator.CODEC);

    public static final DeferredHolder<MapCodec<? extends Feature>, MapCodec<OilLakeGenerator>> OIL_LAKE_TYPE =
        FEATURE_TYPES.register("oil_lake", () -> OilLakeGenerator.CODEC);

    public static void register(IEventBus modBus) {
        FEATURE_TYPES.register(modBus);
    }
}
