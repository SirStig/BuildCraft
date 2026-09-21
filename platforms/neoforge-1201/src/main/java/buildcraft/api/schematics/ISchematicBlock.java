/*
 * Copyright (c) 2026 Joshua Kac -- NeoForge port (BuildCraft 10)
 *
 * The BuildCraft API is distributed under the terms of the MIT License. Please check the contents of the
 * license, which should be located as "LICENSE.API" in the BuildCraft source code distribution.
 */
package buildcraft.api.schematics;

import java.util.Collections;
import java.util.List;
import java.util.Set;

import org.jetbrains.annotations.NotNull;

import net.minecraft.world.item.ItemStack;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

import net.minecraftforge.fluids.FluidStack;

import buildcraft.api.core.InvalidInputDataException;

public interface ISchematicBlock {
    void init(SchematicBlockContext context);

    default boolean isAir() {
        return false;
    }

    @NotNull
    default Set<BlockPos> getRequiredBlockOffsets() {
        return Collections.emptySet();
    }

    @NotNull
    default List<ItemStack> computeRequiredItems() {
        return Collections.emptyList();
    }

    @NotNull
    default List<FluidStack> computeRequiredFluids() {
        return Collections.emptyList();
    }

    ISchematicBlock getRotated(Rotation rotation);

    boolean canBuild(Level level, BlockPos blockPos);

    default boolean isReadyToBuild(Level level, BlockPos blockPos) {
        return true;
    }

    boolean build(Level level, BlockPos blockPos);

    boolean buildWithoutChecks(Level level, BlockPos blockPos);

    boolean isBuilt(Level level, BlockPos blockPos);

    CompoundTag serializeNBT();

    /** @throws InvalidInputDataException If the input data wasn't correct or didn't make sense. */
    void deserializeNBT(CompoundTag nbt) throws InvalidInputDataException;
}
