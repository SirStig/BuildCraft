/*
 * Copyright (c) 2026 Joshua Kac -- NeoForge port (BuildCraft 10)
 *
 * This file is part of the BuildCraft 10 port and is distributed under the terms of the MIT License.
 * Please check the contents of the license, which should be located as "LICENSE.PORT" in the BuildCraft
 * source code distribution.
 */
package buildcraft;

import org.jetbrains.annotations.Nullable;

import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;

import net.minecraftforge.common.capabilities.RegisterCapabilitiesEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.RegistryObject;

import buildcraft.api.core.BuildCraftAPI;
import buildcraft.api.core.EnumHandlerPriority;
import buildcraft.api.mj.MjAPI;
import buildcraft.api.transport.IInjectable;
import buildcraft.api.transport.pipe.IItemPipe;
import buildcraft.api.transport.pipe.IPipe;
import buildcraft.api.transport.pipe.IPipeHolder;
import buildcraft.api.transport.pipe.PipeApi;
import buildcraft.api.transport.pipe.PipeDefinition;
import buildcraft.api.transport.pipe.PipeFlowType;
import buildcraft.api.transport.pluggable.PipePluggable;

import buildcraft.lib.misc.FakePlayerProvider;
import buildcraft.lib.registry.BCRegistry;

import buildcraft.transport.block.BlockPipeHolder;
import buildcraft.transport.container.ContainerDiamondPipe;
import buildcraft.transport.container.ContainerDiamondWoodPipe;
import buildcraft.transport.item.ItemPipeHolder;
import buildcraft.transport.pipe.PipeExtensionManager;
import buildcraft.transport.pipe.PipeRegistry;
import buildcraft.transport.pipe.StripesRegistry;
import buildcraft.transport.pipe.behaviour.PipeBehaviourClay;
import buildcraft.transport.pipe.behaviour.PipeBehaviourCobble;
import buildcraft.transport.pipe.behaviour.PipeBehaviourDaizuli;
import buildcraft.transport.pipe.behaviour.PipeBehaviourDiamondFluid;
import buildcraft.transport.pipe.behaviour.PipeBehaviourDiamondItem;
import buildcraft.transport.pipe.behaviour.PipeBehaviourEmzuli;
import buildcraft.transport.pipe.behaviour.PipeBehaviourGold;
import buildcraft.transport.pipe.behaviour.PipeBehaviourIron;
import buildcraft.transport.pipe.behaviour.PipeBehaviourLapis;
import buildcraft.transport.pipe.behaviour.PipeBehaviourLimiter;
import buildcraft.transport.pipe.behaviour.PipeBehaviourObsidian;
import buildcraft.transport.pipe.behaviour.PipeBehaviourQuartz;
import buildcraft.transport.pipe.behaviour.PipeBehaviourSandstone;
import buildcraft.transport.pipe.behaviour.PipeBehaviourStone;
import buildcraft.transport.pipe.behaviour.PipeBehaviourStripes;
import buildcraft.transport.pipe.behaviour.PipeBehaviourVoid;
import buildcraft.transport.pipe.behaviour.PipeBehaviourWood;
import buildcraft.transport.pipe.behaviour.PipeBehaviourWoodDiamond;
import buildcraft.transport.pipe.behaviour.PipeBehaviourWoodPower;
import buildcraft.transport.pipe.flow.PipeFlowFluids;
import buildcraft.transport.pipe.flow.PipeFlowItems;
import buildcraft.transport.pipe.flow.PipeFlowPower;
import buildcraft.transport.stripes.StripesHandlerEntityInteract;
import buildcraft.transport.stripes.StripesHandlerHoe;
import buildcraft.transport.stripes.StripesHandlerMinecartDestroy;
import buildcraft.transport.stripes.StripesHandlerPipes;
import buildcraft.transport.stripes.StripesHandlerPlaceBlock;
import buildcraft.transport.stripes.StripesHandlerPlant;
import buildcraft.transport.stripes.StripesHandlerUse;
import buildcraft.transport.tile.TilePipeHolder;

/**
 * Registrations belonging to the old {@code buildcrafttransport} module -- the first ones, and the first pipe
 * batch. Mirrors {@code BCFactoryRegistries}/{@code BCEnergyRegistries}' exact established structure. See the
 * 26.x copy of this class for the full account of the one-block-many-items architecture, why
 * {@code canBeColoured} is {@code false} despite the real 1.12.2 cobblestone pipe being colourable, and why the
 * id/texture-prefix naming deliberately diverges from 1.12.2's own {@code "cobblestone_item"} -- unchanged here.
 *
 * <p>Unlike 26.x, 1.20.1 still has {@code ICapabilityProvider}, so {@code TilePipeHolder} exposes its own
 * capabilities directly (see that class's own javadoc) rather than through a
 * {@code RegisterCapabilitiesEvent}-registered lambda here. What this target still needs declaring, because
 * 1.20.1 lost {@code @CapabilityInject}, is the four {@link PipeApi} capability tokens themselves
 * ({@link #registerCapabilities}) -- matching {@code MjCapabilities}/{@code TilesAPI}'s own precedent.
 */
public final class BCTransportRegistries {

    private BCTransportRegistries() {}

    private static final BCRegistry REGISTRY = new BCRegistry(BuildCraft.MOD_ID);

