/*
 * Copyright (c) 2026 Joshua Kac -- NeoForge port (BuildCraft 10)
 *
 * This file is part of the BuildCraft 10 port and is distributed under the terms of the MIT License.
 * Please check the contents of the license, which should be located as "LICENSE.PORT" in the BuildCraft
 * source code distribution.
 */
package buildcraft;

import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.material.MapColor;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.DefaultDataComponentsBoundEvent;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.transfer.fluid.BucketResourceHandler;

import buildcraft.api.fuels.BuildcraftFuelRegistry;
import buildcraft.api.mj.MjCapabilities;

import buildcraft.lib.fluid.CoolantRegistry;
import buildcraft.lib.fluid.FuelRegistry;
import buildcraft.lib.registry.BCRegistry;

import buildcraft.energy.BCEnergyFluids;
import buildcraft.energy.BCEnergyRecipes;

import buildcraft.energy.block.BlockEngineIron;
import buildcraft.energy.block.BlockEngineStone;
import buildcraft.energy.container.ContainerEngineIron;
import buildcraft.energy.container.ContainerEngineStone;
import buildcraft.energy.tile.TileEngineIron;
import buildcraft.energy.tile.TileEngineStone;

/**
 * Registrations belonging to the old {@code buildcraftenergy} module -- the first ones, and the first module in
 * this port with no registration holder yet. Mirrors {@link BCFactoryRegistries}' exact structure.
 */
public final class BCEnergyRegistries {

    private BCEnergyRegistries() {}

    private static final BCRegistry REGISTRY = new BCRegistry(BuildCraft.MOD_ID);

    /** Properties match every other machine ported so far ({@code BCCoreRegistries#ENGINE_WOOD}/
     * {@code #ENGINE_CREATIVE}, {@code BCFactoryRegistries#CHUTE}, ...): hardness 5, resistance 10,
     * {@code SoundType.METAL} -- what {@code BlockBCTile_Neptune}'s constructor gave every 1.12.2 BuildCraft
     * block by default, and {@code TileEngineBase_BC8} never overrode any of them either. */
    public static final DeferredBlock<BlockEngineStone> ENGINE_STONE = REGISTRY.addBlockAndItem(
        "engine_stone", BlockEngineStone::new,
        properties -> properties
            .mapColor(MapColor.METAL)
            .strength(5.0F, 10.0F)
            .sound(SoundType.METAL)
            .requiresCorrectToolForDrops());

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<TileEngineStone>> ENGINE_STONE_TYPE =
        REGISTRY.addBlockEntity("engine_stone", TileEngineStone::new, ENGINE_STONE);

    /** See {@code BCRegistry#addMenu}'s own javadoc for why this exists, and
     * {@code buildcraft.energy.client.BCEnergyClientRegistries} for the separate client-only screen registration
     * this pairs with. */
    public static final DeferredHolder<MenuType<?>, MenuType<ContainerEngineStone>> ENGINE_STONE_MENU =
        REGISTRY.addMenu("engine_stone", ContainerEngineStone::new);

    /** The Combustion Engine -- 1.12.2's {@code IRON} engine type, registered right after the Stirling engine as
     * 1.12.2's {@code BCEnergyBlocks} did. Same block properties as {@link #ENGINE_STONE}. */
    public static final DeferredBlock<BlockEngineIron> ENGINE_IRON = REGISTRY.addBlockAndItem(
        "engine_iron", BlockEngineIron::new,
        properties -> properties
            .mapColor(MapColor.METAL)
            .strength(5.0F, 10.0F)
            .sound(SoundType.METAL)
            .requiresCorrectToolForDrops());

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<TileEngineIron>> ENGINE_IRON_TYPE =
        REGISTRY.addBlockEntity("engine_iron", TileEngineIron::new, ENGINE_IRON);

    public static final DeferredHolder<MenuType<?>, MenuType<ContainerEngineIron>> ENGINE_IRON_MENU =
        REGISTRY.addMenu("engine_iron", ContainerEngineIron::new);

    /* The oil/fuel fluid family: a fluid type, source + flowing fluid, placeable block and bucket for each of the
     * thirty, all defined in BCEnergyFluids. Called here, after the engines, so the buckets follow them in the
     * creative tab. */
    static {
        BCEnergyFluids.preInit(REGISTRY);
    }

