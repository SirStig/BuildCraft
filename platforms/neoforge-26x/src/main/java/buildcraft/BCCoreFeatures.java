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

import buildcraft.core.gen.SpringGenerator;

/**
 * World-generation {@link Feature} <em>types</em> BuildCraft owns, kept separate from {@link BCCoreRegistries}
 * since {@code Registries.FEATURE_TYPE} (what gets registered here) is a different kind of registry than
 * blocks/items -- it holds {@code MapCodec<? extends Feature>} values, the codec a feature instance
 * deserializes through, not the feature instances themselves.
 *
 * <p>The actual, fully-configured {@link SpringGenerator} instance and its {@code PlacedFeature} wrapper are
 * <em>not</em> registered here -- {@code Registries.FEATURE}/{@code Registries.PLACED_FEATURE} are dynamic
 * datapack registries on 26.x (confirmed via {@code javap} against {@code Registries}), not static code
 * registries, so they live purely as JSON under {@code data/buildcraft/worldgen/feature/spring_water.json}
 * and {@code .../placed_feature/spring_water.json}. See {@link SpringGenerator}'s own javadoc for the full
 * account of why this split exists on 26.x and not on 1.20.1.
 */
public final class BCCoreFeatures {

    private BCCoreFeatures() {}

    private static final DeferredRegister<MapCodec<? extends Feature>> FEATURE_TYPES =
        DeferredRegister.create(Registries.FEATURE_TYPE, BuildCraft.MOD_ID);

    public static final DeferredHolder<MapCodec<? extends Feature>, MapCodec<SpringGenerator>> SPRING_WATER_TYPE =
        FEATURE_TYPES.register("spring_water", () -> SpringGenerator.CODEC);

    public static void register(IEventBus modBus) {
        FEATURE_TYPES.register(modBus);
    }
}
