/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * Copyright (c) 2026 Joshua Kac -- NeoForge port (BuildCraft 10)
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL
 * was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.lib.fluid;

import java.util.function.Consumer;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import net.minecraftforge.client.extensions.common.IClientFluidTypeExtensions;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidType;

/**
 * The port of 1.12.2's {@code buildcraft.lib.fluid.BCFluid}. In 1.12.2 a Forge {@code Fluid} carried every
 * fluid-wide property itself (density, viscosity, temperature, textures, colour); on this target those properties
 * belong to Forge's {@link FluidType} instead, and the vanilla {@code Fluid} objects (a source and a flowing
 * instance, see {@code buildcraft.energy.BCEnergyFluids}) only point at it. So this is the class that picks up
 * {@code BCFluid}'s extra BuildCraft fields -- heat, the light/dark recolour pair, flammability -- and its one
 * behavioural override, the heat-suffixed display name.
 *
 * <p><b>Client rendering is the classic 1.20.1 hook, confirmed against the real 1.20.1 sources jar:</b>
 * {@code FluidType}'s constructor calls its own private {@code initClient()}, which -- only when
 * {@code FMLEnvironment.dist == Dist.CLIENT} and not in datagen -- calls {@link #initializeClient} with a consumer
 * that stores the supplied {@link IClientFluidTypeExtensions} as the type's render properties. Vanilla's
 * {@code LiquidBlockRenderer} (via {@code ForgeHooksClient#getFluidSprites}), Forge's
 * {@code DynamicFluidContainerModel} (the bucket model), and this port's own {@code RenderTileTank}/
 * {@code GuiAutoCraftFluids} all read the still/flowing texture and tint through
 * {@code IClientFluidTypeExtensions.of(...)}. The textures are stitched automatically: they live under
 * {@code textures/block/}, which vanilla's {@code atlases/blocks.json} {@code directory} source covers for every
 * namespace. The tint is left at the interface default ({@code 0xFFFFFFFF}): see {@code BCEnergyFluids}' javadoc
 * for why the colour is baked into the sprites instead -- which is also why 1.12.2's
 * {@code BCFluid#setColour(light, dark)} forced its own tint colour to white.
 *
 * <p>This differs from the 26.x copy of this class, where {@code initializeClient} and those texture/tint methods
 * no longer exist and the same ids are handed to vanilla's {@code FluidStateModelSet} instead.
 */
public class BCFluidType extends FluidType {

    private final ResourceLocation stillTexture;
    private final ResourceLocation flowingTexture;
    private final int heat;
    private final boolean heatable;
    private final int lightColour;
    private final int darkColour;
    private final boolean flammable;

    public BCFluidType(
        Properties properties,
        ResourceLocation stillTexture,
        ResourceLocation flowingTexture,
        int heat,
        boolean heatable,
        int lightColour,
        int darkColour,
        boolean flammable
    ) {
        super(properties);
        this.stillTexture = stillTexture;
        this.flowingTexture = flowingTexture;
        this.heat = heat;
        this.heatable = heatable;
        this.lightColour = lightColour;
        this.darkColour = darkColour;
        this.flammable = flammable;
    }

    /**
     * Note {@code FluidType}'s constructor calls this before this subclass's own constructor body has run, so the
     * texture fields are still {@code null} at that moment. The anonymous class therefore reads
     * {@code BCFluidType.this.stillTexture} on every call rather than capturing the value up front -- by the time
     * anything renders, construction has long finished. (Forge itself also rejects a {@code FluidType} that passes
     * itself as its own extensions, which is why this is a separate anonymous object.)
     */
    @Override
    public void initializeClient(Consumer<IClientFluidTypeExtensions> consumer) {
        consumer.accept(new IClientFluidTypeExtensions() {
            @Override
            public ResourceLocation getStillTexture() {
                return BCFluidType.this.stillTexture;
            }

            @Override
            public ResourceLocation getFlowingTexture() {
                return BCFluidType.this.flowingTexture;
            }
        });
    }

    /** 1.12.2's {@code BCFluid#getLocalizedName}: every heatable fluid -- including heat 0 -- is shown as
     * {@code "<name> (Cool)"}/{@code "(Hot)"}/{@code "(Searing)"} through the {@code buildcraft.fluid.heat_N}
     * format key, wrapping the bare fluid name. */
    public Component withHeat(Component bareName) {
        if (heat <= 0 && !heatable) {
            return bareName;
        }
        return Component.translatable("buildcraft.fluid.heat_" + heat, bareName);
    }

    @Override
    public Component getDescription() {
        return withHeat(super.getDescription());
    }

    @Override
    public Component getDescription(FluidStack stack) {
        return withHeat(super.getDescription(stack));
    }

    public ResourceLocation getStillTexture() {
        return stillTexture;
    }

    public ResourceLocation getFlowingTexture() {
        return flowingTexture;
    }

    public int getHeatValue() {
        return heat;
    }

    public boolean isHeatable() {
        return heatable;
    }

    public int getLightColour() {
        return lightColour;
    }

    public int getDarkColour() {
        return darkColour;
    }

    public boolean isFlammable() {
        return flammable;
    }
}