    public static void register(IEventBus modBus) {
        REGISTRY.register(modBus);
        BCEnergyFluids.register(modBus);
        // 1.12.2 installed these in BCLibRegistries#preInit; energy is the only module that fills them.
        BuildcraftFuelRegistry.fuel = FuelRegistry.INSTANCE;
        BuildcraftFuelRegistry.coolant = CoolantRegistry.INSTANCE;
        NeoForge.EVENT_BUS.addListener(BCEnergyRegistries::onDefaultComponentsBound);
        modBus.addListener(BCEnergyRegistries::registerCapabilities);
    }

    /**
     * 1.12.2 filled the fuel/coolant registries in {@code FMLInitializationEvent}. The 26.x equivalent,
     * {@code FMLCommonSetupEvent}, is too early here -- found live, not by reading: the first dedicated-server boot
     * with this wired to common setup crashed mod loading with {@code NullPointerException: Components not bound
     * yet}, thrown from {@code Holder.Reference#components} inside {@code FluidResource.of(Fluids.WATER)} (via
     * {@code Fluid#computeDefaultResource} -> {@code new FluidStack}). On this target even a plain fluid's default
     * {@code FluidResource}/{@code FluidStack}/{@code ItemStack} needs its holder's default data components, and
     * those are only bound by {@code ReloadableServerResources#updateComponentsAndStaticRegistryTags} during
     * world/datapack load (or from the registry-sync packet on a remote client), which then posts this game-bus
     * {@link DefaultDataComponentsBoundEvent}. The registry contents never change, so it is filled exactly once, on
     * the first such event, from whichever side sees it first (both hold the same static, immutable values).
     * 1.20.1 has no data components at all and keeps using common setup.
     */
    private static boolean fuelsRegistered = false;

    private static synchronized void onDefaultComponentsBound(DefaultDataComponentsBoundEvent event) {
        if (!fuelsRegistered) {
            fuelsRegistered = true;
            BCEnergyRecipes.init();
        }
    }

    /**
     * The fuel slot is reachable from every {@code EnumPipePart} it registered with ({@code EnumAccess.BOTH,
     * EnumPipePart.VALUES} -- see {@link TileEngineStone}), so this delegates to
     * {@code itemManager.getHandlerForFace} unconditionally, exactly like {@code BCFactoryRegistries}' own
     * {@code CHUTE}/{@code AUTO_WORKBENCH_ITEMS} entries. The MJ connector is guarded to a single facing, matching
     * {@code BCCoreRegistries}' {@code ENGINE_WOOD}/{@code ENGINE_CREATIVE} entries -- see that class's own
     * reasoning for the guard.
     */
    private static void registerCapabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(Capabilities.Item.BLOCK, ENGINE_STONE_TYPE.get(),
            (tile, side) -> tile.itemManager.getHandlerForFace(side));
        event.registerBlockEntity(MjCapabilities.CONNECTOR, ENGINE_STONE_TYPE.get(),
            (tile, side) -> side == tile.getCurrentFacing() ? tile.mjConnector : null);

        // The Combustion Engine's fluid handler (fill fuel/coolant, drain residue) was 1.12.2's
        // addCapabilityInstance(CAP_FLUIDS, fluidHandler, EnumPipePart.VALUES): every face and the null side alike.
        event.registerBlockEntity(Capabilities.Fluid.BLOCK, ENGINE_IRON_TYPE.get(), (tile, side) -> tile.fluidHandler);
        event.registerBlockEntity(MjCapabilities.CONNECTOR, ENGINE_IRON_TYPE.get(),
            (tile, side) -> side == tile.getCurrentFacing() ? tile.mjConnector : null);

        // Found live while testing the Combustion Engine: NeoForge's CapabilityHooks gives Capabilities.Fluid.ITEM
        // (a BucketResourceHandler) only to items whose class is exactly BucketItem, so none of the thirty
        // BCFluidBucketItem subclasses had a fluid capability at all -- a full oil or fuel bucket could not be
        // emptied into the engine or any other tank. BucketResourceHandler itself works for any BucketItem (it reads
        // BucketItem#content, and turns an emptied bucket back into Items.BUCKET), so it is registered here for ours.
        for (BCEnergyFluids.BCFluid fluid : BCEnergyFluids.allFluids) {
            event.registerItem(Capabilities.Fluid.ITEM, (stack, access) -> new BucketResourceHandler(access),
                fluid.getBucket().get());
        }
    }
}