    static {
        PipeApi.pipeRegistry = PipeRegistry.INSTANCE;
        // Wires the pluggable registry -- see the 26.x copy's own static block for why this is needed
        // (TilePipeHolder#load looks up a PluggableDefinition by id).
        PipeApi.pluggableRegistry = buildcraft.lib.registry.PluggableRegistry.INSTANCE;
        PipeApi.flowItems = new PipeFlowType(PipeFlowItems::new, PipeFlowItems::new);
        PipeApi.flowFluids = new PipeFlowType(PipeFlowFluids::new, PipeFlowFluids::new);
        PipeApi.flowPower = new PipeFlowType(PipeFlowPower::new, PipeFlowPower::new);
        // The stripes pipe's own dispatcher/extension-manager -- see StripesRegistry/PipeExtensionManager's own
        // javadoc (26.x copy; identical on this target).
        PipeApi.stripeRegistry = StripesRegistry.INSTANCE;
        PipeApi.extensionManager = PipeExtensionManager.INSTANCE;
    }

    /** The cobblestone pipe's own {@link PipeDefinition} -- see the 26.x copy of this class's own javadoc for
     * the {@code canBeColoured}/naming notes. */
    public static final PipeDefinition PIPE_COBBLESTONE = new PipeDefinition.PipeDefinitionBuilder()
        .idTexPrefix("cobblestone")
        .logic(PipeBehaviourCobble::new, PipeBehaviourCobble::new)
        .flowItem()
        .disableColouring()
        .define();

    /** The wooden pipe's own {@link PipeDefinition} -- see the 26.x copy of this class's own javadoc for the
     * full account of why no new block/tile code was needed for this, and why {@code canBeColoured} is
     * {@code false}. */
    public static final PipeDefinition PIPE_WOOD = new PipeDefinition.PipeDefinitionBuilder()
        .idTexPrefix("wood")
        .logic(PipeBehaviourWood::new, PipeBehaviourWood::new)
        .flowItem()
        .disableColouring()
        .define();

    /** The stone pipe's own {@link PipeDefinition} -- see the 26.x copy of this class's own javadoc for the
     * full account of the speed-modifier constants and why {@code canBeColoured} is {@code false}. */
    public static final PipeDefinition PIPE_STONE = new PipeDefinition.PipeDefinitionBuilder()
        .idTexPrefix("stone")
        .logic(PipeBehaviourStone::new, PipeBehaviourStone::new)
        .flowItem()
        .disableColouring()
        .define();

    /** The sandstone pipe's own {@link PipeDefinition} -- the pipe-to-pipe-only, never-to-an-inventory
     * material (see {@link PipeBehaviourSandstone}'s own javadoc). */
    public static final PipeDefinition PIPE_SANDSTONE = new PipeDefinition.PipeDefinitionBuilder()
        .idTexPrefix("sandstone")
        .logic(PipeBehaviourSandstone::new, PipeBehaviourSandstone::new)
        .flowItem()
        .disableColouring()
        .define();

    /** The quartz pipe's own {@link PipeDefinition} -- the gentlest speed modifier of this batch. */
    public static final PipeDefinition PIPE_QUARTZ = new PipeDefinition.PipeDefinitionBuilder()
        .idTexPrefix("quartz")
        .logic(PipeBehaviourQuartz::new, PipeBehaviourQuartz::new)
        .flowItem()
        .disableColouring()
        .define();

    /** The golden pipe's own {@link PipeDefinition} -- the speed-boost material. See the 26.x copy of this class for the
     * real 1.12.2 builder call this mirrors and why {@code canBeColoured} is {@code false}. */
    public static final PipeDefinition PIPE_GOLD = new PipeDefinition.PipeDefinitionBuilder()
        .idTexPrefix("gold")
        .logic(PipeBehaviourGold::new, PipeBehaviourGold::new)
        .flowItem()
        .disableColouring()
        .define();

    /** The iron pipe's own {@link PipeDefinition} -- the one-way, wrench-selected output valve. See the 26.x copy of this class for the
     * real 1.12.2 builder call this mirrors and why {@code canBeColoured} is {@code false}. */
    public static final PipeDefinition PIPE_IRON = new PipeDefinition.PipeDefinitionBuilder()
        .idTexPrefix("iron")
        .logic(PipeBehaviourIron::new, PipeBehaviourIron::new)
        .flowItem()
        .disableColouring()
        .define();

    /** The clay pipe's own {@link PipeDefinition} -- prefers inventories over pipes. See the 26.x copy of this class for the
     * real 1.12.2 builder call this mirrors and why {@code canBeColoured} is {@code false}. */
    public static final PipeDefinition PIPE_CLAY = new PipeDefinition.PipeDefinitionBuilder()
        .idTexPrefix("clay")
        .logic(PipeBehaviourClay::new, PipeBehaviourClay::new)
        .flowItem()
        .disableColouring()
        .define();

    /** The void pipe's own {@link PipeDefinition} -- destroys items. See the 26.x copy of this class for the
     * real 1.12.2 builder call this mirrors and why {@code canBeColoured} is {@code false}. */
    public static final PipeDefinition PIPE_VOID = new PipeDefinition.PipeDefinitionBuilder()
        .idTexPrefix("void")
        .logic(PipeBehaviourVoid::new, PipeBehaviourVoid::new)
        .flowItem()
        .disableColouring()
        .define();

    /** The obsidian pipe's own {@link PipeDefinition} -- an MJ-powered magnet, not explosive (see the 26.x copy
     * of this class's own javadoc). Item-only, matching 1.12.2's real registration (its {@code obsidian_fluid}
     * sibling is commented out upstream). */
    public static final PipeDefinition PIPE_OBSIDIAN = new PipeDefinition.PipeDefinitionBuilder()
        .idTexPrefix("obsidian")
        .logic(PipeBehaviourObsidian::new, PipeBehaviourObsidian::new)
        .flowItem()
        .disableColouring()
        .define();

