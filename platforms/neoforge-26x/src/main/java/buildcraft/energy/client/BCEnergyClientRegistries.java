/*
 * Copyright (c) 2026 Joshua Kac -- NeoForge port (BuildCraft 10)
 *
 * This file is part of the BuildCraft 10 port and is distributed under the terms of the MIT License.
 * Please check the contents of the license, which should be located as "LICENSE.PORT" in the BuildCraft
 * source code distribution.
 */
package buildcraft.energy.client;

import net.minecraft.client.renderer.block.FluidModel;
import net.minecraft.client.resources.model.sprite.Material;
import net.minecraft.resources.Identifier;

import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterFluidModelsEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.client.fluid.FluidTintSources;

import buildcraft.lib.fluid.BCFluidType;

import buildcraft.energy.BCEnergyFluids;
import buildcraft.energy.BCEnergyFluids.BCFluid;

import buildcraft.energy.gui.GuiEngineIron;
import buildcraft.energy.gui.GuiEngineStone;

import buildcraft.BCCoreRegistries;
import buildcraft.BCEnergyRegistries;
import buildcraft.lib.engine.RenderTileEngine;

/**
 * Client-only menu screen and renderer registration for {@code buildcraft.energy}. Mirrors
 * {@code buildcraft.factory.client.BCFactoryClientRegistries} exactly -- see that class's own javadoc for why
 * this has to be gated at the *listener registration itself* (in {@code BuildCraft}'s constructor), never just
 * inside the method body: a dedicated server must never have a reason to resolve {@link GuiEngineStone} (a
 * client-only type) at all. {@link #registerRenderers} follows the identical rule for {@link RenderTileEngine}.
 */
public final class BCEnergyClientRegistries {

    private BCEnergyClientRegistries() {}

    public static void registerScreens(RegisterMenuScreensEvent event) {
        event.register(BCEnergyRegistries.ENGINE_STONE_MENU.get(), GuiEngineStone::new);
        event.register(BCEnergyRegistries.ENGINE_IRON_MENU.get(), GuiEngineIron::new);
    }

    /**
     * Registers {@link RenderTileEngine} for all four ported engines -- {@code ENGINE_WOOD}/{@code
     * ENGINE_CREATIVE} live in {@link BCCoreRegistries}, not here, since {@code buildcraft.core} owns those two
     * block/tile pairs (see {@code TileEngineWood}'s own javadoc); only {@code ENGINE_STONE} and {@code ENGINE_IRON}
     * are actually this module's own. Each registration supplies a different existing block texture -- see {@link RenderTileEngine}'s
     * own javadoc for why no new texture asset was authored for this pass.
     */
    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(
            BCCoreRegistries.ENGINE_WOOD_TYPE.get(),
            context -> new RenderTileEngine(context, Identifier.fromNamespaceAndPath("buildcraft", "block/engine_wood_side"))
        );
        event.registerBlockEntityRenderer(
            BCCoreRegistries.ENGINE_CREATIVE_TYPE.get(),
            context -> new RenderTileEngine(context, Identifier.fromNamespaceAndPath("buildcraft", "block/engine_creative_side"))
        );
        event.registerBlockEntityRenderer(
            BCEnergyRegistries.ENGINE_STONE_TYPE.get(),
            context -> new RenderTileEngine(context, Identifier.fromNamespaceAndPath("buildcraft", "block/engine_stone_side"))
        );
        event.registerBlockEntityRenderer(
            BCEnergyRegistries.ENGINE_IRON_TYPE.get(),
            context -> new RenderTileEngine(context, Identifier.fromNamespaceAndPath("buildcraft", "block/engine_iron_side"))
        );
    }

    /**
     * Gives every BuildCraft fluid a real {@link FluidModel} -- without this, all thirty would render (in the world,
     * in buckets, and in {@code RenderTileTank}/{@code GuiAutoCraftFluids}) with vanilla's missing-texture model.
     *
     * <p><b>How a mod supplies a fluid model on 26.x, confirmed against the real 26.3 sources, not assumed.</b>
     * Vanilla's {@code FluidStateModelSet#bake} hard-codes {@code FluidModel.Unbaked}s for water and lava only, then
     * hands the map to NeoForge's {@code ClientHooks#gatherFluidModels}, which posts this mod-bus
     * {@link RegisterFluidModelsEvent} (fired on a worker thread during model loading), and afterwards logs
     * {@code "Missing FluidModel for fluid '...'"} for every registered non-empty fluid still absent from the map;
     * {@code FluidStateModelSet#get} then falls back to {@code ModelBakery}'s missing model (the missing sprite for
     * still and flowing, no tint). There is no JSON asset path for fluid models on this target -- the event is the
     * mechanism, and it is exactly how NeoForge registers its own milk ({@code ClientNeoForgeMod
     * #onRegisterFluidModels}). The two-fluid {@code register} overload bakes one model and maps both the source and
     * the flowing fluid to it.
     *
     * <p>The tint source is a constant opaque white rather than {@code null}: the textures are already
     * recoloured (see {@code BCEnergyFluids}' javadoc), so no tint is wanted, but both of this port's generic fluid
     * consumers -- {@code RenderTileTank#extractRenderState} and {@code GuiAutoCraftFluids} -- call
     * {@code model.tintSource().color(...)} unguarded, so a {@code null} tint source (legal, and what vanilla lava
     * uses) would throw there. {@code FluidTintSources#constant(int)} answers the same colour for both the fluid-
     * and block-state overloads.
     *
     * <p>Materials are resolved against the block atlas; {@code FluidModel.Unbaked#bake} rejects any sprite from
     * another atlas as a missing reference. The textures sit under {@code textures/block/}, which vanilla's
     * {@code atlases/blocks.json} {@code directory} source ({@code "source": "block"}) stitches for every namespace.
     */
    public static void registerFluidModels(RegisterFluidModelsEvent event) {
        for (BCFluid fluid : BCEnergyFluids.allFluids) {
            BCFluidType type = fluid.getType().get();
            event.register(
                new FluidModel.Unbaked(
                    new Material(type.getStillTexture()),
                    new Material(type.getFlowingTexture()),
                    null,
                    FluidTintSources.constant(0xFFFFFFFF)),
                fluid.getSource(),
                fluid.getFlowing());
        }
    }
}
