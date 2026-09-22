/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * Copyright (c) 2026 Joshua Kac -- NeoForge port (BuildCraft 10)
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL
 * was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.lib.engine;

import java.util.List;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

import buildcraft.api.enums.EnumPowerStage;
import buildcraft.api.mj.IMjConnector;
import buildcraft.api.mj.IMjReceiver;
import buildcraft.api.mj.IMjRedstoneReceiver;
import buildcraft.api.mj.MjAPI;
import buildcraft.api.mj.MjCapabilities;
import buildcraft.api.tiles.IDebuggable;

import buildcraft.lib.misc.LocaleUtil;
import buildcraft.lib.misc.StringUtilBC;
import buildcraft.lib.misc.collect.OrderedEnumMap;
import buildcraft.lib.tile.TileBC;

/**
 * Renamed from 1.12.2's {@code TileEngineBase_BC8} (see PORTING.md's convention of dropping version-tag suffixes,
 * already applied to {@code ItemBC_Neptune} -> {@code ItemWrench} and friends). Still a genuine shared base class:
 * a future port of {@code buildcraft.energy}'s STONE/IRON/RF engines extends this exactly as 1.12.2's did.
 *
 * <p>The gameplay logic below -- MJ storage, the heat/power-stage state machine, chain-of-engines power routing,
 * the redstone-pulsed-vs-constant power split, and the piston-progress cycle -- is ported faithfully; only the
 * plumbing around it changed, following patterns already established elsewhere in this port:
 *
 * <ul>
 * <li><b>NBT.</b> {@code readFromNBT}/{@code writeToNBT} become {@link #loadAdditional}/{@link #saveAdditional}
 *     over {@link ValueInput}/{@link ValueOutput}, matching {@link TileBC}'s own javadoc. {@code currentDirection}
 *     has no built-in enum codec convenience worth reaching for here, so it is stored as its
 *     {@link Direction#getSerializedName()} string and read back with {@link Direction#byName(String)} -- the
 *     same "just use a string" approach as everything else in this file that isn't already a primitive.</li>
 * <li><b>Networking.</b> The whole id-tagged {@code writePayload}/{@code readPayload}/
 *     {@code sendNetworkUpdate(NET_RENDER_DATA)} system is gone, not replaced -- {@link TileBC#markDirtyAndSync()}
 *     already syncs this block entity's entire saved state to tracking clients, which covers every render-relevant
 *     field here ({@code currentDirection}, {@code powerStage}, {@code progress}, {@code isPumping}) the same way
 *     it already covered {@code TileMarkerVolume#showSignals}. {@code NET_GUI_DATA}/{@code NET_GUI_TICK} existed
 *     purely to sync {@code heat}/{@code currentOutput}/{@code power} to an *open GUI*; there is no GUI/container
 *     framework anywhere in this port yet (nothing ported so far has one), so that half of the payload system has
 *     no destination to port to and is dropped -- it comes back once a GUI framework exists.</li>
 * <li><b>Ticking.</b> {@code ITickable#update()} doesn't exist any more. {@link #serverTick()} is a plain method,
 *     wired through {@code EntityBlock#getTicker} on each concrete engine block exactly like
 *     {@code BlockPowerConsumerTester}/{@code TilePowerConsumerTester} already do. The original also had a real
 *     client-side half (the piston {@code progress} animation interpolating every client tick while
 *     {@code isPumping}), but nothing in this port can register a {@code BlockEntityRenderer} yet to consume it --
 *     no machine ported so far has one -- so that half ({@code getProgressClient}, the {@code lastProgress}
 *     bookkeeping, {@code clientModelData}/{@code ModelVariableData}) is dropped rather than kept inert. It costs
 *     nothing to re-add once a renderer exists to read {@code progress} from.</li>
 * <li><b>Capabilities.</b> {@code mjConnector} is the only capability an engine tile itself ever actually exposes:
 *     it is typed {@link IMjConnector}, and {@code EngineConnector} (the only implementation either concrete
 *     engine uses) implements nothing else, so the 1.12.2 {@code MjCapabilityHelper}'s {@code instanceof} probing
 *     of {@code IMjReceiver}/{@code IMjRedstoneReceiver}/{@code IMjReadable}/{@code IMjPassiveProvider} against it
 *     never matched anything -- an engine pushes power outward via a neighbour's {@code receivePower}, it does not
 *     itself receive, get read, or get pulled from. So only {@code MjCapabilities.CONNECTOR} needs registering, in
 *     {@code BCCoreRegistries#registerCapabilities}, guarded by {@code side == tile.getCurrentFacing()} -- see
 *     that guard's own reasoning below. RF auto-conversion (the {@code couldPowerRf()}/RF-fallback branch of
 *     1.12.2's {@code getReceiverToPower(TileEntity, Direction)}) is deliberately not ported: the already-built
 *     {@link buildcraft.api.mj.MjToRfAutoConvertor} on this platform wraps an {@link IMjConnector} to *look like*
 *     Forge energy to outside callers, which is the opposite direction from what this call site needs (wrapping
 *     an arbitrary neighbour's foreign energy capability to *look like* an {@link IMjReceiver}) -- writing that
 *     reverse adapter is new work outside this pass, not a rename of something already built, so the MJ-to-MJ
 *     chain below is the whole of {@link #getReceiverToPower(Direction)} here.</li>
 * <li><b>Rotation.</b> {@code attemptRotation()} specifically skips any face without a valid power receiver
 *     behind it ({@link #isFacingReceiver}), which is why it cannot reuse {@code IBlockWithFacing}/
 *     {@code RotationUtil.rotateAll} the way {@code BlockMarkerBase} does -- that cycles blindly through all six
 *     faces with no such check. 1.12.2 drove the cycle order from {@code VanillaRotationHandlers.ROTATE_FACING}
 *     (unported -- see PORTING.md's "deliberately not ported" list; it also covers ~25 unrelated vanilla-block
 *     rotation handlers this file has no use for), so the same six-direction order
 *     (east-south-down-west-north-up) is reproduced inline with {@link OrderedEnumMap}, which is a small,
 *     already-ported, pure-Java utility with no need to drag the rest of that class along.</li>
 * <li><b>Block shape.</b> There is no custom-rendering pipeline in this port to feed 1.12.2's
 *     {@code EnumBlockRenderType.ENTITYBLOCK_ANIMATED} model, so both concrete engine blocks use an ordinary
 *     static model and a full-cube {@code getShape}, matching every other machine ported so far. That also
 *     settles {@code getBlockFaceShape}/{@code isSideSolid} (1.12.2: only the face opposite
 *     {@code currentDirection} was solid): real research this session (decompiled
 *     {@code BlockBehaviour$BlockStateBase#isFaceSturdy}, confirmed via {@code javap}) found sturdiness is no
 *     longer a per-block override point at all on either target -- it is computed purely from the block's own
 *     {@code VoxelShape} geometry ({@code SupportType.FULL.isSupporting}, itself shape-derived), with nothing
 *     resembling 1.12.2's {@code getBlockFaceShape}/{@code isSideSolid} hooks left to override. A full cube is
 *     therefore sturdy on every side, not just the back -- a real, documented behaviour change from 1.12.2, not
 *     an oversight -- and reproducing the old "only the back face is solid" behaviour is not possible through the
 *     modern shape system without a custom per-tile {@code VoxelShape}, which is undesirable without a renderer to
 *     justify the geometry anyway (see the block classes' own javadoc for the fuller account).</li>
 * <li>{@link #getBiome()}/{@code getBiomeHeat()} are dropped outright: dead code even in 1.12.2 (its own
 *     {@code // TODO: Cache this!} comment on {@code getBiome} is the tell), and neither concrete engine tile
 *     ported here calls either.</li>
 * </ul>
 */
