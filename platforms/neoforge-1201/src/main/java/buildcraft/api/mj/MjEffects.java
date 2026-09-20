/** Copyright (c) 2011-2017, SpaceToad and the BuildCraft Team http://www.mod-buildcraft.com
 *
 * The BuildCraft API is distributed under the terms of the MIT License. Please check the contents of the license,
 * which should be located as "LICENSE.API" in the BuildCraft source code distribution. */
package buildcraft.api.mj;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * Holds the active {@link IMjEffectManager} and drives the per-tick power shedding.
 *
 * <p>This replaces {@code MjAPI.EFFECT_MANAGER} and {@code MjBattery.tick(World, ...)} from 1.12.2. The battery's
 * arithmetic is version-independent and lives in {@link MjBattery#shedExcessPower()}; only the visible effect needs a
 * {@link Level}, so it is driven from here.
 */
public final class MjEffects {

    /** Replaced during mod setup by the real implementation; defaults to a no-op so the API works headless. */
    public static IMjEffectManager manager = NullaryEffectManager.INSTANCE;

    private MjEffects() {}

    /**
     * Sheds any power held over twice the battery's capacity and shows the loss. Call once per tick from a machine's
     * server tick, exactly where 1.12.2 called {@code battery.tick(world, pos)}.
     */
    public static void tick(Level level, BlockPos pos, MjBattery battery) {
        long lost = battery.shedExcessPower();
        if (lost > 0) {
            manager.createPowerLossEffect(level, pos, lost);
        }
    }

    /** As {@link #tick(Level, BlockPos, MjBattery)}, for machines that track a sub-block position. */
    public static void tick(Level level, Vec3 pos, MjBattery battery) {
        long lost = battery.shedExcessPower();
        if (lost > 0) {
            manager.createPowerLossEffect(level, pos, lost);
        }
    }

    public enum NullaryEffectManager implements IMjEffectManager {
        INSTANCE;

        @Override
        public void createPowerLossEffect(Level level, Vec3 center, long microJoulesLost) {}

        @Override
        public void createPowerLossEffect(Level level, Vec3 center, Direction direction, long microJoulesLost) {}

        @Override
        public void createPowerLossEffect(Level level, Vec3 center, Vec3 direction, long microJoulesLost) {}
    }
}
