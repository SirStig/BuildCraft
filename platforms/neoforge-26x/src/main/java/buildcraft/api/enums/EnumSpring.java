/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * The BuildCraft API is distributed under the terms of the MIT License. Please check the contents of the license, which
 * should be located as "LICENSE.API" in the BuildCraft source code distribution.
 */
package buildcraft.api.enums;

import java.util.Locale;
import java.util.function.Supplier;

import org.jetbrains.annotations.Nullable;

import net.minecraft.util.StringRepresentable;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import buildcraft.api.properties.BuildCraftProperties;

/**
 * The infinite liquid sources BuildCraft generates: water springs, and oil springs under oil deposits.
 *
 * <p>{@code net.minecraft.init.Blocks} became {@link net.minecraft.world.level.block.Blocks}, and
 * {@code Block.getDefaultState()} became {@code Block.defaultBlockState()}.
 *
 * <p>{@link #liquidBlock} stays mutable and {@link #OIL}'s stays null until the energy module registers oil, which
 * is how 1.12.2 did it -- the api module cannot depend on the module that defines the fluid.
 */
public enum EnumSpring implements StringRepresentable {
    WATER(5, -1, Blocks.WATER.defaultBlockState()),
    OIL(6000, 8, null); // Set by the energy module.

    public static final EnumSpring[] VALUES = values();

    public final int tickRate;
    public final int chance;

    @Nullable
    public BlockState liquidBlock;

    public boolean canGen = true;

    @Nullable
    public Supplier<BlockEntity> tileConstructor;

    private final String serializedName = name().toLowerCase(Locale.ROOT);

    EnumSpring(int tickRate, int chance, @Nullable BlockState liquidBlock) {
        this.tickRate = tickRate;
        this.chance = chance;
        this.liquidBlock = liquidBlock;
    }

    public static EnumSpring fromState(BlockState state) {
        return state.getValue(BuildCraftProperties.SPRING_TYPE);
    }

    @Override
    public String getSerializedName() {
        return serializedName;
    }
}