public abstract class TileEngineBase extends TileBC implements IDebuggable, IEngineLikeForLedger {

    /** Heat per {@link MjAPI#MJ}. Unused within this class; kept for a future engine type that wants it, matching
     * 1.12.2, which declared it the same way. */
    public static final double HEAT_PER_MJ = 0.0023;

    public static final double MIN_HEAT = 20;
    public static final double IDEAL_HEAT = 100;
    public static final double MAX_HEAT = 250;

    /** The six-direction wrench-rotation cycle order, matching 1.12.2's unported
     * {@code VanillaRotationHandlers.ROTATE_FACING} -- see this class's own javadoc. */
    private static final OrderedEnumMap<Direction> ROTATE_FACING = new OrderedEnumMap<>(
        Direction.class, Direction.EAST, Direction.SOUTH, Direction.DOWN, Direction.WEST, Direction.NORTH,
        Direction.UP
    );

    /** The one capability this tile itself exposes -- see the class javadoc's "Capabilities" entry. */
    public final IMjConnector mjConnector = createConnector();

    protected double heat = MIN_HEAT;
    protected long power = 0;
    private long lastPower = 0;
    /** Increments from 0 to 1 across a pump stroke. Above 0.5, all of the held power is emitted. */
    private float progress;
    private int progressPart = 0;