    /** The lapis pipe's own {@link PipeDefinition} -- paints items with a colour for a diamond pipe further down
     * the line to sort on (see {@link PipeBehaviourLapis}'s own javadoc). Item-only. */
    public static final PipeDefinition PIPE_LAPIS = new PipeDefinition.PipeDefinitionBuilder()
        .idTexPrefix("lapis")
        .logic(PipeBehaviourLapis::new, PipeBehaviourLapis::new)
        .flowItem()
        .disableColouring()
        .define();

    /** The daizuli pipe's own {@link PipeDefinition} -- a directional colour filter, BuildCraft 8-specific (see
     * {@link PipeBehaviourDaizuli}'s own javadoc). Item-only. */
    public static final PipeDefinition PIPE_DAIZULI = new PipeDefinition.PipeDefinitionBuilder()
        .idTexPrefix("daizuli")
        .logic(PipeBehaviourDaizuli::new, PipeBehaviourDaizuli::new)
        .flowItem()
        .disableColouring()
        .define();

    /** The emzuli pipe's own {@link PipeDefinition} -- a four-preset extraction wooden pipe, BuildCraft
     * 8-specific (see {@link PipeBehaviourEmzuli}'s own javadoc). Item-only. */
    public static final PipeDefinition PIPE_EMZULI = new PipeDefinition.PipeDefinitionBuilder()
        .idTexPrefix("emzuli")
        .logic(PipeBehaviourEmzuli::new, PipeBehaviourEmzuli::new)
        .flowItem()
        .disableColouring()
        .define();

    /** The stripes pipe's own {@link PipeDefinition} -- a real, distinct pipe material (see
     * {@link PipeBehaviourStripes}'s own javadoc), the extraction pipe that mines/interacts with the world ahead
     * of its open face. Item-only. */
    public static final PipeDefinition PIPE_STRIPES = new PipeDefinition.PipeDefinitionBuilder()
        .idTexPrefix("stripes")
        .logic(PipeBehaviourStripes::new, PipeBehaviourStripes::new)
        .flowItem()
        .disableColouring()
        .define();

    /** The diamond pipe's own {@link PipeDefinition} -- the item-sorting/filtering material. See the 26.x copy
     * of this class for the full account of why a single {@code idTexPrefix("diamond")} is enough despite
     * 1.12.2's own eight per-face texture suffixes. */
    public static final PipeDefinition PIPE_DIAMOND = new PipeDefinition.PipeDefinitionBuilder()
        .idTexPrefix("diamond")
        .logic(PipeBehaviourDiamondItem::new, PipeBehaviourDiamondItem::new)
        .flowItem()
        .disableColouring()
        .define();

    /** The wood/diamond combo pipe's own {@link PipeDefinition} -- a filtered wooden pipe. See the 26.x copy of
     * this class for why the id is the bare {@code "diamond_wood"} rather than 1.12.2's own
     * {@code "diamond_wood_item"}. */
    public static final PipeDefinition PIPE_DIAMOND_WOOD = new PipeDefinition.PipeDefinitionBuilder()
        .idTexPrefix("diamond_wood")
        .logic(PipeBehaviourWoodDiamond::new, PipeBehaviourWoodDiamond::new)
        .flowItem()
        .disableColouring()
        .define();

    // The fluid pipes -- see the 26.x copy of this class for the 1.12.2 builder calls these mirror. No capability
    // registration is needed on this target: TilePipeHolder#getCapability already falls through to
    // PipeFlowFluids#getCapability, which answers FLUID_HANDLER.

    public static final PipeDefinition PIPE_COBBLESTONE_FLUID =
        fluidPipe("cobblestone_fluid", PipeBehaviourCobble::new, PipeBehaviourCobble::new);
    public static final PipeDefinition PIPE_WOOD_FLUID =
        fluidPipe("wood_fluid", PipeBehaviourWood::new, PipeBehaviourWood::new);
    public static final PipeDefinition PIPE_STONE_FLUID =
        fluidPipe("stone_fluid", PipeBehaviourStone::new, PipeBehaviourStone::new);
    public static final PipeDefinition PIPE_SANDSTONE_FLUID =
        fluidPipe("sandstone_fluid", PipeBehaviourSandstone::new, PipeBehaviourSandstone::new);
    public static final PipeDefinition PIPE_QUARTZ_FLUID =
        fluidPipe("quartz_fluid", PipeBehaviourQuartz::new, PipeBehaviourQuartz::new);
    public static final PipeDefinition PIPE_GOLD_FLUID =
        fluidPipe("gold_fluid", PipeBehaviourGold::new, PipeBehaviourGold::new);
    public static final PipeDefinition PIPE_IRON_FLUID =
        fluidPipe("iron_fluid", PipeBehaviourIron::new, PipeBehaviourIron::new);
    public static final PipeDefinition PIPE_CLAY_FLUID =
        fluidPipe("clay_fluid", PipeBehaviourClay::new, PipeBehaviourClay::new);
    public static final PipeDefinition PIPE_VOID_FLUID =
        fluidPipe("void_fluid", PipeBehaviourVoid::new, PipeBehaviourVoid::new);
    public static final PipeDefinition PIPE_DIAMOND_FLUID =
        fluidPipe("diamond_fluid", PipeBehaviourDiamondFluid::new, PipeBehaviourDiamondFluid::new);
    public static final PipeDefinition PIPE_DIAMOND_WOOD_FLUID =
        fluidPipe("diamond_wood_fluid", PipeBehaviourWoodDiamond::new, PipeBehaviourWoodDiamond::new);

    /** 1.12.2's {@code BCTransportConfig#baseFlowRate} default (mB per tick). */
    private static final int BASE_FLOW_RATE = 10;

