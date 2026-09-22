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

import buildcraft.core.gen.SpringGenerator;

/**
 * World-generation {@link Feature} types BuildCraft owns. Unlike 26.x, {@code Registries.FEATURE} is still a
 * static code registry here (confirmed via {@code javap}: {@code ResourceKey<Registry<Feature<?>>>}), the
 * same classic shape {@code Registries.CREATIVE_MODE_TAB} and every other {@code DeferredRegister} in
 * {@link BCCoreRegistries} already uses -- see {@link SpringGenerator}'s own javadoc for why 26.x needs a
 * different split (this class's 26.x counterpart registers a codec, not a feature instance).
 *
 * <p>The {@code ConfiguredFeature}/{@code PlacedFeature} pair that actually configures and places
 * {@link SpringGenerator} are datapack registries, not code -- pure JSON under
 * {@code data/buildcraft/worldgen/configured_feature/spring_water.json} and
 * {@code .../placed_feature/spring_water.json}.
 */
public final class BCCoreFeatures {

    private BCCoreFeatures() {}

    private static final DeferredRegister<Feature<?>> FEATURES =
        DeferredRegister.create(Registries.FEATURE, BuildCraft.MOD_ID);

    public static final RegistryObject<SpringGenerator> SPRING_WATER = FEATURES.register("spring_water", SpringGenerator::new);

    public static void register(IEventBus modBus) {
        FEATURES.register(modBus);
    }
}