    protected EnumPowerStage powerStage = EnumPowerStage.BLUE;
    protected Direction currentDirection = Direction.UP;

    public long currentOutput;
    public boolean isRedstonePowered = false;
    protected boolean isPumping = false;

    protected TileEngineBase(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        currentDirection = Direction.byName(input.getStringOr("currentDirection", "up"));
        if (currentDirection == null) {
            currentDirection = Direction.UP;
        }
        isRedstonePowered = input.getBooleanOr("isRedstonePowered", false);
        heat = input.getDoubleOr("heat", MIN_HEAT);
        power = input.getLongOr("power", 0);
        progress = input.getFloatOr("progress", 0);
        progressPart = input.getIntOr("progressPart", 0);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.putString("currentDirection", currentDirection.getSerializedName());
        output.putBoolean("isRedstonePowered", isRedstonePowered);
        output.putDouble("heat", heat);
        output.putLong("power", power);
        output.putFloat("progress", progress);
        output.putInt("progressPart", progressPart);
    }

    /**
     * Wrench-rotates to the next face that has a valid power receiver behind it, skipping any that don't -- see
     * the class javadoc's "Rotation" entry for why this can't reuse {@code IBlockWithFacing}.
     */
    public InteractionResult attemptRotation() {
        Direction current = currentDirection;
        for (int i = 0; i < 6; i++) {
            current = ROTATE_FACING.next(current);
            if (isFacingReceiver(current)) {
                if (currentDirection != current) {
                    currentDirection = current;
                    markDirtyAndSync();
                    if (level != null) {
                        level.updateNeighborsAt(getBlockPos(), getBlockState().getBlock(), null);
                    }
                    return InteractionResult.SUCCESS;
                }
                return InteractionResult.FAIL;
            }
        }
        return InteractionResult.FAIL;
    }

    private boolean isFacingReceiver(Direction dir) {
        return getReceiverToPower(dir) != null;
    }

    protected final boolean canChain() {
        return getMaxChainLength() > 0;
    }

    /** @return The number of additional engines of the same class that this engine can send power through, looking
     *         for a real receiver past them. */
    protected int getMaxChainLength() {
        return 2;
    }

    public void rotateIfInvalid() {
        if (currentDirection != null && isFacingReceiver(currentDirection)) {
            return;
        }
        attemptRotation();
        if (currentDirection == null) {
            currentDirection = Direction.UP;
        }
    }

    /** Was {@code TileEntity#onPlacedBy} in 1.12.2; {@code BlockEntity} has no such hook any more, so each
     * concrete engine block calls this explicitly from its own {@code setPlacedBy}, matching the pattern
     * {@code TileMarkerVolume#onPlacedBy}/{@code BlockMarkerVolume#setPlacedBy} already established. */
    public void onPlacedBy(LivingEntity placer, ItemStack stack) {
        currentDirection = null; // Force rotateIfInvalid to always attempt to rotate
        rotateIfInvalid();
    }

    public double getPowerLevel() {
        return power / (double) getMaxPower();
    }

    protected EnumPowerStage computePowerStage() {
        double heatLevel = getHeatLevel();
        if (heatLevel < 0.25f) return EnumPowerStage.BLUE;
        else if (heatLevel < 0.5f) return EnumPowerStage.GREEN;
        else if (heatLevel < 0.75f) return EnumPowerStage.YELLOW;
        else if (heatLevel < 0.85f) return EnumPowerStage.RED;
        else return EnumPowerStage.OVERHEAT;
    }

    /** Recomputes the heat/power stage server-side on every call (cheap: a handful of comparisons) and syncs to
     * clients only when it actually changes -- the stage itself is never persisted, only {@link #heat} is, so it
     * is always rederived rather than trusted from NBT. */
    @Override
    public final EnumPowerStage getPowerStage() {
        if (level != null && !level.isClientSide()) {
            EnumPowerStage newStage = computePowerStage();
            if (powerStage != newStage) {
                powerStage = newStage;
                markDirtyAndSync();
            }
        }
        return powerStage;
    }

    public void updateHeatLevel() {
        heat = ((MAX_HEAT - MIN_HEAT) * getPowerLevel()) + MIN_HEAT;
    }

    public double getHeatLevel() {
        return (heat - MIN_HEAT) / (MAX_HEAT - MIN_HEAT);
    }

    public double getIdealHeatLevel() {
        return heat / IDEAL_HEAT;
    }

    @Override
    public double getHeat() {
        return heat;
    }