    /** 1.12.2's {@code BCTransportConfig} {@code fluidTransfer(...)} calls, verbatim -- see the 26.x copy. */
    static {
        fluidTransfer(PIPE_COBBLESTONE_FLUID, BASE_FLOW_RATE, 10);
        fluidTransfer(PIPE_WOOD_FLUID, BASE_FLOW_RATE, 10);
        fluidTransfer(PIPE_STONE_FLUID, BASE_FLOW_RATE * 2, 10);
        fluidTransfer(PIPE_SANDSTONE_FLUID, BASE_FLOW_RATE * 2, 10);
        fluidTransfer(PIPE_CLAY_FLUID, BASE_FLOW_RATE * 4, 10);
        fluidTransfer(PIPE_IRON_FLUID, BASE_FLOW_RATE * 4, 10);
        fluidTransfer(PIPE_QUARTZ_FLUID, BASE_FLOW_RATE * 4, 10);
        fluidTransfer(PIPE_GOLD_FLUID, BASE_FLOW_RATE * 8, 2);
        fluidTransfer(PIPE_VOID_FLUID, BASE_FLOW_RATE * 8, 10);
        fluidTransfer(PIPE_DIAMOND_FLUID, BASE_FLOW_RATE * 8, 10);
        fluidTransfer(PIPE_DIAMOND_WOOD_FLUID, BASE_FLOW_RATE * 8, 10);
    }

    private static PipeDefinition fluidPipe(
        String id, PipeDefinition.IPipeCreator creator, PipeDefinition.IPipeLoader loader
    ) {
        return new PipeDefinition.PipeDefinitionBuilder()
            .idTexPrefix(id)
            .logic(creator, loader)
            .flowFluid()
            .disableColouring()
            .define();
    }

    private static void fluidTransfer(PipeDefinition def, int rate, int delay) {
        PipeApi.fluidTransferData.put(def, new PipeApi.FluidTransferInfo(rate, delay));
    }

    // The power (kinesis) pipes -- see the 26.x copy of this class's own javadoc for the full account.

    public static final PipeDefinition PIPE_COBBLESTONE_POWER =
        powerPipe("pipe_power_cobblestone", PipeBehaviourCobble::new, PipeBehaviourCobble::new);
    public static final PipeDefinition PIPE_WOOD_POWER =
        powerPipe("pipe_power_wood", PipeBehaviourWoodPower::new, PipeBehaviourWoodPower::new);
    public static final PipeDefinition PIPE_STONE_POWER =
        powerPipe("pipe_power_stone", PipeBehaviourStone::new, PipeBehaviourStone::new);
    public static final PipeDefinition PIPE_SANDSTONE_POWER =
        powerPipe("pipe_power_sandstone", PipeBehaviourSandstone::new, PipeBehaviourSandstone::new);
    public static final PipeDefinition PIPE_QUARTZ_POWER =
        powerPipe("pipe_power_quartz", PipeBehaviourQuartz::new, PipeBehaviourQuartz::new);
    public static final PipeDefinition PIPE_GOLD_POWER =
        powerPipe("pipe_power_gold", PipeBehaviourGold::new, PipeBehaviourGold::new);

    /** The iron/diamond power pipes' own {@link PipeDefinition}s -- {@link PipeBehaviourLimiter}, 1.12.2's real
     * behaviour swap for exactly these two materials. See the 26.x copy of this field's own javadoc. */
    public static final PipeDefinition PIPE_IRON_POWER =
        powerPipe("pipe_power_iron", PipeBehaviourLimiter::new, PipeBehaviourLimiter::new);
    public static final PipeDefinition PIPE_DIAMOND_POWER =
        powerPipe("pipe_power_diamond", PipeBehaviourLimiter::new, PipeBehaviourLimiter::new);

    /** The diamond_wood power pipe's own {@link PipeDefinition}. <b>Not {@link PipeBehaviourLimiter}</b> -- see
     * the 26.x copy of this field's own javadoc for why (1.12.2's real {@code BCTransportPipes#preInit} switches
     * {@code diaWoodPower} to plain {@code PipeBehaviourWoodPower}, never to the limiter). */
    public static final PipeDefinition PIPE_DIAMOND_WOOD_POWER =
        powerPipe("pipe_power_diamond_wood", PipeBehaviourWoodPower::new, PipeBehaviourWoodPower::new);

    /** See the 26.x copy of this field's own javadoc for the 1.12.2 {@code BCTransportConfig.basePowerRate}
     * arithmetic these constants come from. */
    private static final int BASE_POWER_RATE = 4;

    static {
        powerTransfer(PIPE_COBBLESTONE_POWER, BASE_POWER_RATE, 16, false);
        powerTransfer(PIPE_STONE_POWER, BASE_POWER_RATE * 2, 32, false);
        powerTransfer(PIPE_WOOD_POWER, BASE_POWER_RATE * 4, 128, true);
        powerTransfer(PIPE_SANDSTONE_POWER, BASE_POWER_RATE * 4, 32, false);
        powerTransfer(PIPE_QUARTZ_POWER, BASE_POWER_RATE * 8, 32, false);
        powerTransfer(PIPE_IRON_POWER, BASE_POWER_RATE * 8, 32, false);
        powerTransfer(PIPE_GOLD_POWER, BASE_POWER_RATE * 32, 32, false);
        powerTransfer(PIPE_DIAMOND_POWER, BASE_POWER_RATE * 64, 32, false);
        powerTransfer(PIPE_DIAMOND_WOOD_POWER, BASE_POWER_RATE * 64, 32, true);
    }

