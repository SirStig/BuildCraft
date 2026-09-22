/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * Copyright (c) 2026 Joshua Kac -- NeoForge port (BuildCraft 10)
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL
 * was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.lib.fluid;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.FluidType;

/**
 * The port of 1.12.2's {@code buildcraft.lib.fluid.BCFluid}. In 1.12.2 a Forge {@code Fluid} carried every
 * fluid-wide property itself (density, viscosity, temperature, textures, colour); on this target those properties
 * belong to NeoForge's {@link FluidType} instead, and the vanilla {@code Fluid} objects (a source and a flowing
 * instance, see {@code buildcraft.energy.BCEnergyFluids}) only point at it. So this is the class that picks up
 * {@code BCFluid}'s extra BuildCraft fields -- heat, the light/dark recolour pair, flammability -- and its one
 * behavioural override, the heat-suffixed display name.
 *
 * <p><b>Textures are recorded here but rendered elsewhere, differently per platform.</b> This is common (both
 * dist) code, so it only stores the still/flowing sprite ids. On 26.x {@code FluidType#initializeClient} and
 * {@code IClientFluidTypeExtensions}' texture/tint methods no longer exist (confirmed against the real 26.3
 * NeoForge sources jar); the ids are consumed by {@code buildcraft.energy.client.BCEnergyClientRegistries#
 * registerFluidModels} instead, which hands vanilla's {@code FluidStateModelSet} a real {@code FluidModel} for
 * every fluid through NeoForge's {@code RegisterFluidModelsEvent}. The 1.20.1 copy of this class still overrides
 * {@code initializeClient}.
 *
 * <p>1.12.2's {@code colour} ({@code getColor()}) was forced to plain white whenever {@code setColour(light, dark)}
 * was used -- which every BuildCraft Energy fluid does -- because the colour is baked into the recoloured sprite
 * itself rather than applied as a tint. That is preserved: see {@code BCEnergyFluids}' javadoc for how the
 * 1.12.2 stitch-time recolour ({@code AtlasSpriteFluid}) became static textures.
 */
public class BCFluidType extends FluidType {

    private final Identifier stillTexture;
    private final Identifier flowingTexture;
    private final int heat;
    private final boolean heatable;
    private final int lightColour;
    private final int darkColour;
    private final boolean flammable;

    public BCFluidType(
        Properties properties,
        Identifier stillTexture,
        Identifier flowingTexture,
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

    public Identifier getStillTexture() {
        return stillTexture;
    }

    public Identifier getFlowingTexture() {
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