    public double getPistonSpeed() {
        return switch (getPowerStage()) {
            case BLUE -> 0.02;
            case GREEN -> 0.04;
            case YELLOW -> 0.08;
            case RED -> 0.12;
            default -> 0;
        };
    }

    protected abstract IMjConnector createConnector();

    /** Recomputes {@link #isRedstonePowered} from the block's neighbours. Called from each concrete engine
     * block's {@code neighborChanged} -- {@code BlockEntity} has no {@code onNeighbourBlockChanged} hook of its
     * own any more. {@code World#isBlockIndirectlyGettingPowered(pos) > 0} is {@code Level#getBestNeighborSignal
     * (pos) > 0} now: the direct modern successor, confirmed by the parallel rename of {@code isBlockPowered} to
     * {@code hasNeighborSignal} already used by {@code BlockMarkerVolume}. */
    public void onNeighbourBlockChanged() {
        if (level != null) {
            isRedstonePowered = level.getBestNeighborSignal(getBlockPos()) > 0;
        }
    }

    /**
     * Was {@code ITickable#update()}. Driven by each concrete engine block's {@code getTicker}, server-side only
     * (the block passes {@code null} client-side, matching {@code BlockPowerConsumerTester}'s pattern), which is
     * also why the 1.12.2 {@code world.isRemote} early-return branch (the client-side piston animation) has no
     * counterpart here at all -- see the class javadoc's "Ticking" entry.
     *
     * <p>{@code overheat} is captured <em>before</em> this tick's heat/power-stage recompute, exactly as 1.12.2
     * did: whether {@link #burn()} runs this tick depends on last tick's stage, not the one just computed a few
     * lines below, so an engine gets one more tick of fuel burn on the exact tick it crosses into overheat rather
     * than cutting off retroactively.
     */
    public void serverTick() {
        boolean overheat = getPowerStage() == EnumPowerStage.OVERHEAT;

        lastPower = 0;
        if (!isRedstonePowered) {
            if (power > MjAPI.MJ) {
                power -= MjAPI.MJ;
            } else if (power > 0) {
                power = 0;
            }
        }

        updateHeatLevel();
        getPowerStage();
        engineUpdate();

        IMjReceiver receiver = getReceiverToPower(currentDirection);
        // Redstone-pulsed receivers (e.g. another engine, or a machine wired for a burst) only get their power
        // once per full pump stroke, at the top of the swing (progress > 0.5); anything else gets power delivered
        // continuously below instead, at every tick the engine is active. The two are mutually exclusive per tick.
        boolean pulsedPower = receiver instanceof IMjRedstoneReceiver;

        if (progressPart != 0) {
            progress += getPistonSpeed();

            if (progress > 0.5 && progressPart == 1) {
                progressPart = 2;
                if (pulsedPower) {
                    sendPower(receiver);
                }
            } else if (progress >= 1) {
                progress = 0;
                progressPart = 0;
            }
        } else if (isRedstonePowered && isActive()) {
            if (getPowerToExtract(false) > 0) {
                progressPart = 1;
                setPumping(true);
            } else {
                setPumping(false);
            }
        } else {
            setPumping(false);
        }

        if (!pulsedPower) {
            if (isRedstonePowered && isActive()) {
                sendPower(receiver);
            } else {
                currentOutput = 0;
            }
        }

        if (!overheat) {
            burn();
        }

        setChanged();
    }

    private long getPowerToExtract(boolean doExtract) {
        IMjReceiver receiver = getReceiverToPower(currentDirection);
        if (receiver == null) {
            return 0;
        }
        return extractPower(0, receiver.getPowerRequested(), doExtract);
    }

    private void sendPower(IMjReceiver receiver) {
        if (receiver != null) {
            long extracted = getPowerToExtract(false);
            if (extracted > 0) {
                long excess = receiver.receivePower(extracted, false);
                extractPower(extracted - excess, extracted - excess, true);
            }
        }
    }

    /** No-op by default -- a future fuel-burning engine type (the unported {@code buildcraft.energy} combustion
     * engine) overrides this to consume fuel. Neither engine ported in this pass needs it. */
    protected void burn() {}

    protected void engineUpdate() {
        if (!isRedstonePowered) {
            if (power >= 1) {
                power -= 1;
            } else if (power < 1) {
                power = 0;
            }
        }
    }

    public boolean isActive() {
        return true;
    }

    protected final void setPumping(boolean isActive) {
        if (this.isPumping == isActive) {
            return;
        }
        this.isPumping = isActive;
        markDirtyAndSync();
    }

    /* STATE INFORMATION */
    public abstract boolean isBurning();