    /** {@code PipeDefinition.textures} has zero readers anywhere in this port -- see the 26.x copy of this
     * method's own javadoc. */
    private static PipeDefinition powerPipe(
        String id, PipeDefinition.IPipeCreator creator, PipeDefinition.IPipeLoader loader
    ) {
        return new PipeDefinition.PipeDefinitionBuilder()
            .idTex(id)
            .logic(creator, loader)
            .flowPower()
            .disableColouring()
            .define();
    }

    private static void powerTransfer(PipeDefinition def, int transferMultiplier, int resistanceDivisor, boolean recv) {
        long transfer = MjAPI.MJ * transferMultiplier;
        long resistance = MjAPI.MJ / resistanceDivisor;
        PipeApi.powerTransferData.put(def, PipeApi.PowerTransferInfo.createFromResistance(transfer, resistance, recv));
    }

    /** Default properties match what {@code BlockBCTile_Neptune}'s constructor gave every 1.12.2 BuildCraft
     * block -- see {@code BCFactoryRegistries#CHUTE}'s own javadoc. A plain full cube, matching
     * {@code BlockTank}/{@code BlockPump}'s own "no renderer yet" precedent. No {@code addBlockAndItem}: this
     * block's item is the pipe-specific {@link ItemPipeHolder} below. */
    public static final RegistryObject<BlockPipeHolder> PIPE_HOLDER = REGISTRY.addBlock(
        "pipe_holder",
        () -> new BlockPipeHolder(
            BlockBehaviour.Properties.of()
                .mapColor(MapColor.STONE)
                .strength(0.25F, 3.0F)
                .sound(SoundType.STONE)
                .noOcclusion()
                .requiresCorrectToolForDrops()
        )
    );

    public static final RegistryObject<BlockEntityType<TilePipeHolder>> PIPE_HOLDER_TYPE =
        REGISTRY.addBlockEntity("pipe_holder", TilePipeHolder::new, PIPE_HOLDER);

    /** The one item registered in this batch: the cobblestone pipe's own placeable item, tagged with
     * {@link #PIPE_COBBLESTONE}. Referencing {@code PIPE_HOLDER.get()} inside this factory is safe for the same
     * reason it is on 26.x -- see that class's own javadoc. */
    public static final RegistryObject<ItemPipeHolder> PIPE_ITEM_COBBLESTONE = REGISTRY.addItem(
        "pipe_item_cobblestone",
        () -> new ItemPipeHolder(PIPE_HOLDER.get(), new Item.Properties(), PIPE_COBBLESTONE)
    );

    /** The wooden pipe's own placeable item, tagged with {@link #PIPE_WOOD}. No capability-registration changes
     * were needed for this on 1.20.1 -- {@code TilePipeHolder#getCapability} already falls through generically
     * to {@code pipe.getCapability}, which is how {@code PipeBehaviourWood}'s own {@code MjCapabilityHelper}
     * gets reached with zero changes here. */
    public static final RegistryObject<ItemPipeHolder> PIPE_ITEM_WOOD = REGISTRY.addItem(
        "pipe_item_wood",
        () -> new ItemPipeHolder(PIPE_HOLDER.get(), new Item.Properties(), PIPE_WOOD)
    );

    /** The stone pipe's own placeable item, tagged with {@link #PIPE_STONE}. */
    public static final RegistryObject<ItemPipeHolder> PIPE_ITEM_STONE = REGISTRY.addItem(
        "pipe_item_stone",
        () -> new ItemPipeHolder(PIPE_HOLDER.get(), new Item.Properties(), PIPE_STONE)
    );

    /** The sandstone pipe's own placeable item, tagged with {@link #PIPE_SANDSTONE}. */
    public static final RegistryObject<ItemPipeHolder> PIPE_ITEM_SANDSTONE = REGISTRY.addItem(
        "pipe_item_sandstone",
        () -> new ItemPipeHolder(PIPE_HOLDER.get(), new Item.Properties(), PIPE_SANDSTONE)
    );

    /** The quartz pipe's own placeable item, tagged with {@link #PIPE_QUARTZ}. */
    public static final RegistryObject<ItemPipeHolder> PIPE_ITEM_QUARTZ = REGISTRY.addItem(
        "pipe_item_quartz",
        () -> new ItemPipeHolder(PIPE_HOLDER.get(), new Item.Properties(), PIPE_QUARTZ)
    );

    /** The golden pipe's own placeable item, tagged with {@link #PIPE_GOLD}. */
    public static final RegistryObject<ItemPipeHolder> PIPE_ITEM_GOLD = REGISTRY.addItem(
        "pipe_item_gold",
        () -> new ItemPipeHolder(PIPE_HOLDER.get(), new Item.Properties(), PIPE_GOLD)
    );

    /** The iron pipe's own placeable item, tagged with {@link #PIPE_IRON}. */
    public static final RegistryObject<ItemPipeHolder> PIPE_ITEM_IRON = REGISTRY.addItem(
        "pipe_item_iron",
        () -> new ItemPipeHolder(PIPE_HOLDER.get(), new Item.Properties(), PIPE_IRON)
    );

    /** The clay pipe's own placeable item, tagged with {@link #PIPE_CLAY}. */
    public static final RegistryObject<ItemPipeHolder> PIPE_ITEM_CLAY = REGISTRY.addItem(
        "pipe_item_clay",
        () -> new ItemPipeHolder(PIPE_HOLDER.get(), new Item.Properties(), PIPE_CLAY)
    );

