/*
 * Copyright (c) 2026 Joshua Kac -- NeoForge port (BuildCraft 10)
 *
 * The BuildCraft API is distributed under the terms of the MIT License. Please check the contents of the
 * license, which should be located as "LICENSE.API" in the BuildCraft source code distribution.
 */
package buildcraft.api.schematics;

import java.util.Collections;
import java.util.List;

import org.jetbrains.annotations.NotNull;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.Level;

import net.minecraftforge.fluids.FluidStack;

import buildcraft.api.core.InvalidInputDataException;

public interface ISchematicEntity {
    void init(SchematicEntityContext context);

    Vec3 getPos();

    @NotNull
    default List<ItemStack> computeRequiredItems() {
        return Collections.emptyList();
    }

    @NotNull
    default List<FluidStack> computeRequiredFluids() {
        return Collections.emptyList();
    }

    ISchematicEntity getRotated(Rotation rotation);

    Entity build(Level level, BlockPos basePos);

    Entity buildWithoutChecks(Level level, BlockPos basePos);

    CompoundTag serializeNBT();

    /** @throws InvalidInputDataException If the input data wasn't correct or didn't make sense. */
    void deserializeNBT(CompoundTag nbt) throws InvalidInputDataException;
}