    public void addPower(long microJoules) {
        power += microJoules;
        lastPower += microJoules;

        if (power > getMaxPower()) {
            power = getMaxPower();
        }
    }

    public long extractPower(long min, long max, boolean doExtract) {
        if (power < min) {
            return 0;
        }

        long actualMax = Math.min(max, maxPowerExtracted());
        if (actualMax < min) {
            return 0;
        }

        long extracted;
        if (power >= actualMax) {
            extracted = actualMax;
            if (doExtract) {
                power -= actualMax;
            }
        } else {
            extracted = power;
            if (doExtract) {
                power = 0;
            }
        }
        return extracted;
    }

    public final boolean isPoweredTile(BlockEntity tile, Direction side) {
        if (tile == null) return false;
        if (tile.getClass() == getClass()) {
            TileEngineBase other = (TileEngineBase) tile;
            return other.currentDirection == currentDirection;
        }
        return getReceiverToPower(tile, side) != null;
    }

    /** Looks up the power receiver directly behind {@code tile}, from the given side -- the tail end of the
     * chain walked by {@link #getReceiverToPower(Direction)}. See the class javadoc's "Capabilities" entry for
     * why there is no RF fallback here, unlike 1.12.2. */
    @Nullable
    private IMjReceiver getReceiverToPower(BlockEntity tile, Direction side) {
        if (tile == null || level == null) {
            return null;
        }
        IMjReceiver rec = level.getCapability(MjCapabilities.RECEIVER, tile.getBlockPos(), side.getOpposite());
        if (rec != null && rec.canConnect(mjConnector) && mjConnector.canConnect(rec)) {
            return rec;
        }
        return null;
    }

    /**
     * Walks up to {@link #getMaxChainLength()} adjacent engines of this exact class in {@code side}, looking for
     * a real receiver past the end of the chain. This is what lets several engines of the same type be lined up
     * behind one another, all feeding a single machine through the last one in the row -- each engine in the
     * middle is transparent to the ones behind it, as long as every one of them also faces {@code side}.
     *
     * <p>The 1.12.2 {@code getTileBuffer(EnumFacing)} indirection ({@code ITileBuffer}, its own comment already
     * marking it {@code "TEMP! This should be replaced with a tile buffer!"}) is dropped in favour of a direct
     * {@code level.getBlockEntity} lookup -- {@code buildcraft.lib.cache.NeighbourTileCache} is this port's real
     * replacement for that "TEMP" tile buffer, but nothing wires it in during this pass either; it is a drop-in
     * upgrade for whoever needs the chunk-loading-avoidance it provides later, not a blocker for this feature.
     */
    public IMjReceiver getReceiverToPower(Direction side) {
        if (level == null) {
            return null;
        }
        TileEngineBase engine = this;
        BlockEntity next = null;

        for (int len = 0; len <= getMaxChainLength(); len++) {
            next = level.getBlockEntity(engine.getBlockPos().relative(side));

            if (next == null) {
                return null;
            }

            if (next.getClass() == getClass()) {
                if (side != ((TileEngineBase) next).currentDirection) {
                    return null;
                }
            }

            if (next instanceof TileEngineBase nextEngine) {
                if (next.getClass() != getClass()) {
                    return null;
                }
                engine = nextEngine;
            } else {
                break;
            }
        }

        if (next == null || next instanceof TileEngineBase) {
            return null;
        }

        return getReceiverToPower(next, side);
    }

    public abstract long getMaxPower();

    public long minPowerReceived() {
        return 2 * MjAPI.MJ;
    }

    public abstract long maxPowerReceived();

    public abstract long maxPowerExtracted();

    public abstract float explosionRange();

    public long getEnergyStored() {
        return power;
    }

    public abstract long getCurrentOutput();

    @Override
    public boolean isEngineOn() {
        return isPumping;
    }

    // IEngineLikeForLedger

    @Override
    public final long getCurrentMjOutput() {
        return getCurrentOutput();
    }

    @Override
    public final long getMjStored() {
        return getEnergyStored();
    }

    public Direction getCurrentFacing() {
        return currentDirection;
    }

    @Override
    public void getDebugInfo(List<String> left, List<String> right, @Nullable Direction side) {
        left.add("facing = " + currentDirection);
        left.add("heat = " + LocaleUtil.localizeHeat(heat) + " -- " + StringUtilBC.formatSafe("%.2f %%", getHeatLevel()));
        left.add("power = " + LocaleUtil.localizeMj(power));
        left.add("stage = " + powerStage);
        left.add("progress = " + progress);
        left.add("last = " + LocaleUtil.localizeMjFlow(lastPower));
    }
}