    /** The void pipe's own placeable item, tagged with {@link #PIPE_VOID}. */
    public static final RegistryObject<ItemPipeHolder> PIPE_ITEM_VOID = REGISTRY.addItem(
        "pipe_item_void",
        () -> new ItemPipeHolder(PIPE_HOLDER.get(), new Item.Properties(), PIPE_VOID)
    );

    /** The obsidian pipe's own placeable item, tagged with {@link #PIPE_OBSIDIAN}. */
    public static final RegistryObject<ItemPipeHolder> PIPE_ITEM_OBSIDIAN = REGISTRY.addItem(
        "pipe_item_obsidian",
        () -> new ItemPipeHolder(PIPE_HOLDER.get(), new Item.Properties(), PIPE_OBSIDIAN)
    );

    /** The lapis pipe's own placeable item, tagged with {@link #PIPE_LAPIS}. */
    public static final RegistryObject<ItemPipeHolder> PIPE_ITEM_LAPIS = REGISTRY.addItem(
        "pipe_item_lapis",
        () -> new ItemPipeHolder(PIPE_HOLDER.get(), new Item.Properties(), PIPE_LAPIS)
    );

    /** The daizuli pipe's own placeable item, tagged with {@link #PIPE_DAIZULI}. */
    public static final RegistryObject<ItemPipeHolder> PIPE_ITEM_DAIZULI = REGISTRY.addItem(
        "pipe_item_daizuli",
        () -> new ItemPipeHolder(PIPE_HOLDER.get(), new Item.Properties(), PIPE_DAIZULI)
    );

    /** The emzuli pipe's own placeable item, tagged with {@link #PIPE_EMZULI}. */
    public static final RegistryObject<ItemPipeHolder> PIPE_ITEM_EMZULI = REGISTRY.addItem(
        "pipe_item_emzuli",
        () -> new ItemPipeHolder(PIPE_HOLDER.get(), new Item.Properties(), PIPE_EMZULI)
    );

    /** The stripes pipe's own placeable item, tagged with {@link #PIPE_STRIPES}. */
    public static final RegistryObject<ItemPipeHolder> PIPE_ITEM_STRIPES = REGISTRY.addItem(
        "pipe_item_stripes",
        () -> new ItemPipeHolder(PIPE_HOLDER.get(), new Item.Properties(), PIPE_STRIPES)
    );

    /** The diamond pipe's own placeable item, tagged with {@link #PIPE_DIAMOND}. */
    public static final RegistryObject<ItemPipeHolder> PIPE_ITEM_DIAMOND = REGISTRY.addItem(
        "pipe_item_diamond",
        () -> new ItemPipeHolder(PIPE_HOLDER.get(), new Item.Properties(), PIPE_DIAMOND)
    );

    /** The wood/diamond combo pipe's own placeable item, tagged with {@link #PIPE_DIAMOND_WOOD}. */
    public static final RegistryObject<ItemPipeHolder> PIPE_ITEM_DIAMOND_WOOD = REGISTRY.addItem(
        "pipe_item_diamond_wood",
        () -> new ItemPipeHolder(PIPE_HOLDER.get(), new Item.Properties(), PIPE_DIAMOND_WOOD)
    );

    // The fluid pipes' placeable items -- "pipe_fluid_<material>", alongside the "pipe_item_<material>" ones.

    public static final RegistryObject<ItemPipeHolder> PIPE_FLUID_COBBLESTONE = REGISTRY.addItem(
        "pipe_fluid_cobblestone",
        () -> new ItemPipeHolder(PIPE_HOLDER.get(), new Item.Properties(), PIPE_COBBLESTONE_FLUID)
    );

    public static final RegistryObject<ItemPipeHolder> PIPE_FLUID_WOOD = REGISTRY.addItem(
        "pipe_fluid_wood",
        () -> new ItemPipeHolder(PIPE_HOLDER.get(), new Item.Properties(), PIPE_WOOD_FLUID)
    );

    public static final RegistryObject<ItemPipeHolder> PIPE_FLUID_STONE = REGISTRY.addItem(
        "pipe_fluid_stone",
        () -> new ItemPipeHolder(PIPE_HOLDER.get(), new Item.Properties(), PIPE_STONE_FLUID)
    );

    public static final RegistryObject<ItemPipeHolder> PIPE_FLUID_SANDSTONE = REGISTRY.addItem(
        "pipe_fluid_sandstone",
        () -> new ItemPipeHolder(PIPE_HOLDER.get(), new Item.Properties(), PIPE_SANDSTONE_FLUID)
    );

    public static final RegistryObject<ItemPipeHolder> PIPE_FLUID_QUARTZ = REGISTRY.addItem(
        "pipe_fluid_quartz",
        () -> new ItemPipeHolder(PIPE_HOLDER.get(), new Item.Properties(), PIPE_QUARTZ_FLUID)
    );

    public static final RegistryObject<ItemPipeHolder> PIPE_FLUID_GOLD = REGISTRY.addItem(
        "pipe_fluid_gold",
        () -> new ItemPipeHolder(PIPE_HOLDER.get(), new Item.Properties(), PIPE_GOLD_FLUID)
    );

    public static final RegistryObject<ItemPipeHolder> PIPE_FLUID_IRON = REGISTRY.addItem(
        "pipe_fluid_iron",
        () -> new ItemPipeHolder(PIPE_HOLDER.get(), new Item.Properties(), PIPE_IRON_FLUID)
    );

    public static final RegistryObject<ItemPipeHolder> PIPE_FLUID_CLAY = REGISTRY.addItem(
        "pipe_fluid_clay",
        () -> new ItemPipeHolder(PIPE_HOLDER.get(), new Item.Properties(), PIPE_CLAY_FLUID)
    );

