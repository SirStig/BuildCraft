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
import net.neoforged.neoforge.registries.DeferredItem;

import buildcraft.api.core.BuildCraftAPI;
import buildcraft.api.core.EnumHandlerPriority;
import buildcraft.api.mj.MjAPI;
import buildcraft.api.mj.MjCapabilities;
import buildcraft.api.transport.pipe.IItemPipe;
import buildcraft.api.transport.pipe.PipeApi;
import buildcraft.api.transport.pipe.PipeDefinition;
import buildcraft.api.transport.pipe.PipeFlowType;

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
 * batch. Mirrors {@code BCFactoryRegistries}/{@code BCEnergyRegistries}' exact established structure: its own
 * private {@link BCRegistry}, block/item/block-entity registration, a {@code registerCapabilities}.
 *
 * <p><b>The one-block-many-items architecture, made concrete.</b> There is exactly one block
 * ({@link #PIPE_HOLDER}) and one block entity type ({@link #PIPE_HOLDER_TYPE}) here, matching 1.12.2's own real
 * design (see {@code common/buildcraft/transport/pipe/PipeRegistry.java}/{@code Pipe.java}) -- a future pipe
 * material is just another {@link PipeDefinition} plus another {@link ItemPipeHolder} instance registered here,
 * never a new block or tile class.
 *
 * <p>Static init order matters in this class and is deliberate: {@link #PIPE_API_INIT} (a static initializer
 * block, not a field) wires {@link PipeApi#pipeRegistry}/{@link PipeApi#flowItems} <em>before</em>
 * {@link #PIPE_COBBLESTONE} is built, because {@code PipeDefinition.PipeDefinitionBuilder#define()} calls
 * {@code PipeApi.pipeRegistry.registerPipe(...)} immediately, and {@code .flowItem()} reads
 * {@code PipeApi.flowItems} immediately -- both would NPE if this ran in the other order. Static fields and
 * blocks run top-to-bottom in one class, so the ordering below is load-bearing, not decorative.
 *
 * <p><b>{@code canBeColoured} is {@code false} on {@link #PIPE_COBBLESTONE}, a deliberate scope choice, not a
 * faithful copy of the original's own value.</b> Verified, not guessed: 1.12.2's real
 * {@code common/buildcraft/transport/BCTransportPipes.java} calls {@code builder.builder.enableColouring()}
 * once, right after defining the structure pipe, and that flag then stays set on the shared builder for every
 * pipe defined afterwards -- wood, stone, cobblestone, quartz, gold, and so on -- so the real 1.12.2 cobblestone
 * pipe <em>is</em> colourable. This batch disables it anyway: colouring a pipe means applying a dye, which needs
 * {@code CustomPaintHelper}-style GUI/interaction plumbing this batch does not add for pipes specifically (dye
 * colours on a pipe are explicitly out of scope -- see this module's own scope notes), so leaving
 * {@code canBeColoured} true would advertise a feature ({@code IPipe#setColour}) nothing in this batch can ever
 * legitimately trigger.
 *
 * <p>The id/texture-prefix naming also deliberately diverges from 1.12.2's own {@code "cobblestone_item"} (which
 * needed the {@code _item} suffix to stay unique alongside sibling {@code cobblestone_fluid}/
 * {@code cobblestone_power}/{@code cobblestone_rf} definitions sharing the same material prefix). This batch
 * registers exactly one cobblestone pipe kind, so the plain {@code "cobblestone"} id has nothing to collide
 * with.
 */
public final class BCTransportRegistries {

    private BCTransportRegistries() {}

    private static final BCRegistry REGISTRY = new BCRegistry(BuildCraft.MOD_ID);

    static {
        PipeApi.pipeRegistry = PipeRegistry.INSTANCE;
        // Wires the pluggable registry -- needed by TilePipeHolder#loadAdditional (looking up a PluggableDefinition
        // by id) as of the wires/gates/pluggables batch; nothing before that batch ever read PipeApi.pluggableRegistry.
        PipeApi.pluggableRegistry = buildcraft.lib.registry.PluggableRegistry.INSTANCE;
        PipeApi.flowItems = new PipeFlowType(PipeFlowItems::new, PipeFlowItems::new);
        PipeApi.flowFluids = new PipeFlowType(PipeFlowFluids::new, PipeFlowFluids::new);
        PipeApi.flowPower = new PipeFlowType(PipeFlowPower::new, PipeFlowPower::new);
        // The stripes pipe's own dispatcher/extension-manager -- see StripesRegistry/PipeExtensionManager's own
        // javadoc. Set here alongside the flow types above for the identical "read immediately by a definition
        // built further down this class" reason.
        PipeApi.stripeRegistry = StripesRegistry.INSTANCE;
        PipeApi.extensionManager = PipeExtensionManager.INSTANCE;
    }

    /** The cobblestone pipe's own {@link PipeDefinition} -- see this class's own javadoc for the
     * {@code canBeColoured}/naming notes. */
    public static final PipeDefinition PIPE_COBBLESTONE = new PipeDefinition.PipeDefinitionBuilder()
        .idTexPrefix("cobblestone")
        .logic(PipeBehaviourCobble::new, PipeBehaviourCobble::new)
        .flowItem()
        .disableColouring()
        .define();

    /** The wooden pipe's own {@link PipeDefinition} -- this batch's proof of this class's own "a future pipe
     * material is just another {@code PipeDefinition} plus another {@code ItemPipeHolder} instance" claim above.
     * {@code canBeColoured} is {@code false} for the identical reason {@link #PIPE_COBBLESTONE} already gives. */
    public static final PipeDefinition PIPE_WOOD = new PipeDefinition.PipeDefinitionBuilder()
        .idTexPrefix("wood")
        .logic(PipeBehaviourWood::new, PipeBehaviourWood::new)
        .flowItem()
        .disableColouring()
        .define();

    /** The stone pipe's own {@link PipeDefinition} -- a fast, constant-crawl speed modifier
     * ({@code PipeBehaviourStone.SPEED_DELTA} of {@code 0.008}). {@code canBeColoured} is {@code false} for the
     * identical reason {@link #PIPE_COBBLESTONE} already gives -- confirmed against the real 1.12.2
     * {@code BCTransportPipes#preInit} that the shared builder's {@code enableColouring()} call (set once,
     * before wood, and never unset) would otherwise have made the real stone pipe colourable too. */
    public static final PipeDefinition PIPE_STONE = new PipeDefinition.PipeDefinitionBuilder()
        .idTexPrefix("stone")
        .logic(PipeBehaviourStone::new, PipeBehaviourStone::new)
        .flowItem()
        .disableColouring()
        .define();

    /** The sandstone pipe's own {@link PipeDefinition} -- the pipe-to-pipe-only, never-to-an-inventory
     * material (see {@link PipeBehaviourSandstone}'s own javadoc). Same speed modifier as {@link #PIPE_STONE}
     * (its behaviour reuses {@code PipeBehaviourStone}'s own constants directly). {@code canBeColoured} is
     * {@code false} for the same reason as every other material in this batch. */
    public static final PipeDefinition PIPE_SANDSTONE = new PipeDefinition.PipeDefinitionBuilder()
        .idTexPrefix("sandstone")
        .logic(PipeBehaviourSandstone::new, PipeBehaviourSandstone::new)
        .flowItem()
        .disableColouring()
        .define();

    /** The quartz pipe's own {@link PipeDefinition} -- the gentlest speed modifier of this batch
     * ({@code PipeBehaviourQuartz.SPEED_DELTA} of {@code 0.002}). {@code canBeColoured} is {@code false} for
     * the same reason as every other material in this batch. */
    public static final PipeDefinition PIPE_QUARTZ = new PipeDefinition.PipeDefinitionBuilder()
        .idTexPrefix("quartz")
        .logic(PipeBehaviourQuartz::new, PipeBehaviourQuartz::new)
        .flowItem()
        .disableColouring()
        .define();

    /** The golden pipe's own {@link PipeDefinition} -- the speed-boost material (see {@link PipeBehaviourGold}).
     * 1.12.2's real builder call is a plain {@code builder.idTex("gold_item").flowItem().define()} after
     * {@code logic(PipeBehaviourGold::new, ...)}: no texture suffixes, default item texture -- so this is the same
     * shape as every other single-texture material here. {@code canBeColoured} is {@code false} for the same
     * reason as every other material (the real 1.12.2 golden pipe inherits the shared builder's
     * {@code enableColouring()} and <em>is</em> colourable -- see this class's own javadoc). */
    public static final PipeDefinition PIPE_GOLD = new PipeDefinition.PipeDefinitionBuilder()
        .idTexPrefix("gold")
        .logic(PipeBehaviourGold::new, PipeBehaviourGold::new)
        .flowItem()
        .disableColouring()
        .define();

    /** The iron pipe's own {@link PipeDefinition} -- the one-way, wrench-selected output valve (see
     * {@link PipeBehaviourIron}). 1.12.2's real builder call is wood's exact shape:
     * {@code texSuffixes("_clear", "_filled")} plus {@code itemTex(0, 0, 1)}, then
     * {@code idTexPrefix("iron_item")}. The two-texture split is reproduced by the {@code active} blockstate
     * property and the {@code pipe_holder_arm_iron}/{@code pipe_holder_arm_iron_filled} model pair rather than
     * by {@code PipeDefinition}'s own texture-suffix fields, which nothing in this port reads (the same reason
     * {@link #PIPE_WOOD} never set them either). {@code canBeColoured} is {@code false} for the same reason as
     * every other material. */
    public static final PipeDefinition PIPE_IRON = new PipeDefinition.PipeDefinitionBuilder()
        .idTexPrefix("iron")
        .logic(PipeBehaviourIron::new, PipeBehaviourIron::new)
        .flowItem()
        .disableColouring()
        .define();

    /** The clay pipe's own {@link PipeDefinition} -- prefers inventories over pipes (see
     * {@link PipeBehaviourClay}). 1.12.2: {@code builder.idTex("clay_item").flowItem().define()}, no suffixes.
     * {@code canBeColoured} is {@code false} for the same reason as every other material. */
    public static final PipeDefinition PIPE_CLAY = new PipeDefinition.PipeDefinitionBuilder()
        .idTexPrefix("clay")
        .logic(PipeBehaviourClay::new, PipeBehaviourClay::new)
        .flowItem()
        .disableColouring()
        .define();

    /** The void pipe's own {@link PipeDefinition} -- destroys items (see {@link PipeBehaviourVoid}). 1.12.2:
     * {@code builder.idTex("void_item").flowItem().define()}, no suffixes; its {@code void_fluid} sibling is
     * {@link #PIPE_VOID_FLUID} below. {@code canBeColoured} is {@code false} for the same reason as every other
     * material. */
    public static final PipeDefinition PIPE_VOID = new PipeDefinition.PipeDefinitionBuilder()
        .idTexPrefix("void")
        .logic(PipeBehaviourVoid::new, PipeBehaviourVoid::new)
        .flowItem()
        .disableColouring()
        .define();

    /** The obsidian pipe's own {@link PipeDefinition} -- an MJ-powered magnet, not explosive at all (see
     * {@link PipeBehaviourObsidian}'s own javadoc). 1.12.2: {@code builder.idTex("obsidian_item").flowItem()
     * .define()}; its {@code obsidian_fluid} sibling is commented out in the real 1.12.2 source itself
     * (confirmed by reading {@code BCTransportPipes#preInit} directly), so this material stays item-only here
     * too, matching upstream rather than a scope cut of this port's own. */
    public static final PipeDefinition PIPE_OBSIDIAN = new PipeDefinition.PipeDefinitionBuilder()
        .idTexPrefix("obsidian")
        .logic(PipeBehaviourObsidian::new, PipeBehaviourObsidian::new)
        .flowItem()
        .disableColouring()
        .define();

    /** The lapis pipe's own {@link PipeDefinition} -- paints items with a colour for a diamond pipe further down
     * the line to sort on (see {@link PipeBehaviourLapis}'s own javadoc). 1.12.2 only ever registers this as
     * {@code lapisItem} -- item-only, no fluid sibling. */
    public static final PipeDefinition PIPE_LAPIS = new PipeDefinition.PipeDefinitionBuilder()
        .idTexPrefix("lapis")
        .logic(PipeBehaviourLapis::new, PipeBehaviourLapis::new)
        .flowItem()
        .disableColouring()
        .define();

    /** The daizuli pipe's own {@link PipeDefinition} -- a directional colour filter, BuildCraft 8-specific (see
     * {@link PipeBehaviourDaizuli}'s own javadoc). 1.12.2 only ever registers this as {@code daizuliItem}. */
    public static final PipeDefinition PIPE_DAIZULI = new PipeDefinition.PipeDefinitionBuilder()
        .idTexPrefix("daizuli")
        .logic(PipeBehaviourDaizuli::new, PipeBehaviourDaizuli::new)
        .flowItem()
        .disableColouring()
        .define();

    /** The emzuli pipe's own {@link PipeDefinition} -- a four-preset extraction wooden pipe, BuildCraft
     * 8-specific (see {@link PipeBehaviourEmzuli}'s own javadoc). 1.12.2 only ever registers this as
     * {@code emzuliItem}. */
    public static final PipeDefinition PIPE_EMZULI = new PipeDefinition.PipeDefinitionBuilder()
        .idTexPrefix("emzuli")
        .logic(PipeBehaviourEmzuli::new, PipeBehaviourEmzuli::new)
        .flowItem()
        .disableColouring()
        .define();

    /** The stripes pipe's own {@link PipeDefinition} -- a real, distinct pipe material (not merely an
     * "extension" attached to another pipe -- see {@link PipeBehaviourStripes}'s own javadoc), the extraction
     * pipe that mines/interacts with the world ahead of its open face. 1.12.2 only ever registers this as
     * {@code stripesItem}. */
    public static final PipeDefinition PIPE_STRIPES = new PipeDefinition.PipeDefinitionBuilder()
        .idTexPrefix("stripes")
        .logic(PipeBehaviourStripes::new, PipeBehaviourStripes::new)
        .flowItem()
        .disableColouring()
        .define();

    /** The diamond pipe's own {@link PipeDefinition} -- the item-sorting/filtering material (see
     * {@link buildcraft.transport.pipe.behaviour.PipeBehaviourDiamond}). 1.12.2's real builder call gives this
     * material eight distinct per-face texture suffixes (one per {@code EnumFacing} plus an item-stack variant);
     * as with every other material in this file, {@code PipeDefinition.textures}/the suffix array has zero
     * readers anywhere in this port, so a single {@code idTexPrefix("diamond")} is enough. {@code canBeColoured}
     * is {@code false} for the same reason as every other material in this batch. */
    public static final PipeDefinition PIPE_DIAMOND = new PipeDefinition.PipeDefinitionBuilder()
        .idTexPrefix("diamond")
        .logic(PipeBehaviourDiamondItem::new, PipeBehaviourDiamondItem::new)
        .flowItem()
        .disableColouring()
        .define();

    /** The wood/diamond combo pipe's own {@link PipeDefinition} -- a filtered wooden pipe (see
     * {@link buildcraft.transport.pipe.behaviour.PipeBehaviourWoodDiamond}). 1.12.2's own id is
     * {@code "diamond_wood_item"}; this port's id is the bare material name {@code "diamond_wood"}, matching
     * every other item pipe's own {@code idTexPrefix(material)} shape in this file (the {@code "pipe_item_"}
     * prefix lives on the placeable item below instead, not the definition). */
    public static final PipeDefinition PIPE_DIAMOND_WOOD = new PipeDefinition.PipeDefinitionBuilder()
        .idTexPrefix("diamond_wood")
        .logic(PipeBehaviourWoodDiamond::new, PipeBehaviourWoodDiamond::new)
        .flowItem()
        .disableColouring()
        .define();

    // The fluid pipes: 1.12.2's BCTransportPipes#preInit defines each one right after its item sibling, with the
    // same logic(...) and flowFluid() in place of flowItem() -- `woodFluid = builder.idTexPrefix("wood_fluid")
    // .flowFluid().define()`, `cobbleFluid = builder.idTex("cobblestone_fluid")...`, and so on. Their ids keep
    // 1.12.2's own "<material>_fluid" names (EnumPipeMaterial's values match them); colouring is disabled for the
    // same reason as every item pipe above. Transfer rates are set by the static block below.

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

    /** 1.12.2's {@code BCTransportConfig#baseFlowRate} default (mB per tick); the config file itself is not
     * ported, so this is the value every rate below derives from. */
    private static final int BASE_FLOW_RATE = 10;

    /** 1.12.2's {@code BCTransportConfig} {@code fluidTransfer(...)} calls, verbatim (rate multiplier, delay):
     * cobblestone/wood x1, stone/sandstone x2, clay/iron/quartz x4, gold x8 with a delay of 2, void x8; every
     * other delay is 10. Must run after the definitions above and before any pipe is placed -- a
     * {@code PipeFlowFluids} reads its rate once, when constructed. */
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

    // The power (kinesis) pipes. 1.12.2's real BCTransportPipes#preInit defines nine of these
    // (cobblestone/wood/stone/sandstone/quartz/gold/iron/diamond/diamond_wood). The six below reuse this batch's
    // existing item-pipe behaviours exactly as 1.12.2 itself does (its own PIPE_STONE/PIPE_COBBLESTONE/... share
    // one `builder.logic(...)` call across their item/fluid/power siblings), since none of Cobble/Stone/
    // Sandstone/Quartz/Gold's behaviour actually depends on which flow they carry. PIPE_WOOD_POWER needs its own
    // behaviour class (PipeBehaviourWoodPower), for the same reason 1.12.2's own woodPower registration switches
    // logic(...) away from plain PipeBehaviourWood first. Iron/diamond/diamond_wood power (below) needed a whole
    // new behaviour (PipeBehaviourLimiter) and were cut from that earlier batch for exactly that reason -- ported
    // this batch, see PipeBehaviourLimiter's own javadoc.
    //
    // Ids follow this port's own "pipe_fluid_<material>" convention for the fluid pipes above, so these are
    // "pipe_power_<material>" rather than 1.12.2's own "<material>_power" -- matching BCTransportRegistries'
    // existing PIPE_ITEM_*/PIPE_FLUID_* naming for their own DeferredItem constants below (PIPE_POWER_*).

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
     * behaviour swap for exactly these two materials (confirmed by re-reading {@code BCTransportPipes#preInit}:
     * {@code ironPower}/{@code diamondPower} both switch {@code logic(...)} to {@code PipeBehaviourLimiter} right
     * before defining them, sharing one builder call the same way this file's own six materials above do). */
    public static final PipeDefinition PIPE_IRON_POWER =
        powerPipe("pipe_power_iron", PipeBehaviourLimiter::new, PipeBehaviourLimiter::new);
    public static final PipeDefinition PIPE_DIAMOND_POWER =
        powerPipe("pipe_power_diamond", PipeBehaviourLimiter::new, PipeBehaviourLimiter::new);

    /** The diamond_wood power pipe's own {@link PipeDefinition}. <b>Not {@link PipeBehaviourLimiter} --</b>
     * verified against 1.12.2's real {@code BCTransportPipes#preInit} rather than assumed from this batch's own
     * task description (which named it alongside iron/diamond as a {@code PipeBehaviourLimiter} material): the
     * real source switches {@code logic(...)} to {@code PipeBehaviourWoodDiamond} for {@code diaWoodItem}/
     * {@code diaWoodFluid}, then to plain {@code PipeBehaviourWoodPower} -- the exact same class {@link
     * #PIPE_WOOD_POWER} above already uses -- for {@code diaWoodPower}, never touching {@code PipeBehaviourLimiter}
     * at all for this one material. This is faithful to the original, not a shortcut: a diamond/wood kinesis pipe
     * is a wooden receiver pipe that merely renders with the fancier diamond/wood arm model, not a limiter. */
    public static final PipeDefinition PIPE_DIAMOND_WOOD_POWER =
        powerPipe("pipe_power_diamond_wood", PipeBehaviourWoodPower::new, PipeBehaviourWoodPower::new);

    /** 1.12.2's {@code BCTransportConfig.basePowerRate} default (4) and its own
     * {@code reloadConfig}/{@code powerTransfer(...)} calls, verbatim: cobblestone x1/16, stone x2/32,
     * wood x4/128 (receiver), sandstone x4/32, quartz x8/32, iron x8/32, gold x32/32, diamond x64/32,
     * diamond_wood x64/32 (receiver) -- {@code BCTransportConfig} itself is not ported (see
     * {@code PIPE_COBBLESTONE_FLUID}'s own fluid-transfer note above for the identical reasoning), so these are
     * the plain constant results of that formula. Must run after the definitions above and before any pipe is
     * placed -- {@code PipeFlowPower} reads its transfer info once, in {@code reconfigure()}. */
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

    /** {@code PipeDefinition.textures} (set by {@code idTex}'s {@code tex()} half) has zero readers anywhere in
     * this port -- confirmed by grepping the whole 26.x source tree -- so which string it resolves to here is
     * inert; block/item appearance goes entirely through the static blockstate multipart + item-json assets these
     * definitions' {@code identifier} feeds into (see {@code EnumPipeMaterial}/this class's own javadoc scope
     * note). {@code idTex(id)} is used anyway, purely for consistency with every other definition in this file. */
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
     * block, the same reasoning already worked out for {@code BlockDecoration}/{@code BlockEngineWood}/
     * {@code BlockChute} -- see {@code BCFactoryRegistries#CHUTE}'s own javadoc. A plain full cube, matching
     * {@code BlockTank}/{@code BlockPump}'s own "no renderer yet" precedent -- see {@link BlockPipeHolder}'s own
     * javadoc. No {@code addBlockAndItem}: this block's item is the pipe-specific {@link ItemPipeHolder} below,
     * not an auto-generated plain {@code BlockItem}. */
    public static final DeferredBlock<BlockPipeHolder> PIPE_HOLDER = REGISTRY.addBlock(
        "pipe_holder", BlockPipeHolder::new,
        properties -> properties
            .mapColor(MapColor.STONE)
            .strength(0.25F, 3.0F)
            .sound(SoundType.STONE)
            .noOcclusion()
            .requiresCorrectToolForDrops()
    );

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<TilePipeHolder>> PIPE_HOLDER_TYPE =
        REGISTRY.addBlockEntity("pipe_holder", TilePipeHolder::new, PIPE_HOLDER);

    /** The one item registered in this batch: the cobblestone pipe's own placeable item, tagged with
     * {@link #PIPE_COBBLESTONE}. Referencing {@code PIPE_HOLDER.get()} inside this factory is safe despite
     * running before {@link #PIPE_HOLDER} is actually bound: {@link DeferredRegister.Items}' own factory is not
     * invoked until the item registry event fires, by which point the block registry (registered first, same
     * pattern {@code BCRegistry#addBlockAndItem}'s own {@code registerSimpleBlockItem} already relies on) has
     * already bound every {@code DeferredBlock}. */
    public static final DeferredItem<ItemPipeHolder> PIPE_ITEM_COBBLESTONE = REGISTRY.addItem(
        "pipe_item_cobblestone",
        properties -> new ItemPipeHolder(PIPE_HOLDER.get(), properties, PIPE_COBBLESTONE)
    );

    /** The wooden pipe's own placeable item, tagged with {@link #PIPE_WOOD}. No new block/tile code was needed
     * for this -- see this class's own javadoc. */
    public static final DeferredItem<ItemPipeHolder> PIPE_ITEM_WOOD = REGISTRY.addItem(
        "pipe_item_wood",
        properties -> new ItemPipeHolder(PIPE_HOLDER.get(), properties, PIPE_WOOD)
    );

    /** The stone pipe's own placeable item, tagged with {@link #PIPE_STONE}. */
    public static final DeferredItem<ItemPipeHolder> PIPE_ITEM_STONE = REGISTRY.addItem(
        "pipe_item_stone",
        properties -> new ItemPipeHolder(PIPE_HOLDER.get(), properties, PIPE_STONE)
    );

    /** The sandstone pipe's own placeable item, tagged with {@link #PIPE_SANDSTONE}. */
    public static final DeferredItem<ItemPipeHolder> PIPE_ITEM_SANDSTONE = REGISTRY.addItem(
        "pipe_item_sandstone",
        properties -> new ItemPipeHolder(PIPE_HOLDER.get(), properties, PIPE_SANDSTONE)
    );

    /** The quartz pipe's own placeable item, tagged with {@link #PIPE_QUARTZ}. */
    public static final DeferredItem<ItemPipeHolder> PIPE_ITEM_QUARTZ = REGISTRY.addItem(
        "pipe_item_quartz",
        properties -> new ItemPipeHolder(PIPE_HOLDER.get(), properties, PIPE_QUARTZ)
    );

    /** The golden pipe's own placeable item, tagged with {@link #PIPE_GOLD}. */
    public static final DeferredItem<ItemPipeHolder> PIPE_ITEM_GOLD = REGISTRY.addItem(
        "pipe_item_gold",
        properties -> new ItemPipeHolder(PIPE_HOLDER.get(), properties, PIPE_GOLD)
    );

    /** The iron pipe's own placeable item, tagged with {@link #PIPE_IRON}. */
    public static final DeferredItem<ItemPipeHolder> PIPE_ITEM_IRON = REGISTRY.addItem(
        "pipe_item_iron",
        properties -> new ItemPipeHolder(PIPE_HOLDER.get(), properties, PIPE_IRON)
    );

    /** The clay pipe's own placeable item, tagged with {@link #PIPE_CLAY}. */
    public static final DeferredItem<ItemPipeHolder> PIPE_ITEM_CLAY = REGISTRY.addItem(
        "pipe_item_clay",
        properties -> new ItemPipeHolder(PIPE_HOLDER.get(), properties, PIPE_CLAY)
    );

    /** The void pipe's own placeable item, tagged with {@link #PIPE_VOID}. */
    public static final DeferredItem<ItemPipeHolder> PIPE_ITEM_VOID = REGISTRY.addItem(
        "pipe_item_void",
        properties -> new ItemPipeHolder(PIPE_HOLDER.get(), properties, PIPE_VOID)
    );

    /** The obsidian pipe's own placeable item, tagged with {@link #PIPE_OBSIDIAN}. */
    public static final DeferredItem<ItemPipeHolder> PIPE_ITEM_OBSIDIAN = REGISTRY.addItem(
        "pipe_item_obsidian",
        properties -> new ItemPipeHolder(PIPE_HOLDER.get(), properties, PIPE_OBSIDIAN)
    );

    /** The lapis pipe's own placeable item, tagged with {@link #PIPE_LAPIS}. */
    public static final DeferredItem<ItemPipeHolder> PIPE_ITEM_LAPIS = REGISTRY.addItem(
        "pipe_item_lapis",
        properties -> new ItemPipeHolder(PIPE_HOLDER.get(), properties, PIPE_LAPIS)
    );

    /** The daizuli pipe's own placeable item, tagged with {@link #PIPE_DAIZULI}. */
    public static final DeferredItem<ItemPipeHolder> PIPE_ITEM_DAIZULI = REGISTRY.addItem(
        "pipe_item_daizuli",
        properties -> new ItemPipeHolder(PIPE_HOLDER.get(), properties, PIPE_DAIZULI)
    );

    /** The emzuli pipe's own placeable item, tagged with {@link #PIPE_EMZULI}. */
    public static final DeferredItem<ItemPipeHolder> PIPE_ITEM_EMZULI = REGISTRY.addItem(
        "pipe_item_emzuli",
        properties -> new ItemPipeHolder(PIPE_HOLDER.get(), properties, PIPE_EMZULI)
    );

    /** The stripes pipe's own placeable item, tagged with {@link #PIPE_STRIPES}. */
    public static final DeferredItem<ItemPipeHolder> PIPE_ITEM_STRIPES = REGISTRY.addItem(
        "pipe_item_stripes",
        properties -> new ItemPipeHolder(PIPE_HOLDER.get(), properties, PIPE_STRIPES)
    );

    /** The diamond pipe's own placeable item, tagged with {@link #PIPE_DIAMOND}. */
    public static final DeferredItem<ItemPipeHolder> PIPE_ITEM_DIAMOND = REGISTRY.addItem(
        "pipe_item_diamond",
        properties -> new ItemPipeHolder(PIPE_HOLDER.get(), properties, PIPE_DIAMOND)
    );

    /** The wood/diamond combo pipe's own placeable item, tagged with {@link #PIPE_DIAMOND_WOOD}. */
    public static final DeferredItem<ItemPipeHolder> PIPE_ITEM_DIAMOND_WOOD = REGISTRY.addItem(
        "pipe_item_diamond_wood",
        properties -> new ItemPipeHolder(PIPE_HOLDER.get(), properties, PIPE_DIAMOND_WOOD)
    );

    // The fluid pipes' placeable items -- "pipe_fluid_<material>", alongside the "pipe_item_<material>" ones.

    public static final DeferredItem<ItemPipeHolder> PIPE_FLUID_COBBLESTONE = REGISTRY.addItem(
        "pipe_fluid_cobblestone",
        properties -> new ItemPipeHolder(PIPE_HOLDER.get(), properties, PIPE_COBBLESTONE_FLUID)
    );

    public static final DeferredItem<ItemPipeHolder> PIPE_FLUID_WOOD = REGISTRY.addItem(
        "pipe_fluid_wood",
        properties -> new ItemPipeHolder(PIPE_HOLDER.get(), properties, PIPE_WOOD_FLUID)
    );

    public static final DeferredItem<ItemPipeHolder> PIPE_FLUID_STONE = REGISTRY.addItem(
        "pipe_fluid_stone",
        properties -> new ItemPipeHolder(PIPE_HOLDER.get(), properties, PIPE_STONE_FLUID)
    );

    public static final DeferredItem<ItemPipeHolder> PIPE_FLUID_SANDSTONE = REGISTRY.addItem(
        "pipe_fluid_sandstone",
        properties -> new ItemPipeHolder(PIPE_HOLDER.get(), properties, PIPE_SANDSTONE_FLUID)
    );

    public static final DeferredItem<ItemPipeHolder> PIPE_FLUID_QUARTZ = REGISTRY.addItem(
        "pipe_fluid_quartz",
        properties -> new ItemPipeHolder(PIPE_HOLDER.get(), properties, PIPE_QUARTZ_FLUID)
    );

    public static final DeferredItem<ItemPipeHolder> PIPE_FLUID_GOLD = REGISTRY.addItem(
        "pipe_fluid_gold",
        properties -> new ItemPipeHolder(PIPE_HOLDER.get(), properties, PIPE_GOLD_FLUID)
    );

    public static final DeferredItem<ItemPipeHolder> PIPE_FLUID_IRON = REGISTRY.addItem(
        "pipe_fluid_iron",
        properties -> new ItemPipeHolder(PIPE_HOLDER.get(), properties, PIPE_IRON_FLUID)
    );

    public static final DeferredItem<ItemPipeHolder> PIPE_FLUID_CLAY = REGISTRY.addItem(
        "pipe_fluid_clay",
        properties -> new ItemPipeHolder(PIPE_HOLDER.get(), properties, PIPE_CLAY_FLUID)
    );

    public static final DeferredItem<ItemPipeHolder> PIPE_FLUID_VOID = REGISTRY.addItem(
        "pipe_fluid_void",
        properties -> new ItemPipeHolder(PIPE_HOLDER.get(), properties, PIPE_VOID_FLUID)
    );

    public static final DeferredItem<ItemPipeHolder> PIPE_FLUID_DIAMOND = REGISTRY.addItem(
        "pipe_fluid_diamond",
        properties -> new ItemPipeHolder(PIPE_HOLDER.get(), properties, PIPE_DIAMOND_FLUID)
    );

    public static final DeferredItem<ItemPipeHolder> PIPE_FLUID_DIAMOND_WOOD = REGISTRY.addItem(
        "pipe_fluid_diamond_wood",
        properties -> new ItemPipeHolder(PIPE_HOLDER.get(), properties, PIPE_DIAMOND_WOOD_FLUID)
    );

    // The power (kinesis) pipes' placeable items -- "pipe_power_<material>", six of the nine 1.12.2 materials;
    // see PIPE_COBBLESTONE_POWER's own javadoc for which three are not ported this batch and why.

    public static final DeferredItem<ItemPipeHolder> PIPE_POWER_COBBLESTONE = REGISTRY.addItem(
        "pipe_power_cobblestone",
        properties -> new ItemPipeHolder(PIPE_HOLDER.get(), properties, PIPE_COBBLESTONE_POWER)
    );

    public static final DeferredItem<ItemPipeHolder> PIPE_POWER_WOOD = REGISTRY.addItem(
        "pipe_power_wood",
        properties -> new ItemPipeHolder(PIPE_HOLDER.get(), properties, PIPE_WOOD_POWER)
    );

    public static final DeferredItem<ItemPipeHolder> PIPE_POWER_STONE = REGISTRY.addItem(
        "pipe_power_stone",
        properties -> new ItemPipeHolder(PIPE_HOLDER.get(), properties, PIPE_STONE_POWER)
    );

    public static final DeferredItem<ItemPipeHolder> PIPE_POWER_SANDSTONE = REGISTRY.addItem(
        "pipe_power_sandstone",
        properties -> new ItemPipeHolder(PIPE_HOLDER.get(), properties, PIPE_SANDSTONE_POWER)
    );

    public static final DeferredItem<ItemPipeHolder> PIPE_POWER_QUARTZ = REGISTRY.addItem(
        "pipe_power_quartz",
        properties -> new ItemPipeHolder(PIPE_HOLDER.get(), properties, PIPE_QUARTZ_POWER)
    );

    public static final DeferredItem<ItemPipeHolder> PIPE_POWER_GOLD = REGISTRY.addItem(
        "pipe_power_gold",
        properties -> new ItemPipeHolder(PIPE_HOLDER.get(), properties, PIPE_GOLD_POWER)
    );

    public static final DeferredItem<ItemPipeHolder> PIPE_POWER_IRON = REGISTRY.addItem(
        "pipe_power_iron",
        properties -> new ItemPipeHolder(PIPE_HOLDER.get(), properties, PIPE_IRON_POWER)
    );

    public static final DeferredItem<ItemPipeHolder> PIPE_POWER_DIAMOND = REGISTRY.addItem(
        "pipe_power_diamond",
        properties -> new ItemPipeHolder(PIPE_HOLDER.get(), properties, PIPE_DIAMOND_POWER)
    );

    public static final DeferredItem<ItemPipeHolder> PIPE_POWER_DIAMOND_WOOD = REGISTRY.addItem(
        "pipe_power_diamond_wood",
        properties -> new ItemPipeHolder(PIPE_HOLDER.get(), properties, PIPE_DIAMOND_WOOD_POWER)
    );

    /** The diamond pipes' own filter-configuration menus -- see {@code TilePipeHolder#createMenu}'s own javadoc
     * for the dispatch, and {@code BCTransportClientRegistries} (new, this batch) for the paired screen
     * registration. */
    public static final DeferredHolder<MenuType<?>, MenuType<ContainerDiamondPipe>> PIPE_DIAMOND_MENU =
        REGISTRY.addMenu("pipe_diamond", ContainerDiamondPipe::new);

    public static final DeferredHolder<MenuType<?>, MenuType<ContainerDiamondWoodPipe>> PIPE_DIAMOND_WOOD_MENU =
        REGISTRY.addMenu("pipe_diamond_wood", ContainerDiamondWoodPipe::new);

    // #########
    //
    // Pluggables (wires/gates/pluggables batch, extended by the gate-accessory-pluggables batch): the pipe-face
    // accessory family -- blocker/power-adaptor plugs, gates, facades, and the lens/timer/light-sensor/pulsar
    // gate accessories. See TilePipeHolder#pluggables' own javadoc for the NBT round-trip these definitions feed.
    //
    // #########

    public static final buildcraft.api.transport.pluggable.PluggableDefinition PLUGGABLE_DEF_BLOCKER =
        new buildcraft.api.transport.pluggable.PluggableDefinition(
            net.minecraft.resources.Identifier.fromNamespaceAndPath(BuildCraft.MOD_ID, "blocker"),
            (definition, holder, side, nbt, registries) -> new buildcraft.transport.plug.PluggableBlocker(definition, holder, side),
            (definition, holder, side, buffer) -> new buildcraft.transport.plug.PluggableBlocker(definition, holder, side)
        );

    public static final buildcraft.api.transport.pluggable.PluggableDefinition PLUGGABLE_DEF_POWER_ADAPTOR =
        new buildcraft.api.transport.pluggable.PluggableDefinition(
            net.minecraft.resources.Identifier.fromNamespaceAndPath(BuildCraft.MOD_ID, "power_adaptor"),
            (definition, holder, side, nbt, registries) ->
                new buildcraft.transport.plug.PluggablePowerAdaptor(definition, holder, side, nbt),
            (definition, holder, side, buffer) -> new buildcraft.transport.plug.PluggablePowerAdaptor(definition, holder, side)
        );

    public static final buildcraft.api.transport.pluggable.PluggableDefinition PLUGGABLE_DEF_GATE =
        new buildcraft.api.transport.pluggable.PluggableDefinition(
            net.minecraft.resources.Identifier.fromNamespaceAndPath(BuildCraft.MOD_ID, "gate"),
            (definition, holder, side, nbt, registries) ->
                new buildcraft.transport.plug.PluggableGate(definition, holder, side, nbt, registries),
            (definition, holder, side, buffer) -> {
                throw new UnsupportedOperationException("PluggableGate has no network-only constructor in this batch");
            }
        );

    /** Facades (this batch): disguises a pipe segment as another block. See {@code PluggableFacade}'s own
     * javadoc, and {@code FacadeStateManager}'s for why its net-loader also throws -- like the gate above, a
     * facade's state only ever round-trips through NBT on this port, never a creation-payload buffer. */
    public static final buildcraft.api.transport.pluggable.PluggableDefinition PLUGGABLE_DEF_FACADE =
        new buildcraft.api.transport.pluggable.PluggableDefinition(
            net.minecraft.resources.Identifier.fromNamespaceAndPath(BuildCraft.MOD_ID, "facade"),
            (definition, holder, side, nbt, registries) ->
                new buildcraft.transport.plug.PluggableFacade(definition, holder, side, nbt, registries),
            (definition, holder, side, buffer) -> {
                throw new UnsupportedOperationException("PluggableFacade has no network-only constructor in this batch");
            }
        );

    /** New this pass (gate-accessory-pluggables batch): {@code buildcraft.silicon.plug.PluggableLens}, ported
     * onto {@code buildcraft.transport.plug} like every other pluggable in this port -- see that class's own
     * javadoc. */
    public static final buildcraft.api.transport.pluggable.PluggableDefinition PLUGGABLE_DEF_LENS =
        new buildcraft.api.transport.pluggable.PluggableDefinition(
            net.minecraft.resources.Identifier.fromNamespaceAndPath(BuildCraft.MOD_ID, "lens"),
            (definition, holder, side, nbt, registries) ->
                new buildcraft.transport.plug.PluggableLens(definition, holder, side, nbt),
            (definition, holder, side, buffer) ->
                new buildcraft.transport.plug.PluggableLens(definition, holder, side, null, false)
        );

    public static final buildcraft.api.transport.pluggable.PluggableDefinition PLUGGABLE_DEF_TIMER =
        new buildcraft.api.transport.pluggable.PluggableDefinition(
            net.minecraft.resources.Identifier.fromNamespaceAndPath(BuildCraft.MOD_ID, "timer"),
            (definition, holder, side) -> new buildcraft.transport.plug.PluggableTimer(definition, holder, side)
        );

    public static final buildcraft.api.transport.pluggable.PluggableDefinition PLUGGABLE_DEF_LIGHT_SENSOR =
        new buildcraft.api.transport.pluggable.PluggableDefinition(
            net.minecraft.resources.Identifier.fromNamespaceAndPath(BuildCraft.MOD_ID, "light_sensor"),
            (definition, holder, side) -> new buildcraft.transport.plug.PluggableLightSensor(definition, holder, side)
        );

    public static final buildcraft.api.transport.pluggable.PluggableDefinition PLUGGABLE_DEF_PULSAR =
        new buildcraft.api.transport.pluggable.PluggableDefinition(
            net.minecraft.resources.Identifier.fromNamespaceAndPath(BuildCraft.MOD_ID, "pulsar"),
            (definition, holder, side, nbt, registries) ->
                new buildcraft.transport.plug.PluggablePulsar(definition, holder, side, nbt),
            (definition, holder, side, buffer) -> new buildcraft.transport.plug.PluggablePulsar(definition, holder, side)
        );

    static {
        PipeApi.pluggableRegistry.register(PLUGGABLE_DEF_BLOCKER);
        PipeApi.pluggableRegistry.register(PLUGGABLE_DEF_POWER_ADAPTOR);
        PipeApi.pluggableRegistry.register(PLUGGABLE_DEF_GATE);
        PipeApi.pluggableRegistry.register(PLUGGABLE_DEF_FACADE);
        PipeApi.pluggableRegistry.register(PLUGGABLE_DEF_LENS);
        PipeApi.pluggableRegistry.register(PLUGGABLE_DEF_TIMER);
        PipeApi.pluggableRegistry.register(PLUGGABLE_DEF_LIGHT_SENSOR);
        PipeApi.pluggableRegistry.register(PLUGGABLE_DEF_PULSAR);
    }

    public static final DeferredItem<buildcraft.transport.item.ItemPluggableSimple> PLUG_BLOCKER = REGISTRY.addItem(
        "plug_blocker",
        properties -> new buildcraft.transport.item.ItemPluggableSimple(
            properties, (holder, side) -> new buildcraft.transport.plug.PluggableBlocker(PLUGGABLE_DEF_BLOCKER, holder, side)
        )
    );

    public static final DeferredItem<buildcraft.transport.item.ItemPluggableSimple> PLUG_POWER_ADAPTOR = REGISTRY.addItem(
        "plug_power_adaptor",
        properties -> new buildcraft.transport.item.ItemPluggableSimple(
            properties,
            (holder, side) -> new buildcraft.transport.plug.PluggablePowerAdaptor(PLUGGABLE_DEF_POWER_ADAPTOR, holder, side)
        )
    );

    /** The one physical lens/filter item -- every colour/lens-or-filter combination is one NBT-tagged stack of
     * this same item, matching {@link #ITEM_PLUGGABLE_GATE}'s own precedent. See {@code ItemPluggableLens}'s own
     * javadoc for the dropped creative-tab cartesian product. */
    public static final DeferredItem<buildcraft.transport.item.ItemPluggableLens> ITEM_PLUGGABLE_LENS = REGISTRY.addItem(
        "plug_lens", buildcraft.transport.item.ItemPluggableLens::new
    );

    public static final DeferredItem<buildcraft.transport.item.ItemPluggableSimple> PLUG_TIMER = REGISTRY.addItem(
        "plug_timer",
        properties -> new buildcraft.transport.item.ItemPluggableSimple(
            properties, (holder, side) -> new buildcraft.transport.plug.PluggableTimer(PLUGGABLE_DEF_TIMER, holder, side)
        )
    );

    public static final DeferredItem<buildcraft.transport.item.ItemPluggableSimple> PLUG_LIGHT_SENSOR = REGISTRY.addItem(
        "plug_light_sensor",
        properties -> new buildcraft.transport.item.ItemPluggableSimple(
            properties,
            (holder, side) -> new buildcraft.transport.plug.PluggableLightSensor(PLUGGABLE_DEF_LIGHT_SENSOR, holder, side)
        )
    );

    /** Unlike every other pluggable item above, this cannot be a plain {@code ItemPluggableSimple} factory --
     * placement is gated on the pipe's own behaviour, see {@code ItemPluggablePulsar}'s own javadoc. */
    public static final DeferredItem<buildcraft.transport.item.ItemPluggablePulsar> PLUG_PULSAR = REGISTRY.addItem(
        "plug_pulsar", buildcraft.transport.item.ItemPluggablePulsar::new
    );

    /** The one physical gate item -- every material/logic/modifier combination is one NBT-tagged stack of this
     * same item, matching 1.12.2's own {@code ItemPluggableGate} shape. See that class's own javadoc for this
     * batch's creative-tab scope cut. */
    public static final DeferredItem<buildcraft.transport.item.ItemPluggableGate> ITEM_PLUGGABLE_GATE = REGISTRY.addItem(
        "gate", buildcraft.transport.item.ItemPluggableGate::new
    );

    /** The gate configuration GUI's menu type -- see {@code PluggableGate}/{@code ContainerGate}'s own javadoc
     * for why this reads its position+side from the menu-open extra data rather than a block position alone
     * (unlike every other {@code addMenu} in this file, a gate is not itself a block entity). */
    public static final DeferredHolder<MenuType<?>, MenuType<buildcraft.transport.container.ContainerGate>> GATE_MENU =
        REGISTRY.addMenu("gate", buildcraft.transport.container.ContainerGate::new);

    /** The one physical facade item, an NBT-tagged {@code FacadeInstance} -- see that class's own javadoc. */
    public static final DeferredItem<buildcraft.transport.item.ItemPluggableFacade> ITEM_PLUGGABLE_FACADE =
        REGISTRY.addItem("plug_facade", buildcraft.transport.item.ItemPluggableFacade::new);

    public static void register(IEventBus modBus) {
        REGISTRY.register(modBus);
        modBus.addListener(BCTransportRegistries::registerCapabilities);
        NeoForge.EVENT_BUS.addListener(BCTransportRegistries::onDefaultComponentsBound);

        // Wires BuildCraftAPI.fakePlayerProvider -- confirmed unassigned anywhere else in this whole port before
        // this batch (see PipeBehaviourStripes's own javadoc); PipeBehaviourStripes#onDrop is the first real
        // caller that needs a live Player to hand a stripes handler.
        BuildCraftAPI.fakePlayerProvider = FakePlayerProvider.INSTANCE;

        // 1.12.2's own BCTransportRegistries#init handler registration, in the same order and at the same
        // priorities -- StripesHandlerShears/StripesHandlerDispenser/StripesHandlerPipeWires are scope cuts (see
        // this module's PORTING.md entry): Shears' underlying IShearable no longer targets blocks on this target
        // (entity-only now), Dispenser's BlockSource became a record tied to a real DispenserBlockEntity rather
        // than a freely-implementable interface, and PipeWires was already dead/commented-out in 1.12.2 itself.
        PipeApi.stripeRegistry.addHandler(StripesHandlerPlant.INSTANCE);
        PipeApi.stripeRegistry.addHandler(new StripesHandlerPipes());
        PipeApi.stripeRegistry.addHandler(StripesHandlerEntityInteract.INSTANCE, EnumHandlerPriority.LOW);
        PipeApi.stripeRegistry.addHandler(StripesHandlerHoe.INSTANCE);
        PipeApi.stripeRegistry.addHandler(StripesHandlerPlaceBlock.INSTANCE, EnumHandlerPriority.LOW);
        PipeApi.stripeRegistry.addHandler(StripesHandlerUse.INSTANCE, EnumHandlerPriority.LOW);
        PipeApi.stripeRegistry.addHandler(StripesHandlerMinecartDestroy.INSTANCE);

        PipeApi.extensionManager.registerRetractionPipe(PIPE_VOID);

        // Gate statements (wires/gates/pluggables batch) -- the one real end-to-end trigger/action pair this
        // batch ports; see TriggerPipeSignal/ActionPipeSignal's own javadoc for what is deliberately not here.
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

        // Gate-accessory-pluggables batch: PluggableTimer/LightSensor/Pulsar's own trigger/action offers.
        // TriggerProviderPipes/ActionProviderPipes above already fire the AddTriggerInternal(Sided)/
        // AddActionInternalSided events these three read from -- see PluggableTimer's own javadoc.
        for (buildcraft.transport.statements.TriggerTimer trigger : buildcraft.transport.statements.TriggerTimer.all()) {
            buildcraft.api.statements.StatementManager.registerStatement(trigger);
        }
        for (buildcraft.transport.statements.TriggerLightSensor trigger
            : buildcraft.transport.statements.TriggerLightSensor.all()) {
            buildcraft.api.statements.StatementManager.registerStatement(trigger);
        }
        for (buildcraft.transport.statements.ActionPowerPulsar action
            : buildcraft.transport.statements.ActionPowerPulsar.all()) {
            buildcraft.api.statements.StatementManager.registerStatement(action);
        }
    }

    /** Looks up the placeable {@link ItemPipeHolder} for whatever {@link PipeDefinition} is actually stamped
     * onto a given {@code TilePipeHolder}'s own {@code Pipe} -- used by {@link BlockPipeHolder#getDrops} to
     * drop the correct material instead of the static {@code pipe_holder} loot table's single hard-coded
     * {@code pipe_item_cobblestone} entry (a real bug, on file in PORTING.md since the wood-pipe batch, now
     * fixed because a third material -- and a fourth, fifth, sixth here -- makes it actually wrong in practice,
     * not just theoretically). Delegates to {@link PipeApi#pipeRegistry}'s own {@code getItemForPipe}, which
     * every {@link ItemPipeHolder} already self-registers into from its own constructor -- no separate map
     * needed here, since {@code PipeRegistry} already keeps exactly this association for every registered
     * pipe material, present and future, with zero extra bookkeeping in this class. */
    @Nullable
    public static ItemPipeHolder getItemForPipe(PipeDefinition definition) {
        IItemPipe item = PipeApi.pipeRegistry.getItemForPipe(definition);
        return item instanceof ItemPipeHolder holder ? holder : null;
    }

    /** Wires the vanilla-interop item capability so a neighbouring machine (a real hopper, an already-ported
     * chute, ...) can push/pull items through the pipe's own ends -- matching
     * {@code BCFactoryRegistries#registerCapabilities}'s own {@code CHUTE_TYPE} precedent. The actual
     * {@code ResourceHandler<ItemResource>} implementation lives on {@code PipeFlowItems} itself (see that
     * class's own {@code getCapability} javadoc for why), reached generically through
     * {@code TilePipeHolder#getCapability} -> {@code Pipe#getCapability} -> {@code PipeBehaviour}/
     * {@code PipeFlow#getCapability}, the same dispatch chain {@link buildcraft.api.transport.pipe.IPipe}'s own
     * javadoc describes. */
    private static boolean facadesScanned = false;

    /** Facades (this batch): scans every registered block for valid disguises once every mod's blocks are
     * actually in the registry. <b>Not</b> wired to {@code FMLCommonSetupEvent} -- {@code BCEnergyRegistries}'
     * own {@code onDefaultComponentsBound} javadoc already documents, from a real reproduced dedicated-server
     * crash, that common setup is too early on this target for anything that builds an {@code ItemStack}:
     * {@code FacadeStateManager.init()} does exactly that (once per scanned block, via {@code getRequiredStack})
     * and would hit the identical {@code NullPointerException: Components not bound yet}. Wired to the same
     * {@link DefaultDataComponentsBoundEvent} instead, on the same game-bus, guarded the same
     * once-only-from-whichever-side-sees-it-first way. Also wires the two {@code FacadeAPI} statics so another
     * mod's {@code IMC} calls (or direct {@code FacadeAPI.registry} use) find a real facade item/registry rather
     * than {@code null} -- see {@code FacadeStateManager}'s own javadoc for the one part of that API (the IMC
     * consumption side) that stays unwired this round. 1.20.1 has no data components and keeps using common
     * setup -- see that platform's own copy of this method. */
    private static synchronized void onDefaultComponentsBound(DefaultDataComponentsBoundEvent event) {
        if (facadesScanned) {
            return;
        }
        facadesScanned = true;
        buildcraft.api.facades.FacadeAPI.facadeItem = ITEM_PLUGGABLE_FACADE.get();
        buildcraft.api.facades.FacadeAPI.registry = buildcraft.transport.plug.FacadeStateManager.INSTANCE;
        buildcraft.transport.plug.FacadeStateManager.init();
        buildcraft.lib.recipe.AssemblyRecipeRegistry.register(buildcraft.transport.recipe.FacadeAssemblyRecipes.INSTANCE);
    }

    private static void registerCapabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(
            Capabilities.Item.BLOCK, PIPE_HOLDER_TYPE.get(),
            (tile, side) -> tile.getCapability(Capabilities.Item.BLOCK, side)
        );

        // The fluid pipes' sections (PipeFlowFluids#getCapability). Null for every item pipe, so a pump or tank
        // next to an item pipe finds no fluid handler there -- and PipeFlowFluids#canConnect finds none on an
        // item pipe either, which with PipeFlow#canConnect(face, PipeFlow) is why the two kinds never connect.
        event.registerBlockEntity(
            Capabilities.Fluid.BLOCK, PIPE_HOLDER_TYPE.get(),
            (tile, side) -> tile.getCapability(Capabilities.Fluid.BLOCK, side)
        );

        // PipeApi.CAP_PIPE_HOLDER/CAP_PIPE/CAP_PLUG expose the tile/pipe/pluggable objects themselves -- this
        // is what IPipe#getConnectedPipe/IPipeHolder#getNeighbourPipe (via Level#getCapability) actually rely
        // on to detect a *neighbouring pipe* as a pipe (as opposed to a plain tile), which in turn is what makes
        // Pipe#canPipesConnect ever get a chance to run between two adjacent pipe segments at all. Direct
        // 26.x-shaped equivalent of 1.12.2's own TilePipeHolder constructor
        // (caps.addCapabilityInstance(CAP_PIPE_HOLDER, this, ...); caps.addCapability(CAP_PIPE, this::getPipe,
        // ...); caps.addCapability(CAP_PLUG, this::getPluggable, ...)).
        event.registerBlockEntity(PipeApi.CAP_PIPE_HOLDER, PIPE_HOLDER_TYPE.get(), (tile, side) -> tile);
        event.registerBlockEntity(PipeApi.CAP_PIPE, PIPE_HOLDER_TYPE.get(), (tile, side) -> tile.getPipe());
        event.registerBlockEntity(PipeApi.CAP_PLUG, PIPE_HOLDER_TYPE.get(), (tile, side) -> tile.getPluggable(side));

        // The wooden pipe's own MJ capabilities -- see PipeBehaviourWood's own javadoc for why this batch has to
        // register these here by hand rather than through MjCapabilityHelper.registerAll (which is keyed by
        // BlockEntityType and cannot distinguish "this particular pipe happens to be wood" on the one shared
        // TilePipeHolder type). Registered unconditionally on the shared tile type, the same as the item
        // capability above -- TilePipeHolder#getCapability already falls through to null for every other pipe
        // material's behaviour, exactly like it does for a capability token no behaviour/flow answers at all.
        event.registerBlockEntity(
            MjCapabilities.CONNECTOR, PIPE_HOLDER_TYPE.get(),
            (tile, side) -> tile.getCapability(MjCapabilities.CONNECTOR, side)
        );
        event.registerBlockEntity(
            MjCapabilities.RECEIVER, PIPE_HOLDER_TYPE.get(),
            (tile, side) -> tile.getCapability(MjCapabilities.RECEIVER, side)
        );
        event.registerBlockEntity(
            MjCapabilities.REDSTONE_RECEIVER, PIPE_HOLDER_TYPE.get(),
            (tile, side) -> tile.getCapability(MjCapabilities.REDSTONE_RECEIVER, side)
        );
    }
}
