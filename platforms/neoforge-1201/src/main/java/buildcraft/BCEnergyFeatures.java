/*
 * Copyright (c) 2026 Joshua Kac -- NeoForge port (BuildCraft 10)
 *
 * This file is part of the BuildCraft 10 port and is distributed under the terms of the MIT License.
 * Please check the contents of the license, which should be located as "LICENSE.PORT" in the BuildCraft
 * source code distribution.
 */
package buildcraft;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.levelgen.feature.Feature;

import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

import buildcraft.energy.gen.OilSpringGenerator;

/**
 * World-generation {@link Feature} types belonging to {@code buildcraft.energy} -- mirrors {@link BCCoreFeatures}
 * exactly. The {@code ConfiguredFeature}/{@code PlacedFeature} pair that actually configures and places
 * {@link OilSpringGenerator} are pure datapack JSON, under
 * {@code data/buildcraft/worldgen/configured_feature/spring_oil.json} and
 * {@code .../placed_feature/spring_oil.json}.
 */
public final class BCEnergyFeatures {

    private BCEnergyFeatures() {}

    private static final DeferredRegister<Feature<?>> FEATURES =
        DeferredRegister.create(Registries.FEATURE, BuildCraft.MOD_ID);

    public static final RegistryObject<OilSpringGenerator> SPRING_OIL = FEATURES.register("spring_oil", OilSpringGenerator::new);

    public static void register(IEventBus modBus) {
        FEATURES.register(modBus);
    }
}