    public static final RegistryObject<ItemPipeHolder> PIPE_FLUID_VOID = REGISTRY.addItem(
        "pipe_fluid_void",
        () -> new ItemPipeHolder(PIPE_HOLDER.get(), new Item.Properties(), PIPE_VOID_FLUID)
    );

    public static final RegistryObject<ItemPipeHolder> PIPE_FLUID_DIAMOND = REGISTRY.addItem(
        "pipe_fluid_diamond",
        () -> new ItemPipeHolder(PIPE_HOLDER.get(), new Item.Properties(), PIPE_DIAMOND_FLUID)
    );

    public static final RegistryObject<ItemPipeHolder> PIPE_FLUID_DIAMOND_WOOD = REGISTRY.addItem(
        "pipe_fluid_diamond_wood",
        () -> new ItemPipeHolder(PIPE_HOLDER.get(), new Item.Properties(), PIPE_DIAMOND_WOOD_FLUID)
    );

    // The power (kinesis) pipes' placeable items -- "pipe_power_<material>".

    public static final RegistryObject<ItemPipeHolder> PIPE_POWER_COBBLESTONE = REGISTRY.addItem(
        "pipe_power_cobblestone",
        () -> new ItemPipeHolder(PIPE_HOLDER.get(), new Item.Properties(), PIPE_COBBLESTONE_POWER)
    );

    public static final RegistryObject<ItemPipeHolder> PIPE_POWER_WOOD = REGISTRY.addItem(
        "pipe_power_wood",
        () -> new ItemPipeHolder(PIPE_HOLDER.get(), new Item.Properties(), PIPE_WOOD_POWER)
    );

    public static final RegistryObject<ItemPipeHolder> PIPE_POWER_STONE = REGISTRY.addItem(
        "pipe_power_stone",
        () -> new ItemPipeHolder(PIPE_HOLDER.get(), new Item.Properties(), PIPE_STONE_POWER)
    );

    public static final RegistryObject<ItemPipeHolder> PIPE_POWER_SANDSTONE = REGISTRY.addItem(
        "pipe_power_sandstone",
        () -> new ItemPipeHolder(PIPE_HOLDER.get(), new Item.Properties(), PIPE_SANDSTONE_POWER)
    );

    public static final RegistryObject<ItemPipeHolder> PIPE_POWER_QUARTZ = REGISTRY.addItem(
        "pipe_power_quartz",
        () -> new ItemPipeHolder(PIPE_HOLDER.get(), new Item.Properties(), PIPE_QUARTZ_POWER)
    );

    public static final RegistryObject<ItemPipeHolder> PIPE_POWER_GOLD = REGISTRY.addItem(
        "pipe_power_gold",
        () -> new ItemPipeHolder(PIPE_HOLDER.get(), new Item.Properties(), PIPE_GOLD_POWER)
    );

    public static final RegistryObject<ItemPipeHolder> PIPE_POWER_IRON = REGISTRY.addItem(
        "pipe_power_iron",
        () -> new ItemPipeHolder(PIPE_HOLDER.get(), new Item.Properties(), PIPE_IRON_POWER)
    );

    public static final RegistryObject<ItemPipeHolder> PIPE_POWER_DIAMOND = REGISTRY.addItem(
        "pipe_power_diamond",
        () -> new ItemPipeHolder(PIPE_HOLDER.get(), new Item.Properties(), PIPE_DIAMOND_POWER)
    );

    public static final RegistryObject<ItemPipeHolder> PIPE_POWER_DIAMOND_WOOD = REGISTRY.addItem(
        "pipe_power_diamond_wood",
        () -> new ItemPipeHolder(PIPE_HOLDER.get(), new Item.Properties(), PIPE_DIAMOND_WOOD_POWER)
    );

    /** The diamond pipes' own filter-configuration menus -- see {@code TilePipeHolder#createMenu}'s own javadoc
     * for the dispatch, and {@code BCTransportClientRegistries} for the paired screen registration. */
    public static final RegistryObject<MenuType<ContainerDiamondPipe>> PIPE_DIAMOND_MENU =
        REGISTRY.addMenu("pipe_diamond", ContainerDiamondPipe::new);

    public static final RegistryObject<MenuType<ContainerDiamondWoodPipe>> PIPE_DIAMOND_WOOD_MENU =
        REGISTRY.addMenu("pipe_diamond_wood", ContainerDiamondWoodPipe::new);

    // #########
    //
    // Pluggables (wires/gates/pluggables batch) -- see the 26.x copy of this file for the full account.
    //
    // #########

    public static final buildcraft.api.transport.pluggable.PluggableDefinition PLUGGABLE_DEF_BLOCKER =
        new buildcraft.api.transport.pluggable.PluggableDefinition(
            new net.minecraft.resources.ResourceLocation(BuildCraft.MOD_ID, "blocker"),
            (definition, holder, side, nbt, registries) -> new buildcraft.transport.plug.PluggableBlocker(definition, holder, side),
            (definition, holder, side, buffer) -> new buildcraft.transport.plug.PluggableBlocker(definition, holder, side)
        );

    public static final buildcraft.api.transport.pluggable.PluggableDefinition PLUGGABLE_DEF_POWER_ADAPTOR =
        new buildcraft.api.transport.pluggable.PluggableDefinition(
            new net.minecraft.resources.ResourceLocation(BuildCraft.MOD_ID, "power_adaptor"),
            (definition, holder, side, nbt, registries) ->
                new buildcraft.transport.plug.PluggablePowerAdaptor(definition, holder, side, nbt),
            (definition, holder, side, buffer) -> new buildcraft.transport.plug.PluggablePowerAdaptor(definition, holder, side)
        );

