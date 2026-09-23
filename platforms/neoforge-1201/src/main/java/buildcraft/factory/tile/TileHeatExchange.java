/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * Copyright (c) 2026 Joshua Kac -- NeoForge port (BuildCraft 10)
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL
 * was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.factory.tile;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;

import buildcraft.api.properties.BuildCraftProperties;
import buildcraft.api.recipes.BuildcraftRecipeRegistry;
import buildcraft.api.recipes.IRefineryRecipeManager;
import buildcraft.api.recipes.IRefineryRecipeManager.ICoolableRecipe;
import buildcraft.api.recipes.IRefineryRecipeManager.IHeatableRecipe;
import buildcraft.api.tiles.IDebuggable;

import buildcraft.lib.fluid.Tank;
import buildcraft.lib.misc.FluidUtilBC;
import buildcraft.lib.misc.SoundUtil;
import buildcraft.lib.tile.TileBC;

import buildcraft.factory.block.BlockHeatExchange;
import buildcraft.factory.block.BlockHeatExchange.EnumExchangePart;

import buildcraft.BCFactoryRegistries;

/**
 * The port of 1.12.2's {@code TileHeatExchange} -- see the 26.x copy of this class for the structure rules, the
 * fluid path and the deliberate differences from 1.12.2, all identical here. The platform differences: tanks are
 * classic {@code FluidTank}s, NBT is a plain {@link CompoundTag}, and the per-side fluid capability is handed out
 * from {@link #getCapability} as {@link LazyOptional}s that are invalidated whenever the section changes (26.x uses
 * {@code level.invalidateCapabilities} for the same purpose).
 */
public class TileHeatExchange extends TileBC implements IDebuggable {
    /** The maximum amount of fluid that can be transferred per tick for each number of middle sections. */
    private static final int[] FLUID_MULT = { 5, 10, 20 };
    private static final int PROGRESS_LAG = 120;

    @Nullable
    protected ExchangeSection section;
    private boolean checkNeighbours = true;
    private boolean tanksChanged = false;

    public TileHeatExchange(BlockPos pos, BlockState state) {
        super(BCFactoryRegistries.HEAT_EXCHANGE_TYPE.get(), pos, state);
    }

    // Ticking

    public void serverTick() {
        if (checkNeighbours) {
            checkNeighbours = false;
            checkStructure();
        }
        if (section != null) {
            section.tick();
        }
        if (tanksChanged) {
            tanksChanged = false;
            markDirtyAndSync();
        }
    }

    public void clientTick() {
        if (section instanceof ExchangeSectionStart start) {
            start.updateProgress();
            start.spawnParticles();
        }
    }

    private void checkStructure() {
        Deque<TileHeatExchange> exchangers = findAdjacentExchangers();
        if (exchangers.isEmpty()) {
            // Something went wrong when searching (as normally this deque will contain this)
            checkNeighbours = true;
        } else if (exchangers.size() < 3) {
            for (TileHeatExchange tile : exchangers) {
                tile.removeSection();
            }
        } else if (exchangers.size() > 5) {
            // 1.12.2: "TODO: Remove all exchangers sections" -- kept as a no-op.
        } else {
            ExchangeSectionStart sectionStart = null;
            ExchangeSectionEnd sectionEnd = null;
            for (TileHeatExchange exchange : exchangers) {
                // For efficiency, only run this check once.
                exchange.checkNeighbours = false;
                if (exchange.section instanceof ExchangeSectionStart s) {
                    if (sectionStart == null) {
                        sectionStart = s;
                    }
                } else if (exchange.section instanceof ExchangeSectionEnd e) {
                    if (sectionEnd == null) {
                        sectionEnd = e;
                    }
                }
                exchange.section = null;
            }
            if (sectionStart == null) {
                sectionStart = new ExchangeSectionStart(exchangers.getFirst());
            }
            if (sectionEnd == null) {
                sectionEnd = new ExchangeSectionEnd(exchangers.getLast());
            }
            sectionStart.endSection = sectionEnd;
            sectionStart.middleCount = exchangers.size() - 2;
            exchangers.getFirst().section = sectionStart;
            sectionStart.setTile(exchangers.getFirst());
            exchangers.getLast().section = sectionEnd;
            sectionEnd.setTile(exchangers.getLast());
            for (TileHeatExchange exchange : exchangers) {
                exchange.onSectionChanged();
            }
        }
    }