    public static final buildcraft.api.transport.pluggable.PluggableDefinition PLUGGABLE_DEF_GATE =
        new buildcraft.api.transport.pluggable.PluggableDefinition(
            new net.minecraft.resources.ResourceLocation(BuildCraft.MOD_ID, "gate"),
            (definition, holder, side, nbt, registries) ->
                new buildcraft.transport.plug.PluggableGate(definition, holder, side, nbt, registries),
            (definition, holder, side, buffer) -> {
                throw new UnsupportedOperationException("PluggableGate has no network-only constructor in this batch");
            }
        );

    static {
        PipeApi.pluggableRegistry.register(PLUGGABLE_DEF_BLOCKER);
        PipeApi.pluggableRegistry.register(PLUGGABLE_DEF_POWER_ADAPTOR);
        PipeApi.pluggableRegistry.register(PLUGGABLE_DEF_GATE);
    }

    public static final RegistryObject<buildcraft.transport.item.ItemPluggableSimple> PLUG_BLOCKER = REGISTRY.addItem(
        "plug_blocker",
        () -> new buildcraft.transport.item.ItemPluggableSimple(
            new Item.Properties(),
            (holder, side) -> new buildcraft.transport.plug.PluggableBlocker(PLUGGABLE_DEF_BLOCKER, holder, side)
        )
    );

    public static final RegistryObject<buildcraft.transport.item.ItemPluggableSimple> PLUG_POWER_ADAPTOR = REGISTRY.addItem(
        "plug_power_adaptor",
        () -> new buildcraft.transport.item.ItemPluggableSimple(
            new Item.Properties(),
            (holder, side) -> new buildcraft.transport.plug.PluggablePowerAdaptor(PLUGGABLE_DEF_POWER_ADAPTOR, holder, side)
        )
    );

    public static final RegistryObject<buildcraft.transport.item.ItemPluggableGate> ITEM_PLUGGABLE_GATE = REGISTRY.addItem(
        "gate", () -> new buildcraft.transport.item.ItemPluggableGate(new Item.Properties())
    );

    public static final RegistryObject<MenuType<buildcraft.transport.container.ContainerGate>> GATE_MENU =
        REGISTRY.addMenu("gate", buildcraft.transport.container.ContainerGate::new);

    public static void register(IEventBus modBus) {
        REGISTRY.register(modBus);
        modBus.addListener(BCTransportRegistries::registerCapabilities);

        // Wires BuildCraftAPI.fakePlayerProvider -- confirmed unassigned anywhere else in this whole port before
        // this batch (see PipeBehaviourStripes's own javadoc); PipeBehaviourStripes#onDrop is the first real
        // caller that needs a live Player to hand a stripes handler.
        BuildCraftAPI.fakePlayerProvider = FakePlayerProvider.INSTANCE;

        // 1.12.2's own BCTransportRegistries#init handler registration -- see the 26.x copy of this method's own
        // comment for the Shears/Dispenser/PipeWires scope cuts.
        PipeApi.stripeRegistry.addHandler(StripesHandlerPlant.INSTANCE);
        PipeApi.stripeRegistry.addHandler(new StripesHandlerPipes());
        PipeApi.stripeRegistry.addHandler(StripesHandlerEntityInteract.INSTANCE, EnumHandlerPriority.LOW);
        PipeApi.stripeRegistry.addHandler(StripesHandlerHoe.INSTANCE);
        PipeApi.stripeRegistry.addHandler(StripesHandlerPlaceBlock.INSTANCE, EnumHandlerPriority.LOW);
        PipeApi.stripeRegistry.addHandler(StripesHandlerUse.INSTANCE, EnumHandlerPriority.LOW);
        PipeApi.stripeRegistry.addHandler(StripesHandlerMinecartDestroy.INSTANCE);

        PipeApi.extensionManager.registerRetractionPipe(PIPE_VOID);

        // Gate statements (wires/gates/pluggables batch) -- see the 26.x copy of this method for the full
        // account.
        buildcraft.api.statements.StatementManager.registerTriggerProvider(
            buildcraft.transport.statements.TriggerProviderPipes.INSTANCE
        );
        buildcraft.api.statements.StatementManager.registerActionProvider(
            buildcraft.transport.statements.ActionProviderPipes.INSTANCE
        );
        for (buildcraft.transport.statements.TriggerPipeSignal trigger
            : buildcraft.transport.statements.TriggerPipeSignal.all()) {
            buildcraft.api.statements.StatementManager.registerStatement(trigger);
        }
        for (buildcraft.transport.statements.ActionPipeSignal action
            : buildcraft.transport.statements.ActionPipeSignal.all()) {
            buildcraft.api.statements.StatementManager.registerStatement(action);
        }
    }

    private static void registerCapabilities(RegisterCapabilitiesEvent event) {
        event.register(IPipeHolder.class);
        event.register(IPipe.class);
        event.register(PipePluggable.class);
        event.register(IInjectable.class);
    }

    /** Looks up the placeable {@link ItemPipeHolder} for whatever {@link PipeDefinition} is actually stamped
     * onto a given {@code TilePipeHolder}'s own {@code Pipe} -- see the 26.x copy of this class's own javadoc
     * for the full account of why this fixes the {@code pipe_holder} loot table's real "always drops
     * cobblestone" bug, and why {@link PipeApi#pipeRegistry}'s existing {@code getItemForPipe} needs no new map
     * here. */
    @Nullable
    public static ItemPipeHolder getItemForPipe(PipeDefinition definition) {
        IItemPipe item = PipeApi.pipeRegistry.getItemForPipe(definition);
        return item instanceof ItemPipeHolder holder ? holder : null;
    }
}