    private void removeSection() {
        if (section == null) {
            return;
        }
        section = null;
        onSectionChanged();
    }

    private void onSectionChanged() {
        updatePartState();
        invalidateSectionCaps();
        markDirtyAndSync();
    }

    /** Writes {@link BlockHeatExchange#PROP_PART} from the current section -- 1.12.2's {@code getActualState}.
     * {@code UPDATE_KNOWN_SHAPE} stops the change from re-running neighbours' {@code updateShape}, since the part
     * never affects a neighbour's connection flags. */
    private void updatePartState() {
        if (level == null || level.isClientSide()) {
            return;
        }
        BlockState state = getBlockState();
        if (!(state.getBlock() instanceof BlockHeatExchange)) {
            return;
        }
        EnumExchangePart part = section instanceof ExchangeSectionStart ? EnumExchangePart.START
            : section instanceof ExchangeSectionEnd ? EnumExchangePart.END : EnumExchangePart.MIDDLE;
        if (state.getValue(BlockHeatExchange.PROP_PART) != part) {
            level.setBlock(worldPosition, state.setValue(BlockHeatExchange.PROP_PART, part),
                Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
        }
    }

    public void onNeighbourChanged() {
        checkNeighbours = true;
    }

    private void onTankChanged() {
        tanksChanged = true;
        setChanged();
    }

    @Nullable
    private TileHeatExchange getLocalTile(BlockPos pos) {
        if (level == null || !level.isLoaded(pos)) {
            return null;
        }
        BlockEntity be = level.getBlockEntity(pos);
        return be instanceof TileHeatExchange ex && !ex.isRemoved() ? ex : null;
    }

    private Deque<TileHeatExchange> findAdjacentExchangers() {
        Direction thisFacing = getFacing();
        if (thisFacing == null) {
            return new ArrayDeque<>();
        }
        Direction dirToStart = thisFacing.getClockWise();
        Direction dirToEnd = thisFacing.getCounterClockWise();
        Deque<TileHeatExchange> exchangers = new ArrayDeque<>();
        exchangers.add(this);
        for (int i = 1; i < 6; i++) {
            TileHeatExchange other = getLocalTile(worldPosition.relative(dirToStart, i));
            if (other == null || other.getFacing() != thisFacing) {
                break;
            }
            exchangers.addFirst(other);
        }
        for (int i = 1; i < 6; i++) {
            TileHeatExchange other = getLocalTile(worldPosition.relative(dirToEnd, i));
            if (other == null || other.getFacing() != thisFacing) {
                break;
            }
            exchangers.addLast(other);
        }
        return exchangers;
    }

    /** Called by the wrench (via {@code BlockHeatExchange#attemptRotation}). A lone exchanger turns 90 degrees;
     * one that is part of a line turns the whole line 180 degrees, which swaps the start and end blocks -- the start
     * section (with its fluid) moves to the old last block and the end section to the old first. 1.12.2's
     * {@code rotate()}, unchanged. */
    public boolean rotate() {
        Direction thisFacing = getFacing();
        if (thisFacing == null || level == null) {
            return false;
        }
        Deque<TileHeatExchange> exchangers = findAdjacentExchangers();
        if (exchangers.size() == 1) {
            level.setBlockAndUpdate(worldPosition,
                getBlockState().setValue(BuildCraftProperties.BLOCK_FACING, thisFacing.getClockWise()));
        } else {
            ExchangeSectionStart start = null;
            ExchangeSectionEnd end = null;
            for (TileHeatExchange exchange : exchangers) {
                if (exchange.section instanceof ExchangeSectionStart s) {
                    start = s;
                } else if (exchange.section instanceof ExchangeSectionEnd e) {
                    end = e;
                }
                exchange.section = null;
                level.setBlockAndUpdate(exchange.worldPosition,
                    exchange.getBlockState().setValue(BuildCraftProperties.BLOCK_FACING, thisFacing.getOpposite()));
                exchange.checkNeighbours = true;
                exchange.setChanged();
            }
            if (start != null) {
                TileHeatExchange tile = exchangers.getLast();
                tile.section = start;
                start.setTile(tile);
                tile.onSectionChanged();
            }
            if (end != null) {
                TileHeatExchange tile = exchangers.getFirst();
                tile.section = end;
                end.setTile(tile);
                tile.onSectionChanged();
            }
        }
        SoundUtil.playSlideSound(level, worldPosition);
        return true;
    }

    public boolean isStart() {
        return section instanceof ExchangeSectionStart;
    }

    public boolean isEnd() {
        return section instanceof ExchangeSectionEnd;
    }

    @Nullable
    public ExchangeSection getSection() {
        return section;
    }

    @Nullable
    public Direction getFacing() {
        BlockState state = getBlockState();
        if (!(state.getBlock() instanceof BlockHeatExchange)) {
            return null;
        }
        return state.getValue(BuildCraftProperties.BLOCK_FACING);
    }

    /** The fluid capability for {@code side}, 1.12.2's {@code CapabilityHelper} wiring: nothing without a section
     * (a middle block, or an incomplete line) and nothing for a side-less query. */
    @Nullable
    public IFluidHandler getFluidHandler(@Nullable Direction side) {
        if (section == null || side == null) {
            return null;
        }
        return section.getTankForSide(side);
    }

    private LazyOptional<IFluidHandler> capInput = LazyOptional.empty();
    private LazyOptional<IFluidHandler> capOutput = LazyOptional.empty();

    private void invalidateSectionCaps() {
        capInput.invalidate();
        capOutput.invalidate();
        ExchangeSection sec = section;
        capInput = sec == null ? LazyOptional.empty() : LazyOptional.of(() -> sec.tankInput);
        capOutput = sec == null ? LazyOptional.empty() : LazyOptional.of(() -> sec.tankOutput);
    }

    @Override
    public <T> @NotNull LazyOptional<T> getCapability(@NotNull Capability<T> cap, @Nullable Direction side) {
        if (cap == ForgeCapabilities.FLUID_HANDLER) {
            IFluidHandler handler = getFluidHandler(side);
            ExchangeSection sec = section;
            if (handler == null || sec == null) {
                return LazyOptional.empty();
            }
            return (handler == sec.tankInput ? capInput : capOutput).cast();
        }
        return super.getCapability(cap, side);
    }

    @Override
    public void invalidateCaps() {
        super.invalidateCaps();
        capInput.invalidate();
        capOutput.invalidate();
    }

    /** 1.12.2's {@code getRenderBoundingBox}: the start block renders the whole line. */
    @Override
    public AABB getRenderBoundingBox() {
        if (isStart()) {
            return new AABB(worldPosition).inflate(6);
        }
        return super.getRenderBoundingBox();
    }

    /** Client-side lookup of the end section for rendering, by position: the end is {@code middleCount + 1}
     * blocks from the start towards {@code facing.getCounterClockWise()}. */
    @Nullable
    public ExchangeSectionEnd findEndSectionClient() {
        if (!(section instanceof ExchangeSectionStart start) || level == null) {
            return null;
        }
        Direction facing = getFacing();
        if (facing == null) {
            return null;
        }
        TileHeatExchange end = getLocalTile(worldPosition.relative(facing.getCounterClockWise(), start.middleCount + 1));
        return end != null && end.section instanceof ExchangeSectionEnd e ? e : null;
    }

    // TileBC

    @Override
    public void load(CompoundTag input) {
        super.load(input);
        section = null;
        if (input.contains("section")) {
            CompoundTag nbt = input.getCompound("section");
            if (nbt.getBoolean("start")) {
                section = new ExchangeSectionStart(this, nbt);
            } else {
                section = new ExchangeSectionEnd(this, nbt);
            }
        }
        invalidateSectionCaps();
        checkNeighbours = true;
    }

    @Override
    protected void saveAdditional(CompoundTag output) {
        super.saveAdditional(output);
        if (section != null) {
            CompoundTag nbt = new CompoundTag();
            section.write(nbt);
            output.put("section", nbt);
        }
    }

    @Override
    public void getDebugInfo(List<String> left, List<String> right, @Nullable Direction side) {
        if (section == null) {
            left.add("section = null");
        } else {
            left.add("section = " + (section instanceof ExchangeSectionStart ? "start" : "end"));
            section.getDebugInfo(left);
        }
    }

    // Sections

    public static abstract class ExchangeSection {
        public final Tank tankInput, tankOutput;
        private TileHeatExchange tile;

        ExchangeSection(TileHeatExchange tile) {
            tankInput = new Tank(2 * 1000, this::onTankChanged);
            tankOutput = new Tank(2 * 1000, this::onTankChanged);
            tankOutput.setCanFill(false);
            this.tile = tile;
        }

        ExchangeSection(TileHeatExchange tile, CompoundTag nbt) {
            this(tile);
            tankInput.readFromNBT(nbt.getCompound("input"));
            tankOutput.readFromNBT(nbt.getCompound("output"));
        }

        private void onTankChanged() {
            if (tile != null) {
                tile.onTankChanged();
            }
        }

        void write(CompoundTag nbt) {
            nbt.put("input", tankInput.writeToNBT(new CompoundTag()));
            nbt.put("output", tankOutput.writeToNBT(new CompoundTag()));
        }

        void tick() {}

        @Nullable
        abstract IFluidHandler getTankForSide(Direction side);

        void getDebugInfo(List<String> left) {
            left.add("tank_input = " + tankInput.getDebugString());
            left.add("tank_output = " + tankOutput.getDebugString());
        }

        public TileHeatExchange getTile() {
            return tile;
        }

        void setTile(TileHeatExchange tile) {
            this.tile = tile;
        }
    }

    public static class ExchangeSectionStart extends ExchangeSection {

        @Nullable
        private ExchangeSectionEnd endSection;
        public int middleCount;
        private int progress = 0;
        private int progressLast = 0;
        private EnumProgressState progressState = EnumProgressState.OFF;
        private EnumProgressState lastSentState = EnumProgressState.OFF;
        // Saved but never used, exactly as in 1.12.2.
        private int inputCoolantAmountCharge = 0;
        private int inputHeatantAmountCharge = 0;

        {
            tankInput.setFilter(ExchangeSectionStart::isHeatant);
        }

        ExchangeSectionStart(TileHeatExchange tile) {
            super(tile);
        }

        ExchangeSectionStart(TileHeatExchange tile, CompoundTag nbt) {
            super(tile, nbt);
            inputCoolantAmountCharge = nbt.getInt("coolantCharge");
            inputHeatantAmountCharge = nbt.getInt("heatantCharge");
            middleCount = nbt.contains("middleCount") ? nbt.getInt("middleCount") : 1;
            progress = nbt.getInt("progress");
            progressLast = progress;
            int ordinal = nbt.getInt("progressState");
            EnumProgressState[] states = EnumProgressState.values();
            progressState = ordinal >= 0 && ordinal < states.length ? states[ordinal] : EnumProgressState.OFF;
            lastSentState = progressState;
        }

        @Override
        void write(CompoundTag nbt) {
            super.write(nbt);
            nbt.putBoolean("start", true);
            nbt.putInt("coolantCharge", inputCoolantAmountCharge);
            nbt.putInt("heatantCharge", inputHeatantAmountCharge);
            nbt.putInt("middleCount", middleCount);
            nbt.putInt("progress", progress);
            nbt.putInt("progressState", progressState.ordinal());
        }

        @Nullable
        public ExchangeSectionEnd getEndSection() {
            return endSection;
        }

        public EnumProgressState getProgressState() {
            return progressState;
        }

        public double getProgress(float partialTicks) {
            return (progressLast + (progress - progressLast) * (double) partialTicks) / PROGRESS_LAG;
        }

        private static boolean isHeatant(FluidStack fluid) {
            IRefineryRecipeManager reg = BuildcraftRecipeRegistry.refineryRecipes;
            return reg != null && reg.getHeatableRegistry().getRecipeForInput(fluid) != null;
        }

        @Override
        @Nullable
        IFluidHandler getTankForSide(Direction side) {
            if (side == Direction.DOWN) {
                return tankInput;
            }
            Direction thisFacing = getTile().getFacing();
            if (thisFacing == null || side != thisFacing.getClockWise()) {
                return null;
            }
            return tankOutput;
        }

        @Override
        void tick() {
            updateProgress();
            ExchangeSectionEnd end = endSection;
            if (end != null && end.getTile().isRemoved()) {
                endSection = end = null;
            }
            if (end != null) {
                craft(end);
            } else if (progressState != EnumProgressState.OFF) {
                progressState = EnumProgressState.STOPPING;
            }
            output();
            if (progressState != lastSentState) {
                lastSentState = progressState;
                getTile().markDirtyAndSync();
            }
        }

        void updateProgress() {
            progressLast = progress;
            switch (progressState) {
                case STOPPING -> {
                    progress--;
                    if (progress <= 0) {
                        progress = 0;
                        progressState = EnumProgressState.OFF;
                    }
                }
                case PREPARING, RUNNING -> {
                    progress++;
                    if (progress >= PROGRESS_LAG) {
                        progress = PROGRESS_LAG;
                        progressState = EnumProgressState.RUNNING;
                    }
                }
                default -> {}
            }
        }

        private void craft(ExchangeSectionEnd end) {
            Tank c_in = end.tankInput;
            Tank c_out = tankOutput;
            Tank h_in = tankInput;
            Tank h_out = end.tankOutput;
            IRefineryRecipeManager reg = BuildcraftRecipeRegistry.refineryRecipes;
            ICoolableRecipe c_recipe = reg == null ? null : reg.getCoolableRegistry().getRecipeForInput(TileDistiller.getFluid(c_in));
            IHeatableRecipe h_recipe = reg == null ? null : reg.getHeatableRegistry().getRecipeForInput(TileDistiller.getFluid(h_in));
            if (h_recipe == null || c_recipe == null) {
                progressState = EnumProgressState.STOPPING;
                return;
            }
            if (c_recipe.heatFrom() <= h_recipe.heatFrom()) {
                progressState = EnumProgressState.STOPPING;
                return;
            }
            int c_diff = c_recipe.heatFrom() - c_recipe.heatTo();
            int h_diff = h_recipe.heatTo() - h_recipe.heatFrom();
            if (h_diff < 1 || c_diff < 1) {
                throw new IllegalStateException("Invalid recipe " + c_recipe + ", " + h_recipe);
            }

            // Find the minimum common amount that we can process from each tank up to `max_amount`
            // min_common_multiplier == 0 indicates that we can no longer process (tanks full/empty)
            int max_amount = FLUID_MULT[Math.max(0, Math.min(FLUID_MULT.length, middleCount) - 1)];
            FluidStack c_in_f = setAmount(c_recipe.in(), max_amount);
            FluidStack c_out_f = setAmount(c_recipe.out(), max_amount);
            FluidStack h_in_f = setAmount(h_recipe.in(), max_amount);
            FluidStack h_out_f = setAmount(h_recipe.out(), max_amount);

            // fluid == null => the fluid is consumed in the process (e.g. water, lava)
            int c_out_amount = c_out_f == null ? max_amount : fillableAmount(c_out, c_out_f);
            int h_out_amount = h_out_f == null ? max_amount : fillableAmount(h_out, h_out_f);

            int c_in_amount = drainableAmount(c_in, c_in_f);
            int h_in_amount = drainableAmount(h_in, h_in_f);

            final int min_common_multiplier =
                Math.min(Math.min(Math.min(c_out_amount, h_out_amount), c_in_amount), h_in_amount);

            if (min_common_multiplier > 0) {
                c_in_f = setAmount(c_recipe.in(), min_common_multiplier);
                c_out_f = setAmount(c_recipe.out(), min_common_multiplier);
                h_in_f = setAmount(h_recipe.in(), min_common_multiplier);
                h_out_f = setAmount(h_recipe.out(), min_common_multiplier);

                if (progressState == EnumProgressState.OFF) {
                    progressState = EnumProgressState.PREPARING;
                } else if (progressState == EnumProgressState.RUNNING) {
                    fill(c_out, c_out_f);
                    drain(c_in, c_in_f);

                    fill(h_out, h_out_f);
                    drain(h_in, h_in_f);
                }
            } else {
                progressState = EnumProgressState.STOPPING;
            }
        }

        void spawnParticles() {
            if (progressState != EnumProgressState.RUNNING) {
                return;
            }
            TileHeatExchange tile = getTile();
            ExchangeSectionEnd end = tile.findEndSectionClient();
            Level level = tile.getLevel();
            Direction facing = tile.getFacing();
            if (end == null || level == null || facing == null) {
                return;
            }
            if (end.tankInput.getFluid().getFluid().isSame(Fluids.LAVA)) {
                spewForth(level, Vec3.atCenterOf(tile.getBlockPos()), facing.getClockWise(), ParticleTypes.LARGE_SMOKE);
            }
            if (tankInput.getFluid().getFluid().isSame(Fluids.WATER)) {
                spewForth(level, Vec3.atCenterOf(end.getTile().getBlockPos()), Direction.UP, ParticleTypes.CLOUD);
            }
        }

        /** 1.12.2's {@code spewForth}, with its particle-setting lookup ({@code Minecraft.gameSettings}, client-only)
         * fixed at the "all particles" count of 5 -- this class is common code. */
        private static void spewForth(Level level, Vec3 from, Direction dir, SimpleParticleType particle) {
            Vec3 vecDir = new Vec3(dir.getStepX(), dir.getStepY(), dir.getStepZ());
            from = from.add(vecDir);
            double x = from.x;
            double y = from.y;
            double z = from.z;
            Vec3 motion = vecDir.scale(0.4);
            int particleCount = 5;
            for (int i = 0; i < particleCount; i++) {
                double dx = motion.x + (Math.random() - 0.5) * 0.1;
                double dy = motion.y + (Math.random() - 0.5) * 0.1;
                double dz = motion.z + (Math.random() - 0.5) * 0.1;
                double interp = i / (double) particleCount;
                x -= dx * interp;
                y -= dy * interp;
                z -= dz * interp;
                level.addParticle(particle, x, y, z, dx, dy, dz);
            }
        }

        private void output() {
            IFluidHandler thisOut = getFluidAutoOutputTarget();
            if (thisOut != null) {
                FluidUtilBC.move(tankOutput, thisOut, 1000);
            }
            ExchangeSectionEnd end = endSection;
            if (end != null) {
                IFluidHandler endOut = end.getFluidAutoOutputTarget();
                if (endOut != null) {
                    FluidUtilBC.move(end.tankOutput, endOut, 1000);
                }
            }
        }

        @Nullable
        private IFluidHandler getFluidAutoOutputTarget() {
            TileHeatExchange tile = getTile();
            Direction facing = tile.getFacing();
            Level level = tile.getLevel();
            if (facing == null || level == null) {
                return null;
            }
            Direction dir = facing.getClockWise();
            return neighbourFluidHandler(level, tile.getBlockPos().relative(dir), dir.getOpposite());
        }

        @Nullable
        private static FluidStack setAmount(@Nullable FluidStack fluid, int mult) {
            if (fluid == null || fluid.isEmpty()) {
                return null;
            }
            return new FluidStack(fluid, mult);
        }

        /** 1.12.2's {@code t.drainInternal(fluid, false).amount}. */
        private static int drainableAmount(Tank t, FluidStack fluid) {
            FluidStack res = t.getFluid();
            if (res.isEmpty() || !res.isFluidEqual(fluid)) {
                return 0;
            }
            return Math.min(fluid.getAmount(), res.getAmount());
        }

        /** 1.12.2's {@code t.fillInternal(fluid, false)}: capacity and fluid type only, bypassing {@code canFill}. */
        private static int fillableAmount(Tank t, FluidStack fluid) {
            FluidStack res = t.getFluid();
            if (!res.isEmpty() && !res.isFluidEqual(fluid)) {
                return 0;
            }
            return Math.min(fluid.getAmount(), t.getCapacity() - t.getFluidAmount());
        }

        private static void fill(Tank t, @Nullable FluidStack fluid) {
            if (fluid == null) {
                return;
            }
            if (fillableAmount(t, fluid) != fluid.getAmount()) {
                throw new IllegalStateException("Buggy transition! Failed to fill " + fluid.getFluid() + " x "
                    + fluid.getAmount() + " into " + t);
            }
            t.fillInternal(fluid);
        }

        private static void drain(Tank t, FluidStack fluid) {
            if (drainableAmount(t, fluid) != fluid.getAmount()) {
                throw new IllegalStateException("Buggy transition! Failed to drain " + fluid.getFluid() + " x "
                    + fluid.getAmount() + " from " + t);
            }
            t.drain(fluid.getAmount(), IFluidHandler.FluidAction.EXECUTE);
        }

        @Override
        void getDebugInfo(List<String> left) {
            super.getDebugInfo(left);
            left.add("progress = " + progress);
            left.add("state = " + progressState);
            left.add("has_end = " + (endSection != null));
            left.add("middle_count = " + middleCount);
        }
    }

    public static class ExchangeSectionEnd extends ExchangeSection {

        {
            tankInput.setFilter(ExchangeSectionEnd::isCoolant);
        }

        ExchangeSectionEnd(TileHeatExchange tile) {
            super(tile);
        }

        ExchangeSectionEnd(TileHeatExchange tile, CompoundTag nbt) {
            super(tile, nbt);
        }

        private static boolean isCoolant(FluidStack fluid) {
            IRefineryRecipeManager reg = BuildcraftRecipeRegistry.refineryRecipes;
            return reg != null && reg.getCoolableRegistry().getRecipeForInput(fluid) != null;
        }

        @Override
        @Nullable
        IFluidHandler getTankForSide(Direction side) {
            if (side == Direction.UP) {
                return tankOutput;
            }
            Direction thisFacing = getTile().getFacing();
            if (thisFacing == null || side != thisFacing.getCounterClockWise()) {
                return null;
            }
            return tankInput;
        }

        @Override
        void write(CompoundTag nbt) {
            super.write(nbt);
            nbt.putBoolean("start", false);
        }

        @Nullable
        IFluidHandler getFluidAutoOutputTarget() {
            Level level = getTile().getLevel();
            if (level == null) {
                return null;
            }
            return neighbourFluidHandler(level, getTile().getBlockPos().above(), Direction.DOWN);
        }
    }

    @Nullable
    static IFluidHandler neighbourFluidHandler(Level level, BlockPos pos, Direction side) {
        if (!level.isLoaded(pos)) {
            return null;
        }
        BlockEntity be = level.getBlockEntity(pos);
        return be == null ? null : be.getCapability(ForgeCapabilities.FLUID_HANDLER, side).resolve().orElse(null);
    }

    public enum EnumProgressState {
        /** Progress is at 0, not moving. */
        OFF,
        /** Progress is increasing from 0 to max */
        PREPARING,
        /** progress stays at max */
        RUNNING,
        /** Progress is decreasing from max to 0. */
        STOPPING;
    }
}
